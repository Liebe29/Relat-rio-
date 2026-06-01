package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = MintOnPrimaryDark,
    primaryContainer = MintPrimaryContainerDark,
    onPrimaryContainer = MintOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = VelvetTertiaryDark,
    onTertiary = VelvetOnTertiaryDark,
    tertiaryContainer = VelvetTertiaryContainerDark,
    onTertiaryContainer = VelvetOnTertiaryContainerDark,
    background = SoftBackgroundDark,
    surface = SoftSurfaceDark,
    onSurface = SoftOnSurfaceDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = MintOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = VelvetTertiary,
    onTertiary = VelvetOnTertiary,
    tertiaryContainer = VelvetTertiaryContainer,
    onTertiaryContainer = VelvetOnTertiaryContainer,
    background = SoftBackground,
    surface = SoftSurface,
    onSurface = SoftOnSurface
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set to false to ensure our beautiful premium colors apply consistently
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
