package com.fantasyidler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary              = GoldPrimary,
    onPrimary            = DarkBackground,
    primaryContainer     = GoldContainer,
    onPrimaryContainer   = GoldOnContainer,
    secondary            = BrownSecondary,
    onSecondary          = ParchmentText,
    secondaryContainer   = BrownContainer,
    onSecondaryContainer = BrownOnContainer,
    background           = DarkBackground,
    onBackground         = ParchmentText,
    surface              = DarkSurface,
    onSurface            = ParchmentText,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = ParchmentTextMuted,
    error                = ErrorRed,
    onError              = ParchmentText,
)

private val MidnightColorScheme = darkColorScheme(
    primary              = GoldPrimary,
    onPrimary            = MidnightBackground,
    primaryContainer     = GoldContainer,
    onPrimaryContainer   = GoldOnContainer,
    secondary            = BrownSecondary,
    onSecondary          = ParchmentText,
    secondaryContainer   = BrownContainer,
    onSecondaryContainer = BrownOnContainer,
    background           = MidnightBackground,
    onBackground         = ParchmentText,
    surface              = MidnightSurface,
    onSurface            = ParchmentText,
    surfaceVariant       = MidnightSurfaceVariant,
    onSurfaceVariant     = MidnightTextMuted,
    error                = ErrorRed,
    onError              = ParchmentText,
)

private val LightColorScheme = lightColorScheme(
    primary              = GoldPrimary,
    onPrimary            = DarkText,
    primaryContainer     = GoldContainerLight,
    onPrimaryContainer   = GoldOnContainerLight,
    secondary            = BrownSecondary,
    onSecondary          = LightBackground,
    secondaryContainer   = BrownContainerLight,
    onSecondaryContainer = BrownOnContainerLight,
    background           = LightBackground,
    onBackground         = DarkText,
    surface              = LightSurface,
    onSurface            = DarkText,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = DarkTextMuted,
    error                = ErrorRed,
    onError              = ParchmentText,
)

@Composable
fun FantasyIdlerTheme(
    themePreference: String = "dark",
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themePreference) {
        "light"    -> LightColorScheme
        "dark"     -> DarkColorScheme
        "midnight" -> MidnightColorScheme
        else       -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
