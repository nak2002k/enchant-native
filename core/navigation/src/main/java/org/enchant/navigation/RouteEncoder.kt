package org.enchant.navigation

internal fun encodeRouteParam(value: String): String {
    val sb = StringBuilder(value.length)
    for (c in value) {
        when {
            c.isLetterOrDigit() -> sb.append(c)
            c in "-._~" -> sb.append(c)
            else -> {
                sb.append('%')
                sb.append(c.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
    return sb.toString()
}
