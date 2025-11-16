package com.protosdk.sdk.fingerprint.integrity

import android.content.Context
import com.protosdk.sdk.BuildConfig
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validates that the runtime DEX payload matches the hash baked into BuildConfig at build time.
 */
object DexIntegrityVerifier {
  @Volatile private var cachedResult: Boolean? = null

  suspend fun verify(context: Context): Boolean {
    cachedResult?.let { return it }
    val expected = BuildConfig.DEX_INTEGRITY_HASH
    if (expected.isBlank() || expected == "UNSET") {
      cachedResult = false
      return false
    }

    val actual = withContext(Dispatchers.IO) { computeDexHash(context) }
    val matches = expected.equals(actual, ignoreCase = true)
    cachedResult = matches
    return matches
  }

  private fun computeDexHash(context: Context): String {
    val apkFile = context.applicationInfo.sourceDir ?: return ""
    val digest = MessageDigest.getInstance("SHA-256")
    ZipFile(apkFile).use { zip ->
      zip.entries().asSequence()
          .filter { it.name.endsWith(".dex", ignoreCase = true) }
          .sortedBy { it.name }
          .forEach { entry ->
            zip.getInputStream(entry).use { input ->
              val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
              while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
              }
            }
          }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}
