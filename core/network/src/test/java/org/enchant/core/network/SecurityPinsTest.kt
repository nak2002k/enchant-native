package org.enchant.core.network

import okhttp3.CertificatePinner
import okhttp3.tls.HeldCertificate
import okio.ByteString.Companion.toByteString
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLPeerUnverifiedException

@DisplayName("SecurityPins — certificate pinning (F-C2)")
class SecurityPinsTest {

    private fun heldCertificate(): HeldCertificate {
        return HeldCertificate.Builder()
            .commonName("api.example.com")
            .addSubjectAlternativeName("api.example.com")
            .build()
    }

    private fun spkiPin(cert: HeldCertificate): String {
        return "sha256/" + cert.certificate.publicKey.encoded.toByteString().sha256().base64()
    }

    @Test
    @DisplayName("no pins installed -> pinner is a no-op (trusts system CAs)")
    fun `no pins is no-op`() {
        SecurityPins.installPins("api.example.com", emptyList())
        val cert = heldCertificate()
        // Empty pinner never throws.
        SecurityPins.active().check("api.example.com", listOf(cert.certificate))
    }

    @Test
    @DisplayName("matching pin is accepted")
    fun `matching pin accepted`() {
        val cert = heldCertificate()
        SecurityPins.installPins("api.example.com", listOf(spkiPin(cert)))
        SecurityPins.active().check("api.example.com", listOf(cert.certificate))
    }

    @Test
    @DisplayName("non-matching pin is rejected")
    fun `non-matching pin rejected`() {
        val cert = heldCertificate()
        val wrongPin = "sha256/" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        SecurityPins.installPins("api.example.com", listOf(wrongPin))
        assertThrows(SSLPeerUnverifiedException::class.java) {
            SecurityPins.active().check("api.example.com", listOf(cert.certificate))
        }
    }

    @Test
    @DisplayName("updatePins installs into ApiClient's shared pinner")
    fun `updatePins installs`() {
        val cert = heldCertificate()
        ApiClient.updatePins("api.example.com", listOf(spkiPin(cert)))
        ApiClient.getPinner().check("api.example.com", listOf(cert.certificate))

        val other = heldCertificate()
        assertThrows(SSLPeerUnverifiedException::class.java) {
            ApiClient.getPinner().check("api.example.com", listOf(other.certificate))
        }
    }
}
