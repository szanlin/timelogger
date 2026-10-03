package com.y3lc.timelogger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val lightColors = lightColorScheme(
    primary = Color(0xFF326A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBCEBEB),
    onPrimaryContainer = Color(0xFF103D3E),
    secondary = Color(0xFF4F6263),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E7E7),
    onSecondaryContainer = Color(0xFF334B4C),
    tertiary = Color(0xFF54617D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE3FF),
    onTertiaryContainer = Color(0xFF35425E),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDBE4E3),
    onSurfaceVariant = Color(0xFF414B4B),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFA1D0D0),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF1A5152),
    onPrimaryContainer = Color(0xFFBCEBEB),
    secondary = Color(0xFFB3C9C9),
    onSecondary = Color(0xFF1E3536),
    secondaryContainer = Color(0xFF354B4C),
    onSecondaryContainer = Color(0xFFD2E7E7),
    tertiary = Color(0xFFBCC7E8),
    onTertiary = Color(0xFF26314B),
    tertiaryContainer = Color(0xFF3C4963),
    onTertiaryContainer = Color(0xFFDCE3FF),
    background = Color(0xFF101918),
    surface = Color(0xFF101918),
    surfaceVariant = Color(0xFF414B4B),
    onSurfaceVariant = Color(0xFFC0C9C8),
)

@Composable
fun TimeLoggerTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) darkColors else lightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun AppPageContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}
