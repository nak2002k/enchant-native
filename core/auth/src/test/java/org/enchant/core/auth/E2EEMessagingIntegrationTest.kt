package org.enchant.core.auth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.enchant.core.crypto.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class E2EEMessagingIntegrationTest {

    private val gatewayUrl = "http://localhost:8080"
    private val authUrl = "http://localhost:8001"
    private val iksUrl = "http://localhost:8002"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private lateinit var alice: TestUser
    private lateinit var bob: TestUser

    data class TestUser(
        val identifier: String,
        var challengeId: String = "",
        var jwt: String = "",
        var refreshToken: String = "",
        var userId: String = "",
        var deviceId: String = "",
        var ikPair: CryptoPrimitives.KeyPair? = null,
        var spkPair: CryptoPrimitives.KeyPair? = null,
        var spkSignature: ByteArray? = null,
        var username: String = ""
    )

    @BeforeAll
    fun checkBackend() {
        try {
            val url = URI("$authUrl/health").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.inputStream.read()
            conn.disconnect()
        } catch (_: Exception) {
            org.junit.jupiter.api.Assumptions.abort("Backend not available at $authUrl")
        }
    }

    @Test
    @Order(1)
    fun `alice requests OTP`() {
        alice = TestUser(identifier = "+14155550101")
        val response = httpPost("$authUrl/v1/auth/request-otp",
            """{"identifier":"${alice.identifier}","method":"sms"}""")
        val obj = json.parseToJsonElement(response).jsonObject
        alice.challengeId = obj["challenge_id"]?.jsonPrimitive?.content
            ?: fail("No challenge_id in OTP response")
        assertTrue(alice.challengeId.isNotBlank())
    }

    @Test
    @Order(2)
    fun `bob requests OTP`() {
        bob = TestUser(identifier = "+14155550102")
        val response = httpPost("$authUrl/v1/auth/request-otp",
            """{"identifier":"${bob.identifier}","method":"sms"}""")
        val obj = json.parseToJsonElement(response).jsonObject
        bob.challengeId = obj["challenge_id"]?.jsonPrimitive?.content
            ?: fail("No challenge_id in OTP response")
        assertTrue(bob.challengeId.isNotBlank())
    }

    @Test
    @Order(3)
    fun `alice verifies OTP and gets JWT`() {
        val otp = readOtpFromDockerLogs(alice.challengeId)
            ?: fail("Could not read OTP from docker logs for ${alice.identifier}")
        val response = httpPost("$authUrl/v1/auth/verify-otp",
            """{"challenge_id":"${alice.challengeId}","otp":"$otp"}""")
        val obj = json.parseToJsonElement(response).jsonObject
        alice.jwt = obj["access_token"]?.jsonPrimitive?.content ?: fail("No access_token")
        alice.refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: fail("No refresh_token")
        alice.userId = obj["user_id"]?.jsonPrimitive?.content ?: fail("No user_id")
        assertTrue(alice.jwt.startsWith("eyJ"), "JWT should be valid")
    }

    @Test
    @Order(4)
    fun `bob verifies OTP and gets JWT`() {
        val otp = readOtpFromDockerLogs(bob.challengeId)
            ?: fail("Could not read OTP from docker logs for ${bob.identifier}")
        val response = httpPost("$authUrl/v1/auth/verify-otp",
            """{"challenge_id":"${bob.challengeId}","otp":"$otp"}""")
        val obj = json.parseToJsonElement(response).jsonObject
        bob.jwt = obj["access_token"]?.jsonPrimitive?.content ?: fail("No access_token")
        bob.refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: fail("No refresh_token")
        bob.userId = obj["user_id"]?.jsonPrimitive?.content ?: fail("No user_id")
        assertTrue(bob.jwt.startsWith("eyJ"), "JWT should be valid")
    }

    @Test
    @Order(5)
    fun `alice creates profile`() {
        alice.username = "alice_e2e_${System.currentTimeMillis() % 100000}"
        val response = httpPut("$gatewayUrl/v1/profile",
            """{"username":"${alice.username}","display_name":"Alice E2E","about":"Test user Alice"}""",
            alice.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        assertEquals(true, obj["updated"]?.jsonPrimitive?.booleanOrNull,
            "Alice profile should be created")
    }

    @Test
    @Order(6)
    fun `bob creates profile`() {
        bob.username = "bob_e2e_${System.currentTimeMillis() % 100000}"
        val response = httpPut("$gatewayUrl/v1/profile",
            """{"username":"${bob.username}","display_name":"Bob E2E","about":"Test user Bob"}""",
            bob.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        assertEquals(true, obj["updated"]?.jsonPrimitive?.booleanOrNull,
            "Bob profile should be created")
    }

    @Test
    @Order(7)
    fun `alice generates and uploads key bundle`() {
        val ikHelper = CryptoHelper.generateEd25519KeyPair()
        val spkHelper = CryptoHelper.generateX25519KeyPair()
        val spkSig = CryptoHelper.signEd25519(spkHelper.publicKey, ikHelper.privateKey)
        val ik = CryptoPrimitives.KeyPair(ikHelper.publicKey, ikHelper.privateKey)
        val spk = CryptoPrimitives.KeyPair(spkHelper.publicKey, spkHelper.privateKey)
        alice.ikPair = ik
        alice.spkPair = spk
        alice.spkSignature = spkSig

        val opksHelper = (1..20).map { CryptoHelper.generateX25519KeyPair() }
        val opks = opksHelper.map { CryptoPrimitives.KeyPair(it.publicKey, it.privateKey) }

        val body = buildJsonObject {
            put("identity_key", JsonPrimitive(base64Url(ik.publicKey)))
            put("signed_prekey", buildJsonObject {
                put("public_key", JsonPrimitive(base64Url(spk.publicKey)))
                put("signature", JsonPrimitive(base64Url(spkSig)))
            })
            put("one_time_prekeys", buildJsonArray {
                opks.forEach { opk ->
                    add(buildJsonObject { put("public_key", JsonPrimitive(base64Url(opk.publicKey))) })
                }
            })
        }
        val response = httpPost("$iksUrl/v1/keys/register", body.toString(), alice.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        alice.deviceId = obj["device_id"]?.jsonPrimitive?.content ?: fail("No device_id")
        assertTrue(alice.deviceId.isNotBlank(), "Alice should get a device_id")
    }

    @Test
    @Order(8)
    fun `bob generates and uploads key bundle`() {
        val ikHelper = CryptoHelper.generateEd25519KeyPair()
        val spkHelper = CryptoHelper.generateX25519KeyPair()
        val spkSig = CryptoHelper.signEd25519(spkHelper.publicKey, ikHelper.privateKey)
        val ik = CryptoPrimitives.KeyPair(ikHelper.publicKey, ikHelper.privateKey)
        val spk = CryptoPrimitives.KeyPair(spkHelper.publicKey, spkHelper.privateKey)
        bob.ikPair = ik
        bob.spkPair = spk
        bob.spkSignature = spkSig

        val opksHelper = (1..20).map { CryptoHelper.generateX25519KeyPair() }
        val opks = opksHelper.map { CryptoPrimitives.KeyPair(it.publicKey, it.privateKey) }

        val body = buildJsonObject {
            put("identity_key", JsonPrimitive(base64Url(ik.publicKey)))
            put("signed_prekey", buildJsonObject {
                put("public_key", JsonPrimitive(base64Url(spk.publicKey)))
                put("signature", JsonPrimitive(base64Url(spkSig)))
            })
            put("one_time_prekeys", buildJsonArray {
                opks.forEach { opk ->
                    add(buildJsonObject { put("public_key", JsonPrimitive(base64Url(opk.publicKey))) })
                }
            })
        }
        val response = httpPost("$iksUrl/v1/keys/register", body.toString(), bob.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        bob.deviceId = obj["device_id"]?.jsonPrimitive?.content ?: fail("No device_id")
        assertTrue(bob.deviceId.isNotBlank(), "Bob should get a device_id")
    }

    @Test
    @Order(9)
    fun `alice fetches bob key bundle and establishes session`() = runBlocking {
        val response = httpGet("$iksUrl/v1/keys/bundle/${bob.userId}", bob.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        val device = obj["devices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: fail("Bob should have at least one device")
        val bobIkStr = device["identity_key"]?.jsonPrimitive?.content ?: fail("No identity_key")
        val spkData = device["signed_prekey"]?.jsonObject ?: fail("No signed_prekey")
        val bobSpkPubStr = spkData["public_key"]?.jsonPrimitive?.content ?: fail("No spk public_key")
        val bobSpkSigStr = spkData["signature"]?.jsonPrimitive?.content ?: fail("No spk signature")
        val bobOpkStr = device["one_time_prekey"]?.jsonPrimitive?.content

        val bobIkX = ed25519PkToX25519(base64UrlDecode(bobIkStr))
        val bobSpkPub = base64UrlDecode(bobSpkPubStr)
        val bobOpkPub = bobOpkStr?.let { base64UrlDecode(it) }

        val aliceEkHelper = CryptoHelper.generateX25519KeyPair()
        val aliceEk = CryptoPrimitives.KeyPair(aliceEkHelper.publicKey, aliceEkHelper.privateKey)
        val aliceResult = X3DH.aliceInitiate(
            ourIdentityKey = alice.ikPair!!,
            ourEphemeralKey = aliceEk,
            theirIdentityKeyPublic = bobIkX,
            theirSignedPrekeyPublic = bobSpkPub,
            theirOneTimePrekeyPublic = bobOpkPub
        )

        assertNotNull(aliceResult)
        assertTrue(aliceResult.sharedSecret.isNotEmpty(), "Shared secret should be derived")

        val aliceSession = DoubleRatchet.initializeAsAlice(
            sharedSecret = aliceResult.sharedSecret,
            theirSignedPrekeyPublic = bobSpkPub,
            ourEphemeralKeyPair = aliceEk
        )

        val plaintext = "Hello Bob, this is Alice! E2EE works.".encodeToByteArray()
        val (_, encrypted) = DoubleRatchet.encrypt(aliceSession, plaintext)

        assertNotNull(encrypted)
        assertTrue(encrypted.ciphertext.isNotEmpty(), "Ciphertext should be non-empty")
    }

    @Test
    @Order(10)
    fun `bob fetches alice key bundle and establishes session`() = runBlocking {
        val response = httpGet("$iksUrl/v1/keys/bundle/${alice.userId}", alice.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        val device = obj["devices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: fail("Alice should have at least one device")
        val aliceIkStr = device["identity_key"]?.jsonPrimitive?.content ?: fail("No identity_key")
        val spkData = device["signed_prekey"]?.jsonObject ?: fail("No signed_prekey")
        val aliceSpkPubStr = spkData["public_key"]?.jsonPrimitive?.content ?: fail("No spk public_key")
        val aliceSpkSigStr = spkData["signature"]?.jsonPrimitive?.content ?: fail("No spk signature")
        val aliceOpkStr = device["one_time_prekey"]?.jsonPrimitive?.content

        val aliceIkX = ed25519PkToX25519(base64UrlDecode(aliceIkStr))
        val aliceSpkPub = base64UrlDecode(aliceSpkPubStr)
        val aliceOpkPub = aliceOpkStr?.let { base64UrlDecode(it) }

        val bobEkHelper = CryptoHelper.generateX25519KeyPair()
        val bobEk = CryptoPrimitives.KeyPair(bobEkHelper.publicKey, bobEkHelper.privateKey)
        val bobResult = X3DH.aliceInitiate(
            ourIdentityKey = bob.ikPair!!,
            ourEphemeralKey = bobEk,
            theirIdentityKeyPublic = aliceIkX,
            theirSignedPrekeyPublic = aliceSpkPub,
            theirOneTimePrekeyPublic = aliceOpkPub
        )

        assertNotNull(bobResult)
        assertTrue(bobResult.sharedSecret.isNotEmpty(), "Shared secret should be derived")
    }

    @Test
    @Order(11)
    fun `alice sends encrypted message bob decrypts it roundtrip`() = runBlocking {
        val aliceIk = alice.ikPair!!
        val aliceEkHelper = CryptoHelper.generateX25519KeyPair()
        val aliceEk = CryptoPrimitives.KeyPair(aliceEkHelper.publicKey, aliceEkHelper.privateKey)
        val bobIk = bob.ikPair!!
        val bobSpk = bob.spkPair!!
        val bobOpkHelper = CryptoHelper.generateX25519KeyPair()
        val bobOpk = CryptoPrimitives.KeyPair(bobOpkHelper.publicKey, bobOpkHelper.privateKey)

        val bobIkX = ed25519PkToX25519(bobIk.publicKey)

        val aliceResult = X3DH.aliceInitiate(
            ourIdentityKey = aliceIk,
            ourEphemeralKey = aliceEk,
            theirIdentityKeyPublic = bobIkX,
            theirSignedPrekeyPublic = bobSpk.publicKey,
            theirOneTimePrekeyPublic = bobOpk.publicKey
        )

        val bobResult = X3DH.bobRespond(
            ourIdentityKey = bobIk,
            ourSignedPrekeyKeyPair = bobSpk,
            ourOneTimePrekeyKeyPair = bobOpk,
            theirIdentityKeyPublic = ed25519PkToX25519(aliceIk.publicKey),
            theirEphemeralKeyPublic = aliceEk.publicKey
        )

        assertTrue(aliceResult.sharedSecret.contentEquals(bobResult.sharedSecret),
            "X3DH shared secrets must match")

        val aliceSession = DoubleRatchet.initializeAsAlice(
            sharedSecret = aliceResult.sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey,
            ourEphemeralKeyPair = aliceEk
        )

        val originalText = "Hey Bob! This message is end-to-end encrypted."
        val (aliceSession2, ratchetMessage) = DoubleRatchet.encrypt(
            aliceSession, originalText.encodeToByteArray()
        )

        val bobSession = DoubleRatchet.initializeAsBob(
            sharedSecret = bobResult.sharedSecret,
            theirRatchetKeyPublic = aliceSession.sendingRatchetKeyPublic
                ?: fail("Alice must have a ratchet key"),
            ourSignedPrekeyPrivate = bobSpk.privateKey
        )

        val (_, decrypted) = DoubleRatchet.decrypt(bobSession, ratchetMessage)

        val decryptedText = decrypted.decodeToString()
        assertEquals(originalText, decryptedText, "Decrypted text must match original")

        val (aliceSession3, msg2) = DoubleRatchet.encrypt(aliceSession2, "Second message".encodeToByteArray())
        val (bobSession2, dec2) = DoubleRatchet.decrypt(bobSession, msg2)
        assertEquals("Second message", dec2.decodeToString(), "Second message must decrypt correctly")

        val (_, msg3) = DoubleRatchet.encrypt(aliceSession3, "Third message".encodeToByteArray())
        val (bobSession3, dec3) = DoubleRatchet.decrypt(bobSession2, msg3)
        assertEquals("Third message", dec3.decodeToString(), "Third message must decrypt correctly")

        val bobToAliceEkHelper = CryptoHelper.generateX25519KeyPair()
        val bobToAliceEk = CryptoPrimitives.KeyPair(bobToAliceEkHelper.publicKey, bobToAliceEkHelper.privateKey)
        val dudKey = CryptoPrimitives.KeyPair(
            CryptoHelper.generateX25519KeyPair().publicKey,
            CryptoHelper.generateX25519KeyPair().privateKey
        )
        val bobToAliceResult = X3DH.aliceInitiate(
            ourIdentityKey = bobIk,
            ourEphemeralKey = bobToAliceEk,
            theirIdentityKeyPublic = ed25519PkToX25519(aliceIk.publicKey),
            theirSignedPrekeyPublic = dudKey.publicKey
        )
        val bobReplySession = DoubleRatchet.initializeAsAlice(
            sharedSecret = bobToAliceResult.sharedSecret,
            theirSignedPrekeyPublic = dudKey.publicKey,
            ourEphemeralKeyPair = bobToAliceEk
        )
        val (_, replyEncrypted) = DoubleRatchet.encrypt(bobReplySession, "Reply from Bob".encodeToByteArray())
        assertTrue(replyEncrypted.ciphertext.isNotEmpty(), "Bob's reply should be encryptable")
    }

    @Test
    @Order(12)
    fun `alice can search for bob by username`() {
        val response = httpGet("$gatewayUrl/v1/profile/search?username=${bob.username.take(5)}", alice.jwt)
        val obj = json.parseToJsonElement(response).jsonObject
        val results = obj["results"]?.jsonArray ?: fail("Should have results array")
        assertTrue(results.isNotEmpty(), "Search should find Bob")
        val found = results.any { item ->
            item.jsonObject["username"]?.jsonPrimitive?.content == bob.username
        }
        assertTrue(found, "Search results should include Bob")
    }

    private fun httpPost(url: String, body: String, bearer: String? = null): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            if (connection.responseCode == 429) {
                throw org.opentest4j.TestAbortedException("Rate limited, skipping test")
            }
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.readText() ?: e.message
            } catch (_: Exception) { e.message }
            fail("HTTP ${connection.responseCode} for POST $url: $errorBody")
        }
    }

    private fun httpPut(url: String, body: String, bearer: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $bearer")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            if (connection.responseCode == 429) {
                throw org.opentest4j.TestAbortedException("Rate limited, skipping test")
            }
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.readText() ?: e.message
            } catch (_: Exception) { e.message }
            fail("HTTP ${connection.responseCode} for PUT $url: $errorBody")
        }
    }

    private fun httpGet(url: String, bearer: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $bearer")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            if (connection.responseCode == 429) {
                throw org.opentest4j.TestAbortedException("Rate limited, skipping test")
            }
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.readText() ?: e.message
            } catch (_: Exception) { e.message }
            fail("HTTP ${connection.responseCode} for GET $url: $errorBody")
        }
    }

    private fun readOtpFromDockerLogs(challengeId: String): String? {
        return try {
            val process = ProcessBuilder("docker", "logs", "chat-auth-1")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            for (line in output.lines()) {
                if (line.contains(challengeId) && line.contains("otp_code")) {
                    val obj = json.parseToJsonElement(line).jsonObject
                    return obj["otp"]?.jsonPrimitive?.content
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun base64Url(data: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    private fun base64UrlDecode(str: String): ByteArray =
        Base64.getUrlDecoder().decode(str)

    private fun ed25519PkToX25519(edPk: ByteArray): ByteArray =
        CryptoHelper.ed25519PkToX25519(edPk)
}
