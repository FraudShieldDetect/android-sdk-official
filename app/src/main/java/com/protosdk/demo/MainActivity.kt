package com.protosdk.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protosdk.demo.ui.theme.ProtoSDKDemoTheme
import com.protosdk.sdk.ProtoSDK
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val protoSDK = ProtoSDK.initialize(this)

    setContent {
      ProtoSDKDemoTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) { SimpleDemoScreen(protoSDK) }
      }
    }
  }
}

@Composable
fun SimpleDemoScreen(protoSDK: ProtoSDK) {
  var resultText by remember { mutableStateOf("Click button to collect fingerprint") }
  var isLoading by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text = "ProtoSDK Demo", style = MaterialTheme.typography.headlineMedium)

    Button(
      onClick = {
        isLoading = true
        coroutineScope.launch {
          try {
            val result = protoSDK.collectFingerprint()
            resultText =
              if (result.success) {
                buildString {
                  appendLine("Collection Successful!")
                  appendLine()
                  appendLine("Fingerprint Hash: ${result.fingerprint}")
                  appendLine(
                    "Collection Time: ${result.collectionTimeMs}ms",
                  )
                  appendLine("JSON Size: ${result.data.getJsonSize()} bytes")
                  appendLine()
                  appendLine("=== Build Information ===")
                  appendLine(result.data.buildInfo.toString(2))
                  appendLine()
                  appendLine("=== Device Information ===")
                  appendLine(result.data.deviceInfo.toString(2))
                  appendLine()
                  appendLine("=== Display Information ===")
                  appendLine(result.data.displayInfo.toString(2))
                  appendLine()
                  appendLine("=== Debug Information ===")
                  appendLine(result.data.debugInfo.toString(2))
                  appendLine()
                  appendLine("=== Root Detection ===")
                  appendLine(result.data.rootInfo.toString(2))
                  appendLine()
                  appendLine("=== Emulator Detection ===")
                  appendLine(result.data.emulatorInfo.toString(2))
                  appendLine()
                  appendLine("=== GPU Fingerprinting ===")
                  appendLine(result.data.gpuInfo.toString(2))
                }
              } else {
                "Collection Failed\nError: ${result.error}\nTime: ${result.collectionTimeMs}ms"
              }
          } catch (e: Exception) {
            resultText = "Error: ${e.message}"
          } finally {
            isLoading = false
          }
        }
      },
      enabled = !isLoading,
    ) {
      if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
      }
      Text(if (isLoading) "Collecting..." else "Collect Fingerprint")
    }

    // Show collectors and their permission status
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Collectors Status:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        protoSDK.getAvailableCollectors().forEach { collectorName ->
          val info = protoSDK.getCollectorInfo(collectorName)
          if (info != null) {
            val hasPermissions = info.requiredPermissions.isEmpty() ||
              info.requiredPermissions.all { permission ->
                protoSDK.hasAllPermissions()
              }

            Text(
              text =
              "• $collectorName: ${if (hasPermissions) "true" else "false"}",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Box(modifier = Modifier.fillMaxSize()) {
        Text(
          text = resultText,
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
