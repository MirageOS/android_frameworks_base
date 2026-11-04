/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** A vertical pill slider: dark track, filled from the bottom, icon at the bottom. */
@Composable
fun VerticalPillSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFractionChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Alpha of the track. Below 1f the shade blur shows through (Motorola style). */
    trackAlpha: Float = 1f,
    icon: @Composable (tint: Color) -> Unit,
) {
    val onChange by rememberUpdatedState(onFractionChange)
    val onFinished by rememberUpdatedState(onFractionChangeFinished)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: fraction).coerceIn(0f, 1f)
    val scheme = MaterialTheme.colorScheme
    val iconTint = if (shown > 0.14f) scheme.onPrimary else scheme.onSurface

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.surfaceContainerHighest.copy(alpha = trackAlpha))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        onChange((1f - offset.y / size.height).coerceIn(0f, 1f))
                        onFinished()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val f = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            dragging = f
                            onChange(f)
                        },
                        onDragEnd = {
                            dragging = null
                            onFinished()
                        },
                        onDragCancel = {
                            dragging = null
                            onFinished()
                        },
                    ) { change, _ ->
                        change.consume()
                        val f = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        dragging = f
                        onChange(f)
                    }
                }
    ) {
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(shown)
                .background(scheme.primary)
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)) { icon(iconTint) }
    }
}
