/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.dynamicisland.ui

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandMediaModel
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "DynamicIslandMedia"

/** Compact island for media: album art on one side and an audio wave on the other. */
@Composable
internal fun MediaIslandPill(
    media: DynamicIslandMediaModel,
    extraCount: Int,
    gap: Dp,
    clickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IslandSides(
        gap = gap,
        modifier =
            modifier
                .height(ISLAND_HEIGHT)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black)
                .thenIf(clickable) { Modifier.clickable(onClick = onClick) }
                .padding(horizontal = 4.dp),
        start = {
            val art = media.art
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                )
            } else {
                Spacer(Modifier.size(28.dp).clip(CircleShape).background(Color.DarkGray))
            }
        },
        end = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioWave(isPlaying = media.isPlaying, modifier = Modifier.padding(end = 10.dp))
                if (extraCount > 0) {
                    Text(
                        text = "+$extraCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun AudioWave(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "audioWave")
    val durations = listOf(420, 560, 380, 620, 480)
    val bars =
        durations.mapIndexed { index, duration ->
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "bar$index",
            )
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(20.dp),
    ) {
        bars.forEach { bar ->
            val fraction = if (isPlaying) bar.value else 0.3f
            Box(
                Modifier.width(3.dp)
                    .height(20.dp * fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Expanded media player, styled like the one in the quick settings panel: album art as the
 * background, app icon and output chip on top, title and artist with a tonal play/pause pill, and
 * previous / wavy seek bar / next / app buttons at the bottom.
 */
@Composable
internal fun MediaPlayerCard(
    media: DynamicIslandMediaModel,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAlbumColorScheme(media.art)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF241F1E))
                .clickable(onClick = onOpenApp)
    ) {
        media.art?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim so the white text stays readable over any artwork.
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC000000))))
        )
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            // Top: app icon + output chip
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIcon(media.packageName)
                OutputChip(
                    name = media.outputName ?: stringResource(R.string.dynamic_island_this_phone)
                )
            }

            // Middle: title/artist + play/pause
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = media.title?.toString().orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = media.artist?.toString().orEmpty(),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val playPauseLabel =
                    stringResource(
                        if (media.isPlaying) R.string.dynamic_island_media_pause
                        else R.string.dynamic_island_media_play
                    )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.size(width = 64.dp, height = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.primaryContainer)
                            .semantics { contentDescription = playPauseLabel }
                            .clickable {
                                val controls = media.controller.transportControls
                                if (media.isPlaying) controls.pause() else controls.play()
                            },
                ) {
                    TransportIcon(
                        kind = if (media.isPlaying) TransportKind.PAUSE else TransportKind.PLAY,
                        tint = colors.onPrimaryContainer,
                        size = 26.dp,
                    )
                }
            }

            // Bottom: previous, seek bar, next, app buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val previousLabel = stringResource(R.string.dynamic_island_media_previous)
                val nextLabel = stringResource(R.string.dynamic_island_media_next)
                TransportButton(
                    kind = TransportKind.PREVIOUS,
                    enabled = media.canSkipPrevious,
                    description = previousLabel,
                    onClick = { media.controller.transportControls.skipToPrevious() },
                )
                WavySeekBar(media = media, modifier = Modifier.weight(1f))
                TransportButton(
                    kind = TransportKind.NEXT,
                    enabled = media.canSkipNext,
                    description = nextLabel,
                    onClick = { media.controller.transportControls.skipToNext() },
                )
                media.customActions.forEach { action ->
                    CustomActionButton(media = media, action = action)
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon =
        remember(packageName) {
            try {
                context.packageManager.getApplicationIcon(packageName).toImageBitmap(ICON_SIZE_PX)
            } catch (e: Exception) {
                null
            }
        }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
    } else {
        Spacer(Modifier.size(28.dp))
    }
}

