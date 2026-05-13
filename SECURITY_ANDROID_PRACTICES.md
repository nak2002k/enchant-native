# Security Best Practices — E2EE Android Messaging App

> **Non-negotiable rules for Kotlin/Android native implementation.**
> Every PR must comply. Violations block merge.

---

## 1. Cryptographic Fundamentals

### 1.1 Key Generation — Always Use Platform CSPRNG

```kotlin
val csprng = SecureRandom()  // backed by /dev/urandom
val key = ByteArray(32).also { csprng.nextBytes(it) }
```

**NEVER** `kotlin.random.Random` or `java.util.Random` for crypto. Use `org.signal.libsignal.protocol` for Signal keys — it internally uses `SecureRandom`.

### 1.2 Key Storage

**Asymmetric keys (Identity Key, Signed Pre-Key):** Android Keystore

```kotlin
val spec = KeyGenParameterSpec.Builder("identity_key", KeyProperties.PURPOSE_SIGN)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setKeySize(256).setIsStrongBoxBacked(true).build()
val keyPair = KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
```

**Session/Chain keys:** `EncryptedSharedPreferences`

```kotlin
val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
val prefs = EncryptedSharedPreferences.create(context, "session_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
```

**Key hierarchy:** User PIN → MasterKey (Keystore) → EncryptedSharedPrefs → session/chain/message keys

### 1.3 Never Log Secrets

```kotlin
Log.d("Session", "established: id=${record.sessionVersion}")      // OK
Log.d("Session", "chain key: ${chainKey.encoded}")                // NEVER
Log.d("Session", "plaintext: $msgContent")                        // NEVER
```

ProGuard release rule: `-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); }`

### 1.4 Constant-Time Comparison

```kotlin
fun verifyFingerprint(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    return MessageDigest.isEqual(a, b)  // constant-time on API 26+
}
```

Do NOT use `Arrays.equals()` or `==` on `ByteArray`.

---

## 2. Signal Protocol Implementation

### 2.1 X3DH + Double Ratchet

```kotlin
// X3DH handshake
SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore, identityKeyStore, address).process(bundle)
// Double Ratchet encrypt/decrypt
val cipher = SessionCipher(sessionStore, preKeyStore, identityKeyStore, address)
val ct = cipher.encrypt(plaintext)
val pt = cipher.decrypt(ct)
```

**Forward secrecy:** guaranteed by ratchet step. **Future secrecy:** requires DH ratchet each step (provided by default).

### 2.2 Post-Quantum (Hybrid KEM)

```kotlin
val x3dhSecret = sessionBuilder.process(preKeyBundle)
val kyberSecret = kyberKem.encapsulate(kyberPublicKey)  // via liboqs-java
val combined = Hkdf.deriveSecrets("EnchantHybridV1".toByteArray(), x3dhSecret + kyberSecret, 32)
```

### 2.3 Session Management

```kotlin
// On identity key change — never auto-accept
fun onIdentityKeyChanged(address: SignalProtocolAddress, newKey: IdentityKey) {
    sessionStore.archiveSession(address)
    negotiateNewSession(address, newKey)  // after user confirms
}
// On compromise — delete entirely
fun onSessionCompromised(address: SignalProtocolAddress) {
    sessionStore.deleteSession(address)
    identityKeyStore.removeIdentityKey(address)
}
```

### 2.4 Pre-Key Rotation Schedule

```kotlin
class PreKeyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Rotate signed pre-key every 30 days
        if (System.currentTimeMillis() - lastRotation > 30.days) uploadNewSignedPreKey()
        // Top up OPKs when < 10 remaining
        if (preKeyStore.countOneTimePreKeys() < 10) uploadBatch(100)
        // Clean OPKs older than 90 days
        preKeyStore.getAllPreKeys().forEach { (id, record) ->
            if (age > 90.days) preKeyStore.removePreKey(id)
        }
        return Result.success()
    }
}
```

---

## 3. Database Security

### 3.1 SQLCipher at Rest

