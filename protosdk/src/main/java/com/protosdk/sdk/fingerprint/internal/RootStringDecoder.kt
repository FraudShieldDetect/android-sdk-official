package com.protosdk.sdk.fingerprint.internal

import com.protosdk.sdk.BuildConfig

internal object RootStringDecoder {
  private const val TYPE_PATH = "PATH"
  private const val TYPE_PROPERTY = "PROPERTY"
  private const val TYPE_PACKAGE = "PACKAGE"
  private const val TYPE_ENV = "ENV"

  private fun decode(type: String): List<String> {
    val key = BuildConfig.ROOT_STRING_KEY
    if (key.isBlank()) return emptyList()
    return RootStringTable.decode(type, key)
  }

  fun paths(): List<String> = decode(TYPE_PATH)

  fun properties(): List<String> = decode(TYPE_PROPERTY)

  fun packages(): List<String> = decode(TYPE_PACKAGE)

  fun env(): List<String> = decode(TYPE_ENV)
}