@Composable
private fun OutputChip(name: CharSequence) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(RoundedCornerShape(percent = 50))
                .background(colors.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Small phone outline
        Canvas(Modifier.size(width = 10.dp, height = 16.dp)) {
            drawRoundRect(
                color = colors.onPrimaryContainer,
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = name.toString(),
            color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransportButton(
    kind: TransportKind,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .clip(CircleShape)
                .semantics { contentDescription = description }
                .thenIf(enabled) { Modifier.clickable(onClick = onClick) },
    ) {
        TransportIcon(
            kind = kind,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            size = 24.dp,
        )
    }
}

@Composable
private fun CustomActionButton(
    media: DynamicIslandMediaModel,
    action: PlaybackState.CustomAction,
) {
    val context = LocalContext.current
    val icon =
        remember(media.packageName, action.icon) {
            try {
                val resources = context.packageManager.getResourcesForApplication(media.packageName)
                resources.getDrawable(action.icon, null).toImageBitmap(ICON_SIZE_PX)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load the icon of ${action.name}", e)
                null
            }
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .clip(CircleShape)
                .semantics { contentDescription = action.name.toString() }
                .clickable { media.controller.transportControls.sendCustomAction(action, null) },
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Seek bar whose played part is a moving wave while the media plays, like the quick settings. */
@Composable
private fun WavySeekBar(media: DynamicIslandMediaModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val position by
        produceState(initialValue = media.currentPositionMs(), media) {
            while (isActive) {
                value = media.currentPositionMs()
                delay(POSITION_TICK_MS)
            }
        }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction =
        dragFraction
            ?: if (media.durationMs > 0) {
                (position.toFloat() / media.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }

    val amplitude by
        animateFloatAsState(
            targetValue = if (media.isPlaying) with(density) { 3.dp.toPx() } else 0f,
            label = "waveAmplitude",
        )
    val phase by
        rememberInfiniteTransition(label = "wavePhase")
            .animateFloat(
                initialValue = 0f,
                targetValue = (2 * PI).toFloat(),
                animationSpec =
                    infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
                label = "phase",
            )

    Canvas(
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .height(32.dp)
                .pointerInput(media) {
                    detectTapGestures { offset ->
                        media.seekToFraction(offset.x / size.width.toFloat())
                    }
                }
                .pointerInput(media) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { media.seekToFraction(it) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                }
    ) {
        val centerY = this.size.height / 2
        val x = this.size.width * fraction
        val stroke = 3.dp.toPx()

        // Part that was not played yet.
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(x, centerY),
            end = Offset(this.size.width, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // Played part: a wave that travels while playing and flattens when paused.
        val wavelength = 22.dp.toPx()
        val step = 2.dp.toPx()
        val path = Path()
        path.moveTo(0f, centerY)
        var px = 0f
        while (px <= x) {
            val y = centerY + amplitude * sin((px / wavelength * 2 * PI).toFloat() - phase)
            path.lineTo(px, y)
            px += step
        }
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Thumb
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(x - 2.dp.toPx(), centerY - 10.dp.toPx()),
            size = Size(4.dp.toPx(), 20.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

internal enum class TransportKind {
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
}

/** Draws the transport icons by hand so no icon library is needed. */
@Composable
private fun TransportIcon(kind: TransportKind, tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            TransportKind.PLAY -> {
                val path =
                    Path().apply {
                        moveTo(0.3f * w, 0.18f * h)
                        lineTo(0.3f * w, 0.82f * h)
                        lineTo(0.84f * w, 0.5f * h)
                        close()
                    }
                drawPath(path, tint)
            }
            TransportKind.PAUSE -> {
                val corner = CornerRadius(0.06f * w)
                drawRoundRect(
                    tint,
                    Offset(0.24f * w, 0.2f * h),
                    Size(0.18f * w, 0.6f * h),
                    corner,
                )
                drawRoundRect(
                    tint,
                    Offset(0.58f * w, 0.2f * h),
                    Size(0.18f * w, 0.6f * h),
                    corner,
                )
            }
            TransportKind.NEXT -> {
                val path =
                    Path().apply {
                        moveTo(0.18f * w, 0.2f * h)
                        lineTo(0.18f * w, 0.8f * h)
                        lineTo(0.68f * w, 0.5f * h)
                        close()
                    }
                drawPath(path, tint)
                drawRoundRect(
                    tint,
                    Offset(0.74f * w, 0.2f * h),
                    Size(0.1f * w, 0.6f * h),
                    CornerRadius(0.04f * w),
                )
            }
            TransportKind.PREVIOUS -> {
                val path =
                    Path().apply {
                        moveTo(0.82f * w, 0.2f * h)
                        lineTo(0.82f * w, 0.8f * h)
                        lineTo(0.32f * w, 0.5f * h)
                        close()
                    }
                drawPath(path, tint)
                drawRoundRect(
                    tint,
                    Offset(0.16f * w, 0.2f * h),
                    Size(0.1f * w, 0.6f * h),
                    CornerRadius(0.04f * w),
                )
            }
        }
    }
}

/** Opens the media app through the session activity, if the app provides one. */
internal fun openMediaApp(media: DynamicIslandMediaModel) {
    val intent: PendingIntent = media.controller.sessionActivity ?: return
    try {
        intent.send()
    } catch (e: PendingIntent.CanceledException) {
        Log.w(TAG, "Unable to open ${media.packageName}", e)
    }
}

private const val ICON_SIZE_PX = 96
private const val POSITION_TICK_MS = 250L

private fun Drawable.toImageBitmap(sizePx: Int): androidx.compose.ui.graphics.ImageBitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap.asImageBitmap()
}
