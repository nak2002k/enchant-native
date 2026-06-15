package org.enchant.core.crypto

/**
 * JNI bridge to libenchantcrypto native library.
 * Auto-generated from enchant/api.h — do not edit manually.
 * The native .so file is loaded from jniLibs/ for each ABI.
 *
 * All return values follow the api.h convention:
 *   0 = ENCHANT_SUCCESS, negative = error code
 */
object EnchantCrypto {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    // --- Error codes ---
    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_DECRYPTION_FAILED = -6
    const val ERROR_SIGNATURE_INVALID = -7
    const val ERROR_INVALID_FORMAT = -11
    const val ERROR_INTERNAL = -99

    // --- Native functions (auto-generated) ---
    external fun enchant_init(): Int
    external fun enchant_version(): String
    external fun enchant_random_bytes(buf: ByteArray, len: Long)
    external fun enchant_x25519_keypair(publicKey: ByteArray, privateKey: ByteArray): Int
    external fun enchant_x25519_dh(privateKey: ByteArray, publicKey: ByteArray, sharedSecret: ByteArray): Int
    external fun enchant_ed25519_keypair(publicKey: ByteArray, privateSeed: ByteArray): Int
    external fun enchant_ed25519_sign(message: ByteArray, messageLen: Long, privateSeed: ByteArray, signature: ByteArray): Int
    external fun enchant_ed25519_verify(message: ByteArray, messageLen: Long, signature: ByteArray, publicKey: ByteArray): Int
    external fun enchant_xchacha20_encrypt(plaintext: ByteArray, plaintextLen: Long, key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ciphertextCapacity: Long): Int
    external fun enchant_xchacha20_decrypt(ciphertext: ByteArray, ciphertextLen: Long, key: ByteArray, nonce: ByteArray, plaintext: ByteArray, plaintextCapacity: Long): Int
    external fun enchant_hkdf_sha256(ikm: ByteArray, ikmLen: Long, salt: ByteArray, saltLen: Long, info: ByteArray, infoLen: Long, okm: ByteArray, okmLen: Long): Int
    external fun enchant_sha256(data: ByteArray, len: Long, hash: ByteArray): Int
    external fun enchant_hmac_sha256(key: ByteArray, keyLen: Long, data: ByteArray, dataLen: Long, mac: ByteArray): Int
    external fun enchant_base64_encode(data: ByteArray, len: Long, output: String, outputLen: Long): Int
    external fun enchant_base64_decode(input: String, output: ByteArray, outputLen: Long): Int
    external fun enchant_argon2id_hash(plaintext: String, plaintextLen: Long, output: String, outputLen: Long): Int
    external fun enchant_argon2id_verify(hash: String, hashLen: Long, plaintext: String, plaintextLen: Long): Int
    external fun enchant_aes_256_keygen(key: ByteArray): Int
    external fun enchant_aes_192_keygen(key: ByteArray): Int
    external fun enchant_aes_128_keygen(key: ByteArray): Int
    external fun enchant_aes_is_available(): Int
    external fun enchant_aes_init(): Int
    external fun enchant_aes_256_gcm_encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, plaintextLen: Long, aad: ByteArray, aadLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_aes_256_gcm_decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, aad: ByteArray, aadLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_aes_256_ctr_encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray): Int
    external fun enchant_aes_256_ctr_decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray): Int
    external fun enchant_aes_256_cbc_encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_aes_256_cbc_decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_aes_cmac(key: ByteArray, keyLen: Long, data: ByteArray, dataLen: Long, mac: ByteArray): Int
    external fun enchant_aes_256_siv_encrypt(key: ByteArray, plaintext: ByteArray, plaintextLen: Long, aad: ByteArray, aadLen: Long, nonce: ByteArray, nonceLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_aes_256_siv_decrypt(key: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, aad: ByteArray, aadLen: Long, nonce: ByteArray, nonceLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_chacha20_poly1305_ietf_encrypt(plaintext: ByteArray, plaintextLen: Long, aad: ByteArray, aadLen: Long, key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_chacha20_poly1305_ietf_decrypt(ciphertext: ByteArray, ciphertextLen: Long, aad: ByteArray, aadLen: Long, key: ByteArray, nonce: ByteArray, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_hpke_seal(recipientPublicKey: ByteArray, info: ByteArray, infoLen: Long, aad: ByteArray, aadLen: Long, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_hpke_open(recipientPrivateKey: ByteArray, info: ByteArray, infoLen: Long, aad: ByteArray, aadLen: Long, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_secure_zero(ptr: Long, len: Long)
    external fun enchant_secure_alloc(ptr: Long, len: Long): Int
    external fun enchant_secure_free(ptr: Long, len: Long)
    external fun enchant_sha384(data: ByteArray, len: Long, hash: ByteArray): Int
    external fun enchant_sha512(data: ByteArray, len: Long, hash: ByteArray): Int
    external fun enchant_hmac_sha512(key: ByteArray, keyLen: Long, data: ByteArray, dataLen: Long, mac: ByteArray): Int
    external fun enchant_constant_time_equals(a: ByteArray, b: ByteArray, len: Long, result: Long): Int
    external fun enchant_ed25519_sk_to_x25519(ed25519Sk: ByteArray, x25519Sk: ByteArray): Int
    external fun enchant_ed25519_pk_to_x25519(ed25519Pk: ByteArray, x25519Pk: ByteArray): Int
    external fun enchant_attachment_encrypt(plaintext: ByteArray, plaintextLen: Long, key: ByteArray, keyLen: Long, ciphertext: ByteArray, ciphertextCapacity: Long, ciphertextLen: Long, mac: ByteArray): Int
    external fun enchant_attachment_decrypt(ciphertext: ByteArray, ciphertextLen: Long, key: ByteArray, keyLen: Long, mac: ByteArray, plaintext: ByteArray, plaintextCapacity: Long, plaintextLen: Long): Int
    external fun enchant_aes_gcm_siv_encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, plaintextLen: Long, aad: ByteArray, aadLen: Long, ciphertext: ByteArray, ciphertextLen: Long, tag: ByteArray): Int
    external fun enchant_aes_gcm_siv_decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, aad: ByteArray, aadLen: Long, tag: ByteArray, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_rsa_generate_keypair(keySizeBits: Int, privateKey: ByteArray, privateKeyLen: Long, publicKey: ByteArray, publicKeyLen: Long): Int
    external fun enchant_x509_create_self_signed(privateKey: ByteArray, privateKeyLen: Long, publicKey: ByteArray, publicKeyLen: Long, subjectName: String, validitySeconds: Long, certDer: ByteArray, certDerLen: Long): Int
    external fun enchant_x509_validate(certDer: ByteArray, certDerLen: Long, trustedCaDer: ByteArray, caDerLen: Long, validOut: Long): Int
    external fun enchant_rsa_wrap_key(recipientPublicKey: ByteArray, keyLen: Long, plaintextKey: ByteArray, plaintextKeyLen: Long, wrappedKey: ByteArray, wrappedKeyLen: Long): Int
    external fun enchant_rsa_unwrap_key(privateKey: ByteArray, keyLen: Long, wrappedKey: ByteArray, wrappedKeyLen: Long, plaintextKey: ByteArray, plaintextKeyLen: Long): Int
    external fun enchant_device_transfer_create(transferKey: ByteArray, sourceDeviceId: Int, sourceDeviceName: String, keyData: ByteArray, keyDataLen: Long, packageOut: ByteArray, packageOutLen: Long): Int
    external fun enchant_device_transfer_import(package: ByteArray, packageLen: Long, transferKey: ByteArray, keyData: ByteArray, keyDataLen: Long): Int
    external fun enchant_device_transfer_rsa_create(sourceDeviceId: Int, sourceDeviceName: String, keyData: ByteArray, keyDataLen: Long, recipientPublicKey: ByteArray, recipientKeyLen: Long, senderCertDer: ByteArray, senderCertLen: Long, packageOut: ByteArray, packageOutLen: Long): Int
    external fun enchant_device_transfer_rsa_import(package: ByteArray, packageLen: Long, privateKey: ByteArray, privateKeyLen: Long, trustedCaDer: ByteArray, caDerLen: Long, keyData: ByteArray, keyDataLen: Long, attestationValid: Long): Int
    external fun enchant_backup_encrypt_frame(masterKey: ByteArray, plaintext: ByteArray, plaintextLen: Long, frameType: Int, frameNumber: Int, frameOut: ByteArray, frameOutLen: Long): Int
    external fun enchant_backup_decrypt_frame(masterKey: ByteArray, frame: ByteArray, frameLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_backup_derive_key(accountKey: ByteArray, backupKey: ByteArray): Int
    external fun enchant_backup_derive_media_key(backupKey: ByteArray, mediaId: String, mediaKey: ByteArray): Int
    external fun enchant_backup_create_transfer(backupKey: ByteArray, targetPublicKey: ByteArray, packageOut: ByteArray, packageOutLen: Long): Int
    external fun enchant_backup_receive_transfer(package: ByteArray, packageLen: Long, devicePrivateKey: ByteArray, backupKey: ByteArray): Int
    external fun enchant_sesame_create_validator(validatorOut: Long): Int
    external fun enchant_sesame_destroy_validator(validator: Long)
    external fun enchant_sesame_add_trust_root(validator: Long, trustRoot: ByteArray, rootLen: Long): Int
    external fun enchant_sesame_set_own_identity(validator: Long, ownUuid: String): Int
    external fun enchant_sesame_validate_sender(validator: Long, certData: ByteArray, certLen: Long, validationTimestamp: Long, trustLevelOut: Long, certificateValidOut: Long, keyChangedOut: Long): Int
    external fun enchant_sesame_establish_trust(validator: Long, senderUuid: String, senderIdentityKey: ByteArray, senderDeviceId: Int, trustLevel: Int, currentTimestamp: Long): Int
    external fun enchant_sesame_verify_identity(validator: Long, senderUuid: String, senderIdentityKey: ByteArray, senderDeviceId: Int, currentTimestamp: Long, verifiedOut: Long): Int
    external fun enchant_sesame_compute_safety_number(senderIdentityKey: ByteArray, recipientIdentityKey: ByteArray, senderUuid: String, recipientUuid: String, safetyNumberOut: ByteArray): Int
    external fun enchant_sesame_generate_trust_token(identitySeed: ByteArray, senderIdentityKey: ByteArray, senderUuid: String, expiration: Long, tokenOut: ByteArray, tokenOutLen: Long): Int
    external fun enchant_sesame_verify_trust_token(identityPublicKey: ByteArray, token: ByteArray, tokenLen: Long, expectedSenderUuid: String, validationTime: Long, validOut: Long): Int
    external fun enchant_sesame_add_revoked_server_key(validator: Long, keyId: Int): Int
    external fun enchant_sesame_get_aggregated_trust(validator: Long, senderUuid: String, trustLevelOut: Long): Int
    external fun enchant_group_send_endorsement_keygen(privateKey: ByteArray, publicKey: ByteArray): Int
    external fun enchant_group_send_endorsement_sign(privateKey: ByteArray, groupId: ByteArray, endorsementOut: ByteArray, endorsementLen: Long): Int
    external fun enchant_group_send_endorsement_verify(publicKey: ByteArray, endorsement: ByteArray, endorsementLen: Long, groupId: ByteArray, validOut: Long): Int
    external fun enchant_call_link_params_generate(secretParams: ByteArray, publicParams: ByteArray): Int
    external fun enchant_call_link_credential_issue(secretParams: ByteArray, groupId: ByteArray, credentialOut: ByteArray, credentialLen: Long): Int
    external fun enchant_call_link_credential_present(secretParams: ByteArray, credential: ByteArray, credentialLen: Long, groupId: ByteArray, presentationOut: ByteArray, presentationLen: Long): Int
    external fun enchant_call_link_credential_verify(publicParams: ByteArray, groupId: ByteArray, presentation: ByteArray, presentationLen: Long, validOut: Long): Int
    external fun enchant_x25519_keygen(privateKey: ByteArray, publicKey: ByteArray): Int
    external fun enchant_x25519_dh_pairwise(privateKey1: ByteArray, publicKey1: ByteArray, privateKey2: ByteArray, publicKey2: ByteArray, sharedSecret1Out: ByteArray, sharedSecret2Out: ByteArray): Int
    external fun enchant_blind_keygen(privateKey: ByteArray, publicKey: ByteArray): Int
    external fun enchant_blind_point(privateKey: ByteArray, point: ByteArray, blindedOut: ByteArray): Int
    external fun enchant_unblind_point(privateKey: ByteArray, blindedPoint: ByteArray, unblindedOut: ByteArray): Int
    external fun enchant_poksho_statement_add(statementOut: Long, name: String, groupElement: ByteArray, groupElementLen: Long): Int
    external fun enchant_poksho_statement_add_scalar(statement: Long, name: String, scalar: ByteArray, scalarLen: Long): Int
    external fun enchant_poksho_prove(statement: Long, witness: ByteArray, witnessLen: Long, msg: ByteArray, msgLen: Long, proofOut: ByteArray, proofLen: Long): Int
    external fun enchant_poksho_verify(statement: Long, proof: ByteArray, proofLen: Long, msg: ByteArray, msgLen: Long, validOut: Long): Int
    external fun enchant_poksho_statement_destroy(statement: Long)
    external fun enchant_identity_store_create(storeOut: Long): Int
    external fun enchant_identity_store_destroy(store: Long)
    external fun enchant_session_store_create(storeOut: Long): Int
    external fun enchant_session_store_destroy(store: Long)
    external fun enchant_identity_store_get_key_pair(store: Long, publicKey: ByteArray, privateKey: ByteArray): Int
    external fun enchant_identity_store_set_registration_id(store: Long, registrationId: Int): Int
    external fun enchant_identity_store_get_registration_id(store: Long, registrationIdOut: Long): Int
    external fun enchant_identity_store_save_identity(store: Long, addressName: String, deviceId: Int, identityKey: ByteArray): Int
    external fun enchant_identity_store_is_trusted(store: Long, addressName: String, deviceId: Int, identityKey: ByteArray, direction: Int, trustedOut: Long): Int
    external fun enchant_identity_store_set_trust(store: Long, addressName: String, deviceId: Int, trusted: Int): Int
    external fun enchant_session_manager_create(identityStore: Long, sessionStore: Long, managerOut: Long): Int
    external fun enchant_session_manager_destroy(manager: Long)
    external fun enchant_session_manager_establish(manager: Long, addressName: String, deviceId: Int, identityKey: ByteArray, signedPrekey: ByteArray, signedPrekeySig: ByteArray, signedPrekeySigLen: Long, oneTimePrekey: ByteArray, registrationId: Int): Int
    external fun enchant_session_manager_encrypt(manager: Long, addressName: String, deviceId: Int, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long, messageTypeOut: Long): Int
    external fun enchant_session_manager_decrypt(manager: Long, addressName: String, deviceId: Int, ciphertext: ByteArray, ciphertextLen: Long, messageType: Int, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_session_manager_has_session(manager: Long, addressName: String, deviceId: Int, hasSessionOut: Long): Int
    external fun enchant_session_manager_archive_session(manager: Long, addressName: String, deviceId: Int): Int
    external fun enchant_session_builder_create(identityStore: Long, sessionStore: Long, builderOut: Long): Int
    external fun enchant_session_builder_destroy(builder: Long)
    external fun enchant_session_builder_process_bundle(builder: Long, addressName: String, deviceId: Int, identityKey: ByteArray, signedPrekey: ByteArray, signedPrekeySig: ByteArray, signedPrekeySigLen: Long, oneTimePrekey: ByteArray, registrationId: Int): Int
    external fun enchant_session_cipher_create(identityStore: Long, sessionStore: Long, cipherOut: Long): Int
    external fun enchant_session_cipher_destroy(cipher: Long)
    external fun enchant_session_cipher_encrypt(cipher: Long, addressName: String, deviceId: Int, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_session_cipher_decrypt(cipher: Long, addressName: String, deviceId: Int, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_session_record_serialize(sessionStateData: ByteArray, sessionStateLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_session_record_deserialize(data: ByteArray, dataLen: Long, sessionStateOutput: ByteArray, sessionStateLen: Long): Int
    external fun enchant_kem_keypair(kemType: Int, publicKey: ByteArray, publicKeyLen: Long, secretKey: ByteArray, secretKeyLen: Long): Int
    external fun enchant_kem_encapsulate(kemType: Int, publicKey: ByteArray, publicKeyLen: Long, ciphertext: ByteArray, ciphertextLen: Long, sharedSecret: ByteArray): Int
    external fun enchant_kem_decapsulate(kemType: Int, secretKey: ByteArray, secretKeyLen: Long, ciphertext: ByteArray, ciphertextLen: Long, sharedSecret: ByteArray): Int
    external fun enchant_kem_public_key_size(kemType: Int, sizeOut: Long): Int
    external fun enchant_kem_secret_key_size(kemType: Int, sizeOut: Long): Int
    external fun enchant_kem_ciphertext_size(kemType: Int, sizeOut: Long): Int
    external fun enchant_kem_is_available(kemType: Int, availableOut: Long): Int
    external fun enchant_kem_serialize_public_key(kemType: Int, rawKey: ByteArray, rawLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_kem_deserialize_public_key(data: ByteArray, dataLen: Long, kemTypeOut: Long, rawKey: ByteArray, rawLen: Long): Int
    external fun enchant_kem_serialize_secret_key(kemType: Int, rawKey: ByteArray, rawLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_kem_deserialize_secret_key(data: ByteArray, dataLen: Long, kemTypeOut: Long, rawKey: ByteArray, rawLen: Long): Int
    external fun enchant_kem_serialize_ciphertext(kemType: Int, rawCt: ByteArray, rawLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_kem_deserialize_ciphertext(data: ByteArray, dataLen: Long, kemTypeOut: Long, rawCt: ByteArray, rawLen: Long): Int
    external fun enchant_fingerprint_generate(version: Int, iterations: Int, localName: String, localKey: ByteArray, remoteName: String, remoteKey: ByteArray, displayableOut: ByteArray, displayableOutLen: Long, scannableOut: ByteArray, scannableLen: Long): Int
    external fun enchant_fingerprint_compare(scannableA: ByteArray, lenA: Long, scannableB: ByteArray, lenB: Long, matchOut: Long): Int
    external fun enchant_safety_number_generate(senderIdentityKey: ByteArray, recipientIdentityKey: ByteArray, senderUuid: String, recipientUuid: String, safetyNumberOut: ByteArray, safetyNumberLen: Long): Int
    external fun enchant_safety_number_compare(safetyNumberA: ByteArray, lenA: Long, safetyNumberB: ByteArray, lenB: Long, matchOut: Long): Int
    external fun enchant_sign_alternate_identity(privateKey: ByteArray, otherIdentityKey: ByteArray, signatureOut: ByteArray): Int
    external fun enchant_verify_alternate_identity(publicKey: ByteArray, otherIdentityKey: ByteArray, signature: ByteArray, validOut: Long): Int
    external fun enchant_calculate_chunk_size(dataSize: Long): Long
    external fun enchant_incremental_mac_create(key: ByteArray, keyLen: Long, dataSize: Long, macOut: Long): Int
    external fun enchant_incremental_mac_destroy(mac: Long)
    external fun enchant_incremental_mac_update(mac: Long, data: ByteArray, dataLen: Long, macOut: ByteArray, macLen: Long): Int
    external fun enchant_incremental_mac_finalize(mac: Long, finalMac: ByteArray, macLen: Long): Int
    external fun enchant_incremental_mac_validator_create(key: ByteArray, keyLen: Long, dataSize: Long, expectedMacs: ByteArray, expectedMacsLen: Long, macSize: Long, validatorOut: Long): Int
    external fun enchant_incremental_mac_validator_destroy(validator: Long)
    external fun enchant_incremental_mac_validator_update(validator: Long, data: ByteArray, dataLen: Long, bytesValidated: Long): Int
    external fun enchant_incremental_mac_validator_finalize(validator: Long, bytesValidated: Long): Int
    external fun enchant_sealed_sender_encrypt_v1(recipientPublicKey: ByteArray, senderIdentityPrivate: ByteArray, senderIdentityPublic: ByteArray, plaintext: ByteArray, plaintextLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_sealed_sender_decrypt_v1(recipientPrivateKey: ByteArray, recipientPublicKey: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long, senderIdentityKeyOut: ByteArray): Int
    external fun enchant_server_certificate_create(keyId: Int, publicKey: ByteArray, privateKey: ByteArray, certOut: ByteArray, certLen: Long): Int
    external fun enchant_server_certificate_validate(certData: ByteArray, certLen: Long, trustRoot: ByteArray, validOut: Long): Int
    external fun enchant_sender_certificate_create(senderUuid: String, senderE164: ByteArray, senderDeviceId: Int, expiration: Long, identityKey: ByteArray, serverCertData: ByteArray, serverCertLen: Long, serverPrivateKey: ByteArray, certOut: ByteArray, certLen: Long): Int
    external fun enchant_sender_certificate_validate(certData: ByteArray, certLen: Long, serverTrustRoot: ByteArray, validationTime: Long, validOut: Long): Int
    external fun enchant_usmc_create(msgType: Int, senderCertData: ByteArray, senderCertLen: Long, plaintext: ByteArray, plaintextLen: Long, contentHint: Int, groupId: ByteArray, groupIdLen: Long, usmcOut: ByteArray, usmcLen: Long): Int
    external fun enchant_usmc_serialize(usmcData: ByteArray, usmcLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_usmc_deserialize(data: ByteArray, dataLen: Long, usmcOut: ByteArray, usmcLen: Long, msgTypeOut: Long): Int
    external fun enchant_prekey_generate_batch(count: Int, startId: Int, keysOut: ByteArray, keysLen: Long): Int
    external fun enchant_prekey_generate_signed(prekeyId: Int, identityPrivateKey: ByteArray, signedPrekeyPublic: ByteArray, signedPrekeyPrivate: ByteArray, signature: ByteArray, signatureLen: Long): Int
    external fun enchant_prekey_generate_kyber_batch(count: Int, startId: Int, kemType: Int, keysOut: ByteArray, keysLen: Long): Int
    external fun enchant_mls_group_create(groupId: ByteArray, groupIdLen: Long, epochSecret: ByteArray, groupStateOut: ByteArray, groupStateLen: Long): Int
    external fun enchant_agent_identity_create(publicKey: ByteArray, privateKey: ByteArray): Int
    external fun enchant_agent_session_initiate(agentPrivateKey: ByteArray, serverPublicKey: ByteArray, sharedSecret: ByteArray, ephemeralPublic: ByteArray): Int
    external fun enchant_agent_session_respond(serverPrivateKey: ByteArray, agentPublicKey: ByteArray, agentEphemeralPublic: ByteArray, sharedSecret: ByteArray): Int
    external fun enchant_agent_encrypt(key: ByteArray, keyLen: Long, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_agent_decrypt(key: ByteArray, keyLen: Long, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_multi_recipient_encrypt(plaintext: ByteArray, plaintextLen: Long, recipientKeys: Long, numRecipients: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_multi_recipient_decrypt(ciphertext: ByteArray, ciphertextLen: Long, privateKey: ByteArray, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_delete_message_key(chainKey: ByteArray, chainKeyLen: Long, messageNumber: Int): Int
    external fun enchant_clear_consumed_keys(skippedKeys: ByteArray, skippedKeysLen: Long, maxKeys: Int): Int
    external fun enchant_profile_encrypt(profileKey: ByteArray, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_profile_decrypt(profileKey: ByteArray, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_svr_create_backup(pin: ByteArray, pinLen: Long, masterKey: ByteArray, backupOut: ByteArray, backupLen: Long): Int
    external fun enchant_svr_restore_backup(pin: ByteArray, pinLen: Long, backup: ByteArray, backupLen: Long, masterKeyOut: ByteArray): Int
    external fun enchant_svr_change_pin(oldPin: ByteArray, oldPinLen: Long, newPin: ByteArray, newPinLen: Long, backup: ByteArray, backupLen: Long, newBackupOut: ByteArray, newBackupLen: Long): Int
    external fun enchant_svr_generate_auth_proof(serverPrivateKey: ByteArray, clientId: ByteArray, clientIdLen: Long, proofOut: ByteArray, nonceOut: ByteArray, timestampOut: Long): Int
    external fun enchant_svr_verify_auth_proof(serverPrivateKey: ByteArray, clientId: ByteArray, clientIdLen: Long, proof: ByteArray, nonce: ByteArray, timestamp: Long): Int
    external fun enchant_account_entropy_create(entropyOut: ByteArray): Int
    external fun enchant_account_entropy_from_passphrase(passphrase: String, passphraseLen: Long, entropyOut: ByteArray): Int
    external fun enchant_account_key_derive(entropy: ByteArray, accountKeyOut: ByteArray): Int
    external fun enchant_backup_key_derive(accountKey: ByteArray, backupKeyOut: ByteArray): Int
    external fun enchant_media_key_derive(accountKey: ByteArray, mediaId: String, mediaKeyOut: ByteArray): Int
    external fun enchant_auth_credential_present(credentialData: ByteArray, credentialLen: Long, serverParams: ByteArray, presentationOut: ByteArray, presentationLen: Long): Int
    external fun enchant_profile_key_credential_present(credentialData: ByteArray, credentialLen: Long, serverParams: ByteArray, presentationOut: ByteArray, presentationLen: Long): Int
    external fun enchant_group_credential_present(credentialData: ByteArray, credentialLen: Long, serverParams: ByteArray, presentationOut: ByteArray, presentationLen: Long): Int
    external fun enchant_key_transparency_generate_keypair(publicKey: ByteArray, privateKey: ByteArray): Int
    external fun enchant_key_transparency_prove(privateKey: ByteArray, commitment: ByteArray, proofOut: ByteArray, proofLen: Long): Int
    external fun enchant_key_transparency_verify(publicKey: ByteArray, commitment: ByteArray, proof: ByteArray, proofLen: Long, validOut: Long): Int
    external fun enchant_key_transparency_vrf_prove(privateKey: ByteArray, message: ByteArray, messageLen: Long, proofOut: ByteArray): Int
    external fun enchant_key_transparency_vrf_verify(publicKey: ByteArray, message: ByteArray, messageLen: Long, proof: ByteArray, validOut: Long): Int
    external fun enchant_key_transparency_vrf_proof_to_hash(proof: ByteArray, hashOut: ByteArray): Int
    external fun enchant_key_transparency_verify_search(vrfPublicKey: ByteArray, userId: ByteArray, userIdLen: Long, expectedValue: ByteArray, expectedValueLen: Long, vrfProof: ByteArray, vrfProofLen: Long, validOut: Long): Int
    external fun enchant_streaming_aead_encrypt_init(handleOut: Long, key: ByteArray, nonce: ByteArray, aad: ByteArray, aadLen: Long): Int
    external fun enchant_streaming_aead_encrypt_update(handle: Long, plaintext: ByteArray, plaintextLen: Long, ciphertext: ByteArray, ciphertextLen: Long): Int
    external fun enchant_streaming_aead_encrypt_finalize(handle: Long, tagOut: ByteArray): Int
    external fun enchant_streaming_aead_encrypt_free(handle: Long)
    external fun enchant_streaming_aead_decrypt_init(handleOut: Long, key: ByteArray, nonce: ByteArray, aad: ByteArray, aadLen: Long): Int
    external fun enchant_streaming_aead_decrypt_update(handle: Long, ciphertext: ByteArray, ciphertextLen: Long, plaintext: ByteArray, plaintextLen: Long): Int
    external fun enchant_streaming_aead_decrypt_finalize(handle: Long, tag: ByteArray): Int
    external fun enchant_streaming_aead_decrypt_free(handle: Long)
    external fun enchant_media_sanitize_mp4(data: ByteArray, dataLen: Long, output: ByteArray, outputLen: Long): Int
    external fun enchant_media_sanitize_webp(data: ByteArray, dataLen: Long, output: ByteArray, outputLen: Long): Int
}
