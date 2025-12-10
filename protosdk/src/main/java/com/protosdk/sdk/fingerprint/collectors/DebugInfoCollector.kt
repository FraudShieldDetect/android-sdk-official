package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.os.Debug
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.utils.SecurityCollectorUtils
import org.json.JSONObject

class DebugInfoCollector : BaseCollector() {

  override suspend fun collect(context: Context): JSONObject = safeCollect {
    JSONObject().apply {
      collectDataPoint("adbEnabled") {
        SecurityCollectorUtils.readGlobalSetting(
          context,
          android.provider.Settings.Global.ADB_ENABLED,
        )
      }

      collectDataPoint("isDebuggerConnected") { Debug.isDebuggerConnected() }

      // val memoryInfo = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
      // put(
      //         "debugMemoryInfo",
      //         JSONObject().apply {
      //           put("totalPrivateDirty", memoryInfo.totalPrivateDirty)
      //           put("totalPrivateClean", memoryInfo.totalPrivateClean)
      //           put("totalSharedDirty", memoryInfo.totalSharedDirty)
      //           put("totalSharedClean", memoryInfo.totalSharedClean)
      //           put("totalPss", memoryInfo.totalPss)
      //           put("totalSwappablePss", memoryInfo.totalSwappablePss)
      //         },
      // )
      // put("nativeHeapAllocatedSize", Debug.getNativeHeapAllocatedSize())
      // put("nativeHeapFreeSize", Debug.getNativeHeapFreeSize())
      // put("nativeHeapSize", Debug.getNativeHeapSize())
    }
  }

  override fun getCollectorName(): String = "DebugInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()
}
