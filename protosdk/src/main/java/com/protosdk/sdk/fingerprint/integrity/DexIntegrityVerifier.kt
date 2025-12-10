package com.protosdk.sdk.fingerprint.integrity

import android.content.Context
import com.protosdk.sdk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Validates that the runtime DEX payload matches the hash baked into BuildConfig at build time.
 */
object DexIntegrityVerifier {
  @Volatile private var cachedResult: Boolean? = null

  suspend fun verify(context: Context): Boolean {
    cachedResult?.let { return it }
    val expected = loadExpectedHash(context)
    if (expected.isBlank() || expected == "UNSET") {
      cachedResult = false
      return false
    }

    val actual = withContext(Dispatchers.IO) { computeDexHash(context) }
    val matches = expected.equals(actual, ignoreCase = true)
    cachedResult = matches
    return matches
  }

  private fun loadExpectedHash(context: Context): String {
    // Prefer an app-provided hash (generated from merged dex) if present in assets,
    // otherwise fall back to the library BuildConfig value.
    runCatching {
      context.assets.open("dex_integrity_hash.txt").bufferedReader().use { reader ->
        val line = reader.readLine()
        if (!line.isNullOrBlank()) return line.trim()
      }
    }
    val resId = context.resources.getIdentifier("dex_integrity_hash", "string", context.packageName)
    if (resId != 0) {
      return runCatching { context.getString(resId) }.getOrDefault("")
    }
    return BuildConfig.DEX_INTEGRITY_HASH
  }

  private fun computeDexHash(context: Context): String {
    val apkFile = context.applicationInfo.sourceDir ?: return ""
    val perFileHashes = mutableListOf<String>()
    ZipFile(apkFile).use { zip ->
      zip.entries().asSequence()
        .filter { it.name.endsWith(".dex", ignoreCase = true) }
        .forEach { entry ->
          val perFileDigest = MessageDigest.getInstance("SHA-256")
          zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
              val count = input.read(buffer)
              if (count <= 0) break
              perFileDigest.update(buffer, 0, count)
            }
          }
          perFileHashes += perFileDigest.digest().joinToString("") { "%02x".format(it) }
        }
    }
    if (perFileHashes.isEmpty()) return ""
    val combinedDigest = MessageDigest.getInstance("SHA-256")
    perFileHashes.sorted().forEach { hex ->
      combinedDigest.update(hex.toByteArray())
    }
    return combinedDigest.digest().joinToString("") { "%02x".format(it) }
  }
}
