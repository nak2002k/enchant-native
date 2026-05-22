package org.enchant.auth

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthNavKey serialization")
class AuthNavKeyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Nested
    @DisplayName("Init flow routes")
    inner class InitRoutes {

        @Test
        fun `Welcome serialization round-trip`() {
            val original = AuthNavKey.Welcome
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `Permissions serialization round-trip`() {
            val original = AuthNavKey.Permissions(nextRoute = AuthNavKey.PhoneEntry)
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `Permissions with nested nextRoute`() {
            val original = AuthNavKey.Permissions(nextRoute = AuthNavKey.KeyGeneration)
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `PhoneEntry serialization round-trip`() {
            val original = AuthNavKey.PhoneEntry
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `CountryCodePicker serialization round-trip`() {
            val original = AuthNavKey.CountryCodePicker
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `OtpVerify serialization round-trip`() {
            val original = AuthNavKey.OtpVerify(identifier = "+15551234567")
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `OtpVerify with empty identifier`() {
            val original = AuthNavKey.OtpVerify(identifier = "")
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `KeyGeneration serialization round-trip`() {
            val original = AuthNavKey.KeyGeneration
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("Post-init routes")
    inner class PostInitRoutes {

        @Test
        fun `TwoStepPin serialization round-trip`() {
            val original = AuthNavKey.TwoStepPin
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `ProfileSetup serialization round-trip`() {
            val original = AuthNavKey.ProfileSetup
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `UsernamePicker serialization round-trip`() {
            val original = AuthNavKey.UsernamePicker
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `RestorePrompt serialization round-trip with default`() {
            val original = AuthNavKey.RestorePrompt()
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `RestorePrompt serialization round-trip with hasBackup true`() {
            val original = AuthNavKey.RestorePrompt(hasBackup = true)
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `AppLock serialization round-trip`() {
            val original = AuthNavKey.AppLock
            val serialized = json.encodeToString(AuthNavKey.serializer(), original)
            val deserialized = json.decodeFromString(AuthNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("Serialized form")
    inner class SerializedForm {

        @Test
        fun `Welcome serializes to expected type`() {
            val serialized = json.encodeToString(AuthNavKey.serializer(), AuthNavKey.Welcome)
            val deserialized = json.decodeFromString<AuthNavKey>(serialized)
            assertEquals(AuthNavKey.Welcome, deserialized)
        }

        @Test
        fun `OtpVerify contains identifier in serialized form`() {
            val serialized = json.encodeToString(AuthNavKey.serializer(), AuthNavKey.OtpVerify(identifier = "test"))
            val deserialized = json.decodeFromString<AuthNavKey>(serialized)
            assertEquals(AuthNavKey.OtpVerify("test"), deserialized)
        }

        @Test
        fun `Permissions contains nextRoute in serialized form`() {
            val serialized = json.encodeToString(AuthNavKey.serializer(), AuthNavKey.Permissions(AuthNavKey.PhoneEntry))
            val deserialized = json.decodeFromString<AuthNavKey>(serialized)
            assertEquals(AuthNavKey.Permissions(AuthNavKey.PhoneEntry), deserialized)
        }
    }
}
