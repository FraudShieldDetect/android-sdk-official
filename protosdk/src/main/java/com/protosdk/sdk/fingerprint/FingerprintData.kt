package com.protosdk.sdk.fingerprint

import org.json.JSONObject

data class FingerprintData(
        var buildInfo: JSONObject = JSONObject(),
        var timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply { put("build", buildInfo) }
    }

    fun toJsonWithTimestamp(): JSONObject {
        return JSONObject().apply {
            put("build", buildInfo)
            put("timestamp", timestamp)
        }
    }
}

data class FingerprintResult(
        val data: FingerprintData,
        val fingerprint: String,
        val success: Boolean,
        val error: String? = null,
        val collectionTimeMs: Long = 0,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("data", data.toJson())
            put("fingerprint", fingerprint)
            put("success", success)
            put("error", error)
            put("collectionTimeMs", collectionTimeMs)
        }
    }
}
