package com.protosdk.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Your custom App color scheme
private val AppColorScheme = darkColorScheme(
    primary = PrimaryColor,          // Titles + button text
    onPrimary = TealBackground,      // Button background contrast if needed
    background = TealBackground,     // Entire app background
    onBackground = OffWhite,         // Main text color on background
    surface = CardBackground,        // Card backgrounds (white)
    onSurface = Color.Black          // Text inside cards
)

@Composable
fun ProtoSDKDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
