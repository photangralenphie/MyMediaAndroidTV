package com.photangralenphie.mymedia.androidtv.ui

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.photangralenphie.mymedia.androidtv.data.Appearance

@Composable
fun MyMediaTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val accent = runCatching { Color(AndroidColor.parseColor(appearance.accentHex)) }
        .getOrDefault(Color(0xFFFF981F))
    val scheme = if (appearance.isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            primaryContainer = accent.copy(alpha = .24f),
            onPrimaryContainer = accent,
            secondary = accent,
            secondaryContainer = accent.copy(alpha = .24f),
            onSecondaryContainer = accent,
            background = Color(0xFF090B0F),
            surface = Color(0xFF11151B),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = .18f),
            onPrimaryContainer = accent,
            secondary = accent,
            secondaryContainer = accent.copy(alpha = .18f),
            onSecondaryContainer = accent,
            background = Color(0xFFF2F3F5),
            surface = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
