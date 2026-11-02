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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandLayoutSettings
import kotlin.math.abs
import kotlin.math.max

// Room kept on each side of the camera hole when the island fits itself to the cutout.
private val AUTO_GAP_MARGIN = 6.dp
private val AUTO_MIN_GAP = 32.dp
private val AUTO_MAX_GAP = 160.dp
private val AUTO_MAX_TOP = 24.dp
private const val AUTO_MIN_SCALE = 0.7f

/**
 * Where the island is drawn: the [gap] left for the camera in the middle of the pill, the
 * [topPadding] from the top edge of the screen, the horizontal shift [offsetX] from the center and
 * the [scale] of the pill. All the dp values are in regular (unscaled) dp.
 */
internal data class DynamicIslandLayout(
    val gap: Dp = CAMERA_GAP,
    val topPadding: Dp = ISLAND_TOP_PADDING,
    val offsetX: Dp = 0.dp,
    val scale: Float = 1f,
) {
    /** The density the pill is drawn with, so every dp and sp inside it follows [scale]. */
    fun scaled(density: Density): Density = Density(density.density * scale, density.fontScale)
}

/**
 * The layout chosen in Settings: the values the user set, or, with automatic adjustment on, the
 * ones that fit the display cutout of this device.
 */
@Composable
internal fun rememberDynamicIslandLayout(settings: DynamicIslandLayoutSettings): DynamicIslandLayout {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // The configuration changes on rotation and on fold/unfold, which move the cutout.
    return remember(settings, configuration, density) {
        if (settings.autoPosition) {
            autoLayout(context, density)
        } else {
            DynamicIslandLayout(
                gap = settings.cameraGapDp.dp,
                topPadding = settings.positionYDp.dp,
                offsetX = settings.positionXDp.dp,
                scale = settings.sizePercent / 100f,
            )
        }
    }
}

/**
 * Fits the island to the camera cutout: the gap is as wide as the hole plus a margin, the pill is
 * centered on the hole, and it shrinks when the status bar is not tall enough for it. Falls back
 * to the default layout when the top of the screen has no cutout.
 */
private fun autoLayout(context: Context, density: Density): DynamicIslandLayout {
    val cutout = runCatching { context.display?.cutout }.getOrNull() ?: return DynamicIslandLayout()
    val metrics = context.resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels

    // Only the cutouts at the top edge matter; when there are several, take the one nearest to the
    // middle of the screen.
    val hole =
        cutout.boundingRects
            .filter { !it.isEmpty && it.exactCenterY() < screenHeight / 6f }
            .minByOrNull { abs(it.exactCenterX() - screenWidth / 2f) }
            ?: return DynamicIslandLayout()

    return with(density) {
        val gap = (hole.width().toDp() + AUTO_GAP_MARGIN * 2).coerceIn(AUTO_MIN_GAP, AUTO_MAX_GAP)
        val offsetX = (hole.exactCenterX() - screenWidth / 2f).toDp()
        val top = (hole.exactCenterY().toDp() - ISLAND_HEIGHT / 2).coerceIn(0.dp, AUTO_MAX_TOP)
        val barHeight = SystemBarUtils.getStatusBarHeight(context).toDp()
        val scale = ((barHeight - top) / ISLAND_HEIGHT).coerceIn(AUTO_MIN_SCALE, 1f)
        DynamicIslandLayout(gap = gap, topPadding = top, offsetX = offsetX, scale = scale)
    }
}

/**
 * The two sides of the pill with the camera [gap] between them. Both sides get the width of the
 * wider one, so the middle of the gap is always the middle of the pill (and so the camera, which
 * the pill is centered on) whatever is shown on each side.
 */
@Composable
internal fun IslandSides(
    gap: Dp,
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { start() }
            Box { end() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaceable = measurables[0].measure(loose)
        val endPlaceable = measurables[1].measure(loose)
        val side = max(startPlaceable.width, endPlaceable.width)
        val width = (2 * side + gap.roundToPx()).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                max(startPlaceable.height, endPlaceable.height)
            }
        layout(width, height) {
            startPlaceable.placeRelative(0, (height - startPlaceable.height) / 2)
            endPlaceable.placeRelative(
                width - endPlaceable.width,
                (height - endPlaceable.height) / 2,
            )
        }
    }
}
