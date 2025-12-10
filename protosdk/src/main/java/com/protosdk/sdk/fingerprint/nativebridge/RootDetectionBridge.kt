package com.protosdk.sdk.fingerprint.nativebridge

/**
 * Native bridge that exposes syscall-level helpers for the hardened root detection collector.
 */
object RootDetectionBridge {
  init {
    System.loadLibrary("root_checker")
  }

  external fun nativeStat(path: String): Boolean

  external fun nativeGetProperty(key: String): String

  external fun nativeTracerPid(): Int
}
