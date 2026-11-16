package com.kimicode.pocket

import java.net.URI

object PairingValidator {
    private const val MAX_URL_LENGTH = 8192

    fun parse(raw: String?): URI? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URL_LENGTH } ?: return null
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return uri
    }

    fun normalize(raw: String?): String? = parse(raw)?.toASCIIString()

    fun displayHost(raw: String?): String? = parse(raw)?.host
}
