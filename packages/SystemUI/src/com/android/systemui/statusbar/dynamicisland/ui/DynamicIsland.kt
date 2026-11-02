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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.compose.modifiers.thenIf
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.compose.ChipContent
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChip
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandLayoutSettings
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandMediaModel
import com.android.systemui.statusbar.dynamicisland.ui.model.DynamicIslandItem
import com.android.systemui.statusbar.dynamicisland.ui.model.DynamicIslandKind
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import kotlinx.coroutines.delay

internal val ISLAND_HEIGHT = 36.dp
internal val ISLAND_TOP_PADDING = 4.dp
// Space left in the middle of the pill for the front camera.
internal val CAMERA_GAP = 56.dp
private val CARD_WIDTH = 400.dp
private const val AUTO_COLLAPSE_MS = 12_000L

/**
 * Motorola-style Dynamic Island: a black pill centered on the camera showing the highest priority
 * live update (icon on one side, timer or text on the other); when there are none it shows the
 * playing [media] (album art and an audio wave). Tapping it expands a card with every live update
 * and the media player, when [expandOnTap] is on.
 *
 * [layoutSettings] says where the island sits and how big it is: fitted to the display cutout, or
 * the values chosen in Settings.
 */
@Composable
fun DynamicIsland(
    items: List<DynamicIslandItem>,
    media: DynamicIslandMediaModel?,
    expandOnTap: Boolean,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
    layoutSettings: DynamicIslandLayoutSettings = DynamicIslandLayoutSettings(),
) {
    var expanded by remember { mutableStateOf(false) }
    val islandLayout = rememberDynamicIslandLayout(layoutSettings)
    val density = LocalDensity.current

    // Collapse when there is nothing left to show or the option was turned off.
    val hasContent = items.isNotEmpty() || media != null
    val canExpand = expandOnTap && hasContent
    LaunchedEffect(canExpand) {
        if (!canExpand) expanded = false
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(AUTO_COLLAPSE_MS)
            expanded = false
        }
    }

    if (!hasContent) return

    Box(modifier = modifier.offset { IntOffset(islandLayout.offsetX.roundToPx(), 0) }) {
        // Inside the pill every dp and sp is scaled by the size of the island, so the camera gap
        // and the top padding, which are in regular dp, are brought back to it.
        CompositionLocalProvider(LocalDensity provides islandLayout.scaled(density)) {
            val gap = islandLayout.gap / islandLayout.scale
            if (items.isNotEmpty()) {
                IslandPill(
                    item = items.first(),
                    extraCount = items.size - 1 + (if (media != null) 1 else 0),
                    gap = gap,
                    clickable = expandOnTap,
                    onClick = { expanded = !expanded },
                    iconViewStore = iconViewStore,
                    modifier = Modifier.padding(top = islandLayout.topPadding / islandLayout.scale),
                )
            } else if (media != null) {
                MediaIslandPill(
                    media = media,
                    extraCount = 0,
                    gap = gap,
                    clickable = expandOnTap,
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(top = islandLayout.topPadding / islandLayout.scale),
                )
            }
        }
        // The popup stays alive until the exit animation is over.
        val cardState = remember { MutableTransitionState(false) }
        cardState.targetState = expanded && canExpand
        if (cardState.currentState || cardState.targetState) {
            Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                AnimatedVisibility(
                    visibleState = cardState,
                    enter =
                        fadeIn(tween(160)) +
                            scaleIn(
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                                initialScale = 0.55f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            ),
                    exit =
                        fadeOut(tween(120)) +
                            scaleOut(
                                animationSpec = tween(180),
                                targetScale = 0.55f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            ),
                ) {
                    IslandCard(
                        items = items,
                        media = media,
                        topPadding = islandLayout.topPadding,
                        iconViewStore = iconViewStore,
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandPill(
    item: DynamicIslandItem,
    extraCount: Int,
    gap: Dp,
    clickable: Boolean,
    onClick: () -> Unit,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    val chip = item.chip
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
            // Icon side. Countdown chips have no icon.
            if (chip.icon != null && chip.content !is OngoingActivityChipModel.Content.Countdown) {
                OngoingActivityChip(
                    model =
                        chip.copy(
                            content = OngoingActivityChipModel.Content.IconOnly,
                            clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
                            transitionManager = null,
                        ),
                    iconViewStore = iconViewStore,
                )
            }
        },
        end = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Content side: timer / text, drawn on the chip's own background so it stays
                // readable.
                if (chip.content !is OngoingActivityChipModel.Content.IconOnly) {
                    val context = LocalContext.current
                    Box(
                        modifier =
                            Modifier.clip(RoundedCornerShape(percent = 50))
                                .background(Color(chip.colors.background(context).defaultColor))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        ChipContent(viewModel = chip.content, icon = null, colors = chip.colors)
                    }
                }

                if (extraCount > 0) {
                    Text(
                        text = "+$extraCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun IslandCard(
    items: List<DynamicIslandItem>,
    media: DynamicIslandMediaModel?,
    topPadding: Dp,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = topPadding).width(CARD_WIDTH),
    ) {
        if (items.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black)
                        .padding(16.dp),
            ) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(labelFor(item.kind)),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // The regular chip: shows the timer and keeps its own click action (open
                        // the app, stop the recording, ...).
                        OngoingActivityChip(model = item.chip, iconViewStore = iconViewStore)
                    }
                }
            }
        }
        if (media != null) {
            MediaPlayerCard(media = media, onOpenApp = { openMediaApp(media) })
        }
    }
}

private fun labelFor(kind: DynamicIslandKind): Int =
    when (kind) {
        DynamicIslandKind.CALL -> R.string.dynamic_island_label_call
        DynamicIslandKind.SCREEN_RECORD -> R.string.dynamic_island_label_screen_record
        DynamicIslandKind.ALARM -> R.string.dynamic_island_label_alarm
        DynamicIslandKind.RECORDER -> R.string.dynamic_island_label_recorder
        DynamicIslandKind.APP -> R.string.dynamic_island_label_app
    }
