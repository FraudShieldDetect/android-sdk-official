package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.nativebridge.CpuDetectionBridge
import org.json.JSONObject

class CpuInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    JSONObject().apply {
      collectDataPoint("topology") { JSONObject(CpuDetectionBridge.nativeGetCpuTopology()) }
      collectDataPoint("frequency") { JSONObject(CpuDetectionBridge.nativeGetCpuFreqInfo()) }
      // collectDataPoint("idleStates") { JSONObject(CpuDetectionBridge.nativeGetCpuIdleStates()) }
      // collectDataPoint("platform") { JSONObject(CpuDetectionBridge.nativeGetPlatformId()) }
      collectDataPoint("procinfo") { JSONObject(CpuDetectionBridge.nativeGetProcInfo()) }
    }
  }

  override fun getCollectorName(): String = "CpuInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
