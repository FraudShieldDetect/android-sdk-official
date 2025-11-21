package com.protosdk.sdk.fingerprint.collectors

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import java.io.File
import org.json.JSONObject

class StorageInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val result = JSONObject()

    val directory: File? = context.filesDir ?: context.cacheDir
    if (directory != null) {
      val statFs = StatFs(directory.absolutePath)
      val blockSize = statFs.blockSizeLong
      val totalBytes = statFs.blockCountLong * blockSize

      result.apply { put("storageTotalBytes", totalBytes) }
    } else {
      result.put("storageError", "directoryUnavailable")
    }

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (activityManager != null) {
      val memInfo = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memInfo)

      result.apply {
        put("ramTotalBytes", memInfo.totalMem)
        put("ramThresholdBytes", memInfo.threshold)
      }
    } else {
      result.put("ramError", "activityManagerUnavailable")
    }

    result
  }

  override fun getCollectorName(): String = "StorageInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
