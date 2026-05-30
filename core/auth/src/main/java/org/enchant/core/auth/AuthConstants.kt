package org.enchant.core.auth

object AuthConstants {
    const val JWT_KEY = "auth.jwt"
    const val REFRESH_TOKEN_KEY = "auth.refresh_token"
    const val USER_ID_KEY = "auth.user_id"
    const val DEVICE_ID_KEY = "auth.device_id"
    const val LAST_OTP_REQUEST_KEY = "auth.last_otp_request"
    const val OTP_COOLDOWN_MS = 30_000L
    const val MAX_REFRESH_RETRIES = 1

    const val PATH_REQUEST_OTP = "/v1/auth/request-otp"
    const val PATH_VERIFY_OTP = "/v1/auth/verify-otp"
    const val PATH_REFRESH = "/v1/auth/refresh"
    const val PATH_LOGOUT = "/v1/auth/logout"
    const val PATH_DEVICES = "/v1/auth/devices"
    const val PATH_ACCOUNT = "/v1/auth/account"
    const val PATH_JWKS = "/v1/auth/.well-known/jwks.json"
    const val PATH_KEYS_REGISTER = "/v1/keys/register"
    const val PATH_KEYS_SPK = "/v1/keys/signed-prekey"
    const val PATH_KEYS_OPK = "/v1/keys/one-time-prekeys"
    const val PATH_KEYS_OPK_COUNT = "/v1/keys/opk-count"
    const val PATH_RESTORE_BACKUP = "/v1/backup/restore"
    const val PATH_WHOAMI = "/v1/accounts/whoami"
}
