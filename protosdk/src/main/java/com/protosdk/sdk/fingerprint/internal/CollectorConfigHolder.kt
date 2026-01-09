package com.protosdk.sdk.fingerprint.internal

import com.protosdk.sdk.ProtoSDKConfig

/**
 * Holds the SDK configuration for access by collectors.
 * Updated when ProtoSDK.applyConfig() is called.
 */
internal object CollectorConfigHolder {
  @Volatile var config: ProtoSDKConfig = ProtoSDKConfig()
}
