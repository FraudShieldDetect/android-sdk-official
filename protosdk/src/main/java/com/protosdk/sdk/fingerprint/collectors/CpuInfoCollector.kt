package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.nativebridge.CpuDetectionBridge
import org.json.JSONObject

class CpuInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val topology = JSONObject(CpuDetectionBridge.nativeGetCpuTopology())
    val frequency = JSONObject(CpuDetectionBridge.nativeGetCpuFreqInfo())
    // val idle = JSONObject(CpuDetectionBridge.nativeGetCpuIdleStates())
    // val platform = JSONObject(CpuDetectionBridge.nativeGetPlatformId())
    val procinfo = JSONObject(CpuDetectionBridge.nativeGetProcInfo())

    JSONObject().apply {
      put("topology", topology)
      put("frequency", frequency)
      // put("idleStates", idle)
      // put("platform", platform)
      put("procinfo", procinfo)
    }
  }

  override fun getCollectorName(): String = "CpuInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
