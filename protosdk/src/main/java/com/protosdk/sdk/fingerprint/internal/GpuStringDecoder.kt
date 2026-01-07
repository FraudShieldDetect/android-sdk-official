package com.protosdk.sdk.fingerprint.internal

import com.protosdk.sdk.BuildConfig
import java.security.MessageDigest
import java.util.Locale

internal object GpuStringDecoder {
  data class Pattern(val value: String, val hash: String)

  private fun decode(type: String): List<Pattern> {
    val key = BuildConfig.GPU_STRING_KEY
    if (key.isBlank()) return emptyList()
    return GpuStringTable.entries()
      .asSequence()
      .filter { it.type == type }
      .mapNotNull { entry ->
        val decoded = GpuStringTable.decode(entry, key)
        if (decoded != null) Pattern(decoded, entry.hash) else null
      }
      .filter { it.value.isNotBlank() }
      .toList()
  }

  fun rendererPatterns(): List<Pattern> = decode("RENDERER")

  fun vendorPatterns(): List<Pattern> = decode("VENDOR")

  fun extensionPatterns(): List<Pattern> = decode("EXTENSION")

  fun eglVendorPatterns(): List<Pattern> = decode("EGL_VENDOR")

  fun normalizedHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val normalized = value.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
    digest.update(normalized)
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun matchPattern(value: String, patterns: List<Pattern>): Pattern? {
    if (value.isBlank()) return null
    val normalized = normalizedHash(value)
    return patterns.firstOrNull { it.hash == normalized } ?: patterns.firstOrNull {
      value.contains(it.value, ignoreCase = true)
    }
  }
}
