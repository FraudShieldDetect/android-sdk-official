package com.protosdk.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protosdk.demo.ui.theme.*
import com.protosdk.sdk.ProtoSDK
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val protoSDK = ProtoSDK.initialize(this)

        setContent {
            ProtoSDKDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FingerprintScreen(protoSDK)
                }
            }
        }
    }
}

//////////////////////////////////////////////////////////////
// MAIN SCREEN (SINGLE PAGE)
//////////////////////////////////////////////////////////////

@Composable
fun FingerprintScreen(protoSDK: ProtoSDK) {

    var sections by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Auto-collect on startup
    LaunchedEffect(Unit) {
        collectFingerprint(protoSDK) { resultSections, loading ->
            sections = resultSections
            isLoading = loading
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        //---------------------------------------------------------
        // APP NAME
        //---------------------------------------------------------
        Text(
            text = "ProtoSDK",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = OffWhite
            )
        )

        //---------------------------------------------------------
        // SCREEN TITLE + COLLECT AGAIN BUTTON
        //---------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device Fingerprint",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = OffWhite
                )
            )

            Button(
                onClick = {
                    collectFingerprint(protoSDK) { resultSections, loading ->
                        sections = resultSections
                        isLoading = loading
                    }
                }
            ) {
                Text(if (isLoading) "Collecting..." else "Collect Again")
            }
        }

        //---------------------------------------------------------
        // SKELETONS WHILE LOADING
        //---------------------------------------------------------
        if (isLoading) {
            repeat(4) {
                SkeletonCard()
            }
        } else {
            //---------------------------------------------------------
            // REAL SECTION CARDS
            //---------------------------------------------------------
            sections.forEach { (title, content) ->
                SectionCard(title = title, data = content)
            }
        }
    }
}

//////////////////////////////////////////////////////////////
// SECTION CARD
//////////////////////////////////////////////////////////////

@Composable
fun SectionCard(title: String, data: Map<String, Any?>) {

    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            // Title + Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // SECTION TITLE (same color as item keys: #171417)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ItemKeyColor
                    )
                )

                TextButton(
                    onClick = {
                        val txt = buildString {
                            appendLine(title)
                            data.toSortedMap().forEach { (k, v) ->
                                appendLine("$k: ${v?.toString() ?: "null"}")
                            }
                        }.trimEnd()

                        clipboard.setText(AnnotatedString(txt))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ItemKeyColor)
                ) {
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ItemKeyColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content items
            data.toSortedMap().forEach { (key, value) ->

                val line = buildAnnotatedString {

                    append("• ")

                    // Key (bold)
                    withStyle(
                        SpanStyle(
                            color = ItemKeyColor,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("$key: ")
                    }

                    // Value (normal)
                    withStyle(
                        SpanStyle(
                            color = ItemValueColor,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(value?.toString() ?: "null")
                    }
                }

                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

//////////////////////////////////////////////////////////////
// SKELETON LOADER CARD
//////////////////////////////////////////////////////////////

@Composable
fun SkeletonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(18.dp)
                    .shimmer()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 item skeletons
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .padding(vertical = 4.dp)
                        .shimmer()
                )
            }
        }
    }
}

//////////////////////////////////////////////////////////////
// SHIMMER EFFECT
//////////////////////////////////////////////////////////////

@Composable
fun Modifier.shimmer(): Modifier {
    val anim = rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    return this.alpha(anim.value)
}

//////////////////////////////////////////////////////////////
// JSON STRING → MAP
//////////////////////////////////////////////////////////////

fun jsonStringToMap(json: String): Map<String, Any?> {
    val obj = JSONObject(json)
    val map = mutableMapOf<String, Any?>()

    obj.keys().forEach { key ->
        map[key] = obj.get(key)
    }

    return map
}

//////////////////////////////////////////////////////////////
// FINGERPRINT COLLECTION (WITH HASH CARD)
//////////////////////////////////////////////////////////////

private fun collectFingerprint(
    protoSDK: ProtoSDK,
    callback: (Map<String, Map<String, Any?>>, Boolean) -> Unit
) {
    callback(emptyMap(), true)

    CoroutineScope(Dispatchers.Default).launch {
        try {
            val result = protoSDK.collectFingerprint()

            val output =
                if (result.success) {
                    mapOf(
                        // NEW fingerprint hash card
                        "Fingerprint Hash" to mapOf(
                            "hash" to result.fingerprint,
                            "collection_time_ms" to result.collectionTimeMs
                        ),

                        "Build Information" to jsonStringToMap(result.data.buildInfo.toString(2)),
                        "Device Information" to jsonStringToMap(result.data.deviceInfo.toString(2)),
                        "Display Information" to jsonStringToMap(result.data.displayInfo.toString(2)),
                        "Debug Information" to jsonStringToMap(result.data.debugInfo.toString(2)),
                        "Root Detection" to jsonStringToMap(result.data.rootInfo.toString(2)),
                        "Emulator Detection" to jsonStringToMap(result.data.emulatorInfo.toString(2)),
                        "GPU Information" to jsonStringToMap(result.data.gpuInfo.toString(2)),
                        "CPU Information" to jsonStringToMap(result.data.cpuInfo.toString(2)),
                        "Storage Information" to jsonStringToMap(result.data.storageInfo.toString(2)),
                        "Sensor Information" to jsonStringToMap(result.data.sensorInfo.toString(2)),
                        "Network Information" to jsonStringToMap(result.data.networkInfo.toString(2)),
                        "Google Services Framework ID" to jsonStringToMap(result.data.gsfInfo.toString(2)),
                        "MediaDrm ID (Hashed)" to jsonStringToMap(result.data.mediaDrmInfo.toString(2))
                    )
                } else {
                    mapOf(
                        "Error" to mapOf(
                            "message" to (result.error ?: "Unknown error"),
                            "time_ms" to result.collectionTimeMs
                        )
                    )
                }

            withContext(Dispatchers.Main) { callback(output, false) }

        } catch (e: Exception) {
            val err = mapOf("Error" to mapOf("message" to (e.message ?: "Unknown error")))
            withContext(Dispatchers.Main) { callback(err, false) }
        }
    }
}
