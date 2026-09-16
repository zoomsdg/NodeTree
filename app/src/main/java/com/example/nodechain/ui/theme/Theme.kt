package com.example.nodechain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF29695A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3F1DD),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B),
    tertiary = Color(0xFF416276),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97D5C2),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0D5043),
    onPrimaryContainer = Color(0xFFB3F1DD),
    secondary = Color(0xFFB2CCC2),
    tertiary = Color(0xFFA8CAE1),
    error = Color(0xFFFFB4AB),
)

/** 结果状态用的语义色，深浅色下各一套。 */
data class OutcomeColors(
    val pass: Color,
    val passContainer: Color,
    val fail: Color,
    val failContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
)

val outcomeColorsLight = OutcomeColors(
    pass = Color(0xFF1B6B3F),
    passContainer = Color(0xFFCFF3DC),
    fail = Color(0xFFB3261E),
    failContainer = Color(0xFFFFDAD6),
    neutral = Color(0xFF4A5C6A),
    neutralContainer = Color(0xFFDCE4EC),
)

val outcomeColorsDark = OutcomeColors(
    pass = Color(0xFF7ADCA4),
    passContainer = Color(0xFF17452C),
    fail = Color(0xFFFFB4AB),
    failContainer = Color(0xFF5C1712),
    neutral = Color(0xFFB4C3D0),
    neutralContainer = Color(0xFF32404C),
)

@Composable
fun NodeChainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // minSdk 31，动态取色一定可用，不需要版本判断
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun outcomeColors(darkTheme: Boolean = isSystemInDarkTheme()): OutcomeColors =
    if (darkTheme) outcomeColorsDark else outcomeColorsLight
