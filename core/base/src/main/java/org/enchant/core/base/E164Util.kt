package org.enchant.core.base

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object E164Util {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun formatToE164(phoneNumber: String, defaultRegion: String? = null): String? {
        return try {
            val region = defaultRegion ?: Locale.getDefault().country
            val number = phoneUtil.parse(phoneNumber, region)
            if (phoneUtil.isValidNumber(number)) {
                phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }
    }

    fun isValidE164(phoneNumber: String, defaultRegion: String? = null): Boolean {
        return formatToE164(phoneNumber, defaultRegion) != null
    }

    fun getCountryCode(phoneNumber: String, defaultRegion: String? = null): Int? {
        return try {
            val region = defaultRegion ?: Locale.getDefault().country
            val number = phoneUtil.parse(phoneNumber, region)
            if (phoneUtil.isValidNumber(number)) {
                number.countryCode
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }
    }

    fun getNationalNumber(phoneNumber: String, defaultRegion: String? = null): Long? {
        return try {
            val region = defaultRegion ?: Locale.getDefault().country
            val number = phoneUtil.parse(phoneNumber, region)
            if (phoneUtil.isValidNumber(number)) {
                number.nationalNumber
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }
    }

    fun getRegionCodeForCountryCode(countryCode: Int): String? {
        return phoneUtil.getRegionCodeForCountryCode(countryCode)
    }

    fun getCountryCodeForRegion(regionCode: String): Int {
        return phoneUtil.getCountryCodeForRegion(regionCode)
    }

    fun formatNational(phoneNumber: String, defaultRegion: String? = null): String? {
        return try {
            val region = defaultRegion ?: Locale.getDefault().country
            val number = phoneUtil.parse(phoneNumber, region)
            if (phoneUtil.isValidNumber(number)) {
                phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }
    }

    fun formatInternational(phoneNumber: String, defaultRegion: String? = null): String? {
        return try {
            val region = defaultRegion ?: Locale.getDefault().country
            val number = phoneUtil.parse(phoneNumber, region)
            if (phoneUtil.isValidNumber(number)) {
                phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            } else {
                null
            }
        } catch (_: NumberParseException) {
            null
        }
    }
}
