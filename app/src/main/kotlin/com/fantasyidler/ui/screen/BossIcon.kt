package com.fantasyidler.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

// Decoded once per boss id and shared by every screen; the 512px sources are 8-15 KB each.
private val bossIconCache = mutableMapOf<String, ImageBitmap?>()

private fun loadBossIcon(context: Context, bossId: String): ImageBitmap? =
    bossIconCache.getOrPut(bossId) {
        try {
            context.assets.open("sprites/bosses/$bossId.png")
                .use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

/**
 * Pixel-art portrait for a raid boss, loaded from `assets/sprites/bosses/<bossId>.png`.
 * [silhouette] tints the art to a featureless shape for bosses the player hasn't met yet.
 * When no art exists for the id, [fallbackEmoji] renders in its place so new bosses added
 * without an icon degrade to the old emoji look instead of an empty gap.
 */
@Composable
fun BossIcon(
    bossId: String,
    modifier: Modifier = Modifier,
    silhouette: Boolean = false,
    fallbackEmoji: String? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(bossId) { loadBossIcon(context, bossId) }
    if (bitmap != null) {
        Image(
            bitmap             = bitmap,
            contentDescription = null,
            modifier           = modifier,
            filterQuality      = FilterQuality.None,
            colorFilter        = if (silhouette)
                ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            else null,
        )
    } else if (fallbackEmoji != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = fallbackEmoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}
