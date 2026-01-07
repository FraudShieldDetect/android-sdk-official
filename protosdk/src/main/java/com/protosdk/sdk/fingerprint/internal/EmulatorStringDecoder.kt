package com.protosdk.sdk.fingerprint.internal

import com.protosdk.sdk.BuildConfig

internal object EmulatorStringDecoder {
  private const val TYPE_PATH = "PATH"
  private const val TYPE_PROPERTY = "PROPERTY"
  private const val TYPE_DEVICE = "DEVICE"
  private const val TYPE_MAC = "MAC_PREFIX"
  private const val TYPE_IP = "IP_RANGE"
  private const val TYPE_TOKEN = "PROC_TOKEN"

  private fun decode(type: String): List<String> {
    val key = BuildConfig.EMULATOR_STRING_KEY
    if (key.isBlank()) return emptyList()
    return EmulatorStringTable.entries()
      .asSequence()
      .filter { it.type == type }
      .mapNotNull { EmulatorStringTable.decode(it, key) }
      .filter { it.isNotBlank() }
      .toList()
  }

  fun paths(): List<String> = decode(TYPE_PATH)

  fun properties(): List<String> = decode(TYPE_PROPERTY)

  fun devices(): List<String> = decode(TYPE_DEVICE)

  fun macPrefixes(): List<String> = decode(TYPE_MAC)

  fun ipRanges(): List<String> = decode(TYPE_IP)

  fun procTokens(): List<String> = decode(TYPE_TOKEN)
}
