package com.dc.aurora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    background          = Background,
    surface             = Surface,
    surfaceVariant      = SurfaceHigh,
    primary             = AuroraIndigo,
    secondary           = AuroraViolet,
    tertiary            = AuroraTeal,
    onBackground        = OnBackground,
    onSurface           = OnBackground,
    onSurfaceVariant    = Muted,
    onPrimary           = OnBackground,
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography,
        content     = content,
    )
}
