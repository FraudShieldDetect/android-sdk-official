package com.protosdk.sdk.fingerprint

import org.json.JSONObject

data class FingerprintData(
        var buildInfo: JSONObject = JSONObject(),
        var timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject {
        val json = JSONObject().apply { put("build", buildInfo) }
        
        // Check JSON size (in bytes)
        val jsonString = json.toString()
        val sizeInBytes = jsonString.toByteArray(Charsets.UTF_8).size
        val maxSizeBytes = 1024 * 1024 // 1MB max
        
        if (sizeInBytes > maxSizeBytes) {
            throw IllegalStateException("Fingerprint JSON too large: ${sizeInBytes} bytes (max: ${maxSizeBytes} bytes)")
        }
        
        return json
    }

    fun toJsonWithTimestamp(): JSONObject {
        val json = JSONObject().apply {
            put("build", buildInfo)
            put("timestamp", timestamp)
        }
        
        // Check JSON size (in bytes)
        val jsonString = json.toString()
        val sizeInBytes = jsonString.toByteArray(Charsets.UTF_8).size
        val maxSizeBytes = 1024 * 1024 // 1MB max
        
        if (sizeInBytes > maxSizeBytes) {
            throw IllegalStateException("Fingerprint JSON too large: ${sizeInBytes} bytes (max: ${maxSizeBytes} bytes)")
        }
        
        return json
    }
    
    fun getJsonSize(): Int {
        return toJson().toString().toByteArray(Charsets.UTF_8).size
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
