package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.net.Uri
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONObject

private const val GSF_CONTENT_URI = "content://com.google.android.gsf.gservices"
private const val GSF_ID_KEY = "android_id"

class GsfIdCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val result = JSONObject()
    val contentResolver = context.contentResolver
    val uri = Uri.parse(GSF_CONTENT_URI)

    var rawValue: String? = null
    var gsfIdHex: String? = null

    contentResolver?.query(uri, null, null, arrayOf(GSF_ID_KEY), null)?.use { cursor ->
      if (cursor.moveToFirst() && cursor.columnCount >= 2) {
        rawValue = cursor.getString(1)
        gsfIdHex = rawValue?.toLongOrNull()?.let { java.lang.Long.toHexString(it) } ?: rawValue
      }
    }

    result.put("gsfId", gsfIdHex ?: "")

    result
  }

  override fun getCollectorName(): String = "GsfIdCollector"

  override fun getRequiredPermissions(): List<String> =
          listOf("com.google.android.providers.gsf.permission.READ_GSERVICES")

  override fun isSupported(context: Context): Boolean =
          context.packageManager.resolveContentProvider("com.google.android.gsf.gservices", 0) !=
                  null
}
