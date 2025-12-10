package com.protosdk.sdk.fingerprint.nativebridge

/** Native bridge exposing CPU topology/frequency/idle/platform/thermal helpers. */
object CpuDetectionBridge {
  init {
    System.loadLibrary("cpu_checker")
  }

  external fun nativeGetCpuTopology(): String
  external fun nativeGetCpuFreqInfo(): String

  // external fun nativeGetCpuIdleStates(): String
  // external fun nativeGetPlatformId(): String
  external fun nativeGetProcInfo(): String
}
