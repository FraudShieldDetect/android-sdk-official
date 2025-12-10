package com.protosdk.sdk.fingerprint.nativebridge

/**
 * Native bridge that exposes syscall-level helpers for the hardened emulator detection collector.
 */
object EmulatorDetectionBridge {
  init {
    System.loadLibrary("emulator_checker")
  }

  external fun nativeStat(path: String): Boolean

  external fun nativeGetProperty(key: String): String

  external fun nativeReadCpuInfo(): String

  external fun nativeReadProc(path: String): String

  external fun nativeGetNetworkIfaces(): Array<String>

  external fun nativeCheckSensors(windowMs: Int): IntArray

  external fun nativeTracerPid(): Int

  external fun nativeNeonProbe(): Boolean

  external fun nativeDecodeString(payload: IntArray, key: String): String
}
