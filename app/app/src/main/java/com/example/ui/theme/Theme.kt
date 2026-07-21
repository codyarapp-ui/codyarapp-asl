package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CodyarLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C2B4A), // CodyarNavy
    primaryContainer = Color(0xFFE2E8F0),
    onPrimary = Color.White,
    secondary = Color(0xFFE0393E), // CodyarRed
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA), // CodyarBg
    onBackground = Color(0xFF0D1527), // Almost black slate for maximum readability and clear text
    surface = Color.White,
    onSurface = Color(0xFF0D1527), // Almost black slate
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF1E293B), // Darker text color instead of light gray, ensuring clear contrast
    error = Color(0xFFD32F2F),
    outline = Color(0xFFB0BEC5) // Clearer borders
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always use the optimized light color scheme with high-contrast text colors
    MaterialTheme(
        colorScheme = CodyarLightColorScheme,
        typography = Typography,
        content = content
    )
}

