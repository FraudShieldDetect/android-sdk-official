package com.protosdk.sdk.fingerprint.nativebridge

/**
 * Native bridge exposing GPU fingerprinting helpers.
 */
object GpuDetectionBridge {
  init {
    System.loadLibrary("gpu_checker")
  }

  external fun nativeGetGpuRenderer(): String

  external fun nativeGetGpuVendor(): String

  external fun nativeGetGpuVersion(): String

  external fun nativeGetGpuExtensions(): Array<String>

  external fun nativeGetEglVendor(): String

  external fun nativeGetEglConfig(): IntArray

  external fun nativeGetGpuMemoryInfo(): IntArray

  external fun nativeGetMaxTextureSize(): Int

  external fun nativeGetComputeWorkGroupInvocations(): Int

  external fun nativeRunMicroBenchmark(): Double

  external fun nativeCheckVulkan(): Boolean

  external fun nativeDecodeString(payload: IntArray, key: String): String
}
