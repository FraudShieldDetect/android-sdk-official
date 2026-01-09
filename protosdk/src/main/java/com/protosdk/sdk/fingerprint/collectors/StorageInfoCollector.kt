package com.protosdk.sdk.fingerprint.collectors

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONObject
import java.io.File

class StorageInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val result = JSONObject()

    val directory: File? = context.filesDir ?: context.cacheDir
    if (directory != null) {
      result.collectDataPoint("storageTotalBytes") {
        val statFs = StatFs(directory.absolutePath)
        val blockSize = statFs.blockSizeLong
        statFs.blockCountLong * blockSize
      }
    } else {
      result.collectDataPoint("storageError") { "directoryUnavailable" }
    }

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (activityManager != null) {
      val memInfo = ActivityManager.MemoryInfo()
      activityManager.getMemoryInfo(memInfo)
      result.collectDataPoint("ramTotalBytes") { memInfo.totalMem }
      result.collectDataPoint("ramThresholdBytes") { memInfo.threshold }
    } else {
      result.collectDataPoint("ramError") { "activityManagerUnavailable" }
    }

    result
  }

  override fun getCollectorName(): String = "StorageInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