```kotlin
val rawKey = Hkdf.deriveSecrets("EnchantDBKeyV2".toByteArray(), userMaterial + deviceSecret, 32)
val db = SQLiteDatabase.openDatabase(path, "", null, SupportFactory(rawKey, OPEN_READWRITE), OPEN_READWRITE)
db.enableWriteAheadLogging()
db.execSQL("PRAGMA cipher_default_kdf_iter = 256000")
db.execSQL("PRAGMA kdf_algorithm = PBKDF2_HMAC_SHA512")
db.execSQL("PRAGMA hmac_algorithm = HMAC_SHA512")
```

### 3.2 Never Log Plaintext

```kotlin
Crashlytics.log("msg processed: id=$messageId")           // OK
Crashlytics.log("decrypted: $plaintext")                  // NEVER
Crashlytics.setCustomKey("last_message", plaintext)        // NEVER
```

### 3.3 Prevent SQL Injection

```kotlin
// GOOD — parameterized
db.rawQuery("SELECT * FROM messages WHERE chat_id = ?", arrayOf(chatId))
// BAD — concatenation
db.rawQuery("SELECT * FROM messages WHERE chat_id = '$chatId'", null)  // NEVER
// Always close cursors: cursor.use { ... }
```

---

## 4. Network Security

### 4.1 TLS 1.3 with Certificate Pinning

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.enchant.chat", "sha256/AAAA...AAA=")  // primary
    .add("api.enchant.chat", "sha256/BBBB...BBB=")  // backup
    .build()
val client = OkHttpClient.Builder()
    .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
        .tlsVersions(TlsVersion.TLS_1_3)
        .cipherSuites(CipherSuite.TLS_AES_256_GCM_SHA384, CipherSuite.TLS_CHACHA20_POLY1305_SHA256)
        .build()))
    .certificatePinner(pinner).build()
```

```xml
<application android:usesCleartextTraffic="false" ...>
```

### 4.2 WebSocket — JWT in Headers, Never URL

```kotlin
Request.Builder().url("wss://api.enchant.chat/ws")
    .addHeader("Authorization", "Bearer $jwt").build()
```

JWT in query params leaks to proxy/access logs — **forbidden**.

### 4.3 Protobuf for Message Envelopes

```protobuf
message Envelope {
    uint32 type = 1;
    bytes source_device = 2;
    bytes content = 3;    // encrypted blob
    uint64 timestamp = 4;
    bytes server_guid = 5;
}
```

### 4.4 Client-Side Rate Limiting

```kotlin
object RateLimiter {
    private val timestamps = mutableMapOf<String, MutableList<Long>>()
    private val MAX = 30; private val WINDOW = 60_000L
    fun canSend(convId: String): Boolean {
        val now = System.currentTimeMillis()
        val list = timestamps.getOrPut(convId) { mutableListOf() }
        list.removeAll { now - it > WINDOW }
        if (list.size >= MAX) return false
        list.add(now); return true
    }
}
```

---

## 5. Android Platform Security

### 5.1 FLAG_SECURE

```kotlin
// Activity
window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
// Compose
DisposableEffect(Unit) {
    (LocalContext.current as Activity).window.addFlags(FLAG_SECURE)
    onDispose { window.clearFlags(FLAG_SECURE) }
}
```

**Required on:** chat screens, safety numbers, backup key, app lock setup.

### 5.2 Biometric App Lock

```kotlin
fun setPin(pin: String) {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 100000, 256)).encoded
    prefs.edit().putString("hash", Base64.encodeToString(hash))
        .putString("salt", Base64.encodeToString(salt)).apply()
}

BiometricPrompt(this, exec, callback).authenticate(PromptInfo.Builder()
    .setTitle("Unlock Enchant")
    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL).build())
