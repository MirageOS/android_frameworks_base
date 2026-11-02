/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.dynamicisland.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HUE_BINS = 24
private const val SAMPLE_SIZE = 24
private const val COLOR_ANIM_MS = 500

/** The dominant hue of an album art and how vivid it is (0 = gray cover). */
private class ArtSeed(val hue: Float, val sat: Float)

private fun seedFrom(art: Bitmap?): ArtSeed? {
    if (art == null) return null
    return try {
        val soft = art.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val small = Bitmap.createScaledBitmap(soft, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        val weight = FloatArray(HUE_BINS)
        val satSum = FloatArray(HUE_BINS)
        val hsv = FloatArray(3)
        for (p in pixels) {
            AndroidColor.colorToHSV(p, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.15f) continue
            // Vivid, bright pixels count more than dull ones.
            val w = s * s * v
            val bin = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            weight[bin] += w
            satSum[bin] += s * w
        }
        var best = 0
        for (i in 1 until HUE_BINS) if (weight[i] > weight[best]) best = i
        if (weight[best] < 2f) {
            ArtSeed(0f, 0f)
        } else {
            ArtSeed((best + 0.5f) * 360f / HUE_BINS, satSum[best] / weight[best])
        }
    } catch (e: Exception) {
        null
    }
}

private fun tone(h: Float, s: Float, v: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/**
 * A color scheme tinted by the album art: a light pastel container for the play pill, a dark tone
 * for what is drawn on it and a light accent for icons. Colors fade when the track changes.
 */
@Composable
internal fun rememberAlbumColorScheme(art: Bitmap?): ColorScheme {
    val base = MaterialTheme.colorScheme
    val seed by
        produceState<ArtSeed?>(initialValue = null, art) {
            value = withContext(Dispatchers.Default) { seedFrom(art) }
        }
    val s = seed
    val h = s?.hue ?: 0f
    val k = (s?.sat ?: 0f).coerceIn(0.35f, 1f).let { if (s == null || s.sat == 0f) 0f else it }
    val spec = tween<Color>(COLOR_ANIM_MS)
    val accent by
        animateColorAsState(
            if (s == null) base.primary else tone(h, 0.30f * k, 1f),
            spec,
            label = "albumAccent",
        )
    val container by
        animateColorAsState(
            if (s == null) base.primaryContainer else tone(h, 0.32f * k, 0.96f),
            spec,
            label = "albumContainer",
        )
    val onContainer by
        animateColorAsState(
            if (s == null) base.onPrimaryContainer else tone(h, 0.75f * k, 0.20f),
            spec,
            label = "albumOnContainer",
        )
    return base.copy(
        primary = accent,
        onPrimary = onContainer,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = accent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
    )
}

/** Accent used by the player icons and seek bar: the album accent while the player is shown. */
internal val LocalAlbumAccent = androidx.compose.runtime.compositionLocalOf { Color.White }

/** The expanded player, tinted by the album art. */
@Composable
internal fun MediaPlayerCard(
    media: com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandMediaModel,
    onOpenApp: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val scheme = rememberAlbumColorScheme(media.art)
    androidx.compose.runtime.CompositionLocalProvider(LocalAlbumAccent provides scheme.primary) {
        MaterialTheme(colorScheme = scheme) {
            MediaPlayerCardContent(media = media, onOpenApp = onOpenApp, modifier = modifier)
        }
    }
}
