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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R

private val FLASHLIGHT_COLOR = Color(0xFFFFD54F)

/** The island pill shown while the flashlight is on: a softly pulsing icon and its name. */
@Composable
internal fun FlashlightIslandPill(
    gap: Dp,
    clickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse by
        rememberInfiniteTransition(label = "flashlightPulse")
            .animateFloat(
                initialValue = 0.65f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(900, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                label = "flashlightPulseAlpha",
            )
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
            FlashlightGlyph(
                tint = FLASHLIGHT_COLOR.copy(alpha = pulse),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        end = {
            Text(
                text = stringResource(R.string.dynamic_island_flashlight),
                color = FLASHLIGHT_COLOR,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
    )
}

/** A flashlight drawn with a Canvas, like the other island icons, so no icon library is needed. */
@Composable
private fun FlashlightGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val head =
            Path().apply {
                moveTo(0.26f * w, 0.06f * h)
                lineTo(0.74f * w, 0.06f * h)
                lineTo(0.64f * w, 0.34f * h)
                lineTo(0.36f * w, 0.34f * h)
                close()
            }
        drawPath(head, tint)
        drawRoundRect(
            tint,
            Offset(0.36f * w, 0.40f * h),
            Size(0.28f * w, 0.54f * h),
            CornerRadius(0.08f * w),
        )
    }
}
