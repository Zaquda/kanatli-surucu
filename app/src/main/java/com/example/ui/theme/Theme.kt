package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MartiGreen,
    secondary = MintCyan,
    tertiary = MartiGold,
    background = DarkMidnight,
    surface = DarkSlate,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = CoralRed,
    onError = Color.White,
    surfaceVariant = SoftSlate,
    onSurfaceVariant = TextGray
)

private val LightColorScheme = lightColorScheme(
    primary = MartiEmerald,
    secondary = MintCyan,
    tertiary = MartiGold,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextDark,
    onSurface = TextDark,
    error = CoralRed,
    onError = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // We will use our custom beautiful dark/light themes rather than system dynamic colors,
    // which ensures the Martı Brand visual identity remains cohesive.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