```

### 5.3 File-Level Encryption for Media

```kotlin
val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
val c = Cipher.getInstance("AES/GCM/NoPadding")
c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
val encrypted = c.doFinal(file.readBytes())
// Store key alongside the message (itself encrypted via session cipher)
```

### 5.4 ContentProvider Security

```xml
<provider android:exported="false"
    android:readPermission="com.enchant.chat.permission.READ_MEDIA"
    android:writePermission="com.enchant.chat.permission.WRITE_MEDIA" />
<permission android:protectionLevel="signature" />
```

---

## 6. Secure Coding Practices

### 6.1 ProGuard / R8

```
-keep class org.signal.libsignal.protocol.** { *; }
-keep class com.enchant.protocol.** { *; }
-assumenosideeffects class android.util.Log {
    public static int v(...); public static int d(...);
}
```

### 6.2 No Hardcoded Secrets

```kotlin
// GOOD: BuildConfig.TENOR_API_KEY (set via gradle.properties)
// BAD: const val TENOR_API_KEY = "AIzaSyC-..."  // do not commit
```

### 6.3 Input Validation

```kotlin
fun validateMessage(text: String) =
    text.length <= 65536 && text.none { it.code in 0..31 && it != '\n' }
fun validateUsername(name: String) =
    name.length in 3..32 && name.matches(Regex("^[a-zA-Z0-9_]+$"))
```

### 6.4 Internationalization Safety

```kotlin
fun formatFingerprint(digest: ByteArray) =
    digest.joinToString("") { String.format("%02X", it) }.chunked(12).joinToString(" ")
```

Always use fixed format (never locale-dependent) for security-critical strings.

---

## 7. Audit & Incident Response

### 7.1 Logging Strategy

```kotlin
object SecurityLogger {
    fun logEvent(event: String, meta: Map<String, String> = emptyMap()) {
        Log.d("Sec", "$event keys=${meta.keys}")
        meta.forEach { (k, v) -> Crashlytics.setCustomKey("sec_${k}_$event", v) }
    }
    // NEVER pass plaintext as a metadata value
}
```

### 7.2 Crash Reporting

```kotlin
FirebaseCrashlytics.getInstance().log("Session: established")     // OK
FirebaseCrashlytics.getInstance().log("Decrypted: $plaintext")    // NEVER
```

### 7.3 Safety Number Verification

```kotlin
fun computeFingerprint(ours: IdentityKey, theirs: IdentityKey): String {
    val hash = MessageDigest.getInstance("SHA-512").digest(ours.serialize() + theirs.serialize())
    return formatFingerprint(hash)
}
fun verify(remote: String, local: String) = MessageDigest.isEqual(remote.toByteArray(), local.toByteArray())
```

### 7.4 Remote Attestation

```kotlin
// Play Integrity API on key upload
val token = IntegrityManagerFactory.create(context)
    .requestIntegrityToken(IntegrityTokenRequest.builder()
        .setCloudProjectNumber(PROJECT_NUMBER).build()).await()
// On failure: warn user, never force-block
```

---

## Audit Checklist (Run Before Every Release)

- [ ] `grep -r 'Log\.[dvie].*plaintext\|Log\..*decrypt\|Log\..*secret' app/src/` → zero
- [ ] `grep -r '=\s*"AIza\|\"sk-\|apiKey\s*=' app/src/` (excl. BuildConfig) → zero
- [ ] `FLAG_SECURE` on chat, safety numbers, backup key, app lock screens
- [ ] SQLCipher key uses HKDF(user + device secret) — not hardcoded
- [ ] Android Keystore for all long-term asymmetric keys
- [ ] Certificate pinning for all API and WebSocket endpoints
- [ ] `android:usesCleartextTraffic="false"`
- [ ] ProGuard strips debug logging in release
- [ ] BiometricPrompt uses `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL`
- [ ] Message content never reaches Crashlytics, Analytics, or logcat
- [ ] `libsignal-client` version pinned (no `+` ranges)
- [ ] Pre-key worker registered (30-day SPK, 90-day OPK cleanup)
- [ ] `MessageDigest.isEqual()` used for all verification comparisons
- [ ] ContentProviders: `exported="false"` for internal providers
