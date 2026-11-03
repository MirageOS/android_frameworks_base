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

import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.settingslib.volume.shared.model.AudioStream
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.common.ui.compose.Icon as SysUiIcon
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.qs.panels.data.repository.QsStyleRepository
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.toElementKey
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Motorola style Quick Settings layout. The tile grid is the paginated one; [HeaderBlock] draws
 * the Wi-Fi, mobile data and Bluetooth tiles stacked next to a media volume slider and a
 * brightness slider.
 */
class MotorolaGridLayout
@Inject
constructor(
    private val paginated: PaginatedGridLayout,
    private val detailsViewModel: DetailsViewModel,
    private val tileHapticsViewModelFactory: TileHapticsViewModel.Factory,
    private val audioStreamSliderViewModelFactory: AudioStreamSliderViewModel.Factory,
    private val qsStyleRepository: QsStyleRepository,
) : GridLayout by paginated {

    @Composable
    fun ContentScope.HeaderBlock(
        tiles: List<TileViewModel>,
        listening: () -> Boolean,
        brightnessViewModel: BrightnessSliderViewModel?,
        modifier: Modifier = Modifier,
    ) {
        val blockTiles =
            remember(tiles) {
                HeaderOrder.mapNotNull { spec -> tiles.firstOrNull { it.spec.spec == spec } }
            }
        val bounceables = remember(blockTiles) { List(blockTiles.size) { BounceableTileViewModel() } }
        val scope = rememberCoroutineScope()
        val spacing = dimensionResource(R.dimen.qs_tile_margin_horizontal)
        val count = blockTiles.size.coerceAtLeast(1)
        val blockHeight = CommonTileDefaults.TileHeight * count + spacing * (count - 1)

        val qsStyleSettings by qsStyleRepository.settings.collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalQsStyle provides
                QsStyleUi(qsStyleSettings.motorolaStyle, qsStyleSettings.showLabels)
        ) {
        Row(
            modifier = modifier.fillMaxWidth().height(blockHeight),
            horizontalArrangement = spacedBy(spacing),
        ) {
            Column(modifier = Modifier.weight(2.3f), verticalArrangement = spacedBy(spacing)) {
                blockTiles.forEachIndexed { index, tile ->
                    Element(tile.spec.toElementKey(), Modifier) {
                        Tile(
                            tile = tile,
                            iconOnly = false,
                            squishiness = { 1f },
                            tileHapticsViewModelFactory = tileHapticsViewModelFactory,
                            coroutineScope = scope,
                            bounceableInfo =
                                bounceables.bounceableInfo(
                                    SizedTileImpl(tile, 1),
                                    index = index,
                                    column = 0,
                                    columns = 1,
                                    isFirstInRow = true,
                                    isLastInRow = true,
                                ),
                            detailsViewModel = detailsViewModel,
                            isVisible = listening,
                        )
                    }
                }
            }
            VolumeColumn(Modifier.weight(1f).fillMaxHeight())
            if (brightnessViewModel != null) {
                BrightnessColumn(brightnessViewModel, Modifier.weight(1f).fillMaxHeight())
            }
        }
        TileListener(blockTiles, listening)
        }
    }

    @Composable
    private fun VolumeColumn(modifier: Modifier) {
        val scope = rememberCoroutineScope()
        val viewModel = remember {
            audioStreamSliderViewModelFactory.create(
                AudioStreamSliderViewModel.FactoryAudioStreamWrapper(
                    AudioStream(AudioManager.STREAM_MUSIC)
                ),
                scope,
            )
        }
        val state by viewModel.slider.collectAsStateWithLifecycle()
        val range = state.valueRange
        val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
        VerticalPillSlider(
            fraction = ((state.value - range.start) / span).coerceIn(0f, 1f),
            onFractionChange = { viewModel.onValueChanged(state, range.start + it * span) },
            onFractionChangeFinished = { viewModel.onValueChangeFinished() },
            enabled = state.isEnabled,
            modifier = modifier,
        ) { tint ->
            val icon = state.icon
            if (icon != null) {
                SysUiIcon(icon = icon, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }

    @Composable
    private fun BrightnessColumn(viewModel: BrightnessSliderViewModel, modifier: Modifier) {
        val scope = rememberCoroutineScope()
        val min = viewModel.minBrightness.value
        val max = viewModel.maxBrightness.value
        val current = viewModel.currentBrightness.value
        val span = (max - min).coerceAtLeast(1)
        var last by remember { mutableIntStateOf(current) }
        VerticalPillSlider(
            fraction = ((current - min).toFloat() / span).coerceIn(0f, 1f),
            onFractionChange = { f ->
                last = (min + f * span).roundToInt().coerceIn(min, max)
                scope.launch { viewModel.onDrag(Drag.Dragging(GammaBrightness(last))) }
            },
            onFractionChangeFinished = {
                scope.launch { viewModel.onDrag(Drag.Stopped(GammaBrightness(last))) }
            },
            modifier = modifier,
        ) { tint ->
            SunIcon(tint)
        }
    }

    companion object {
        /** Tiles drawn in the header block, top to bottom. They are removed from the grid. */
        val HeaderOrder = listOf("wifi", "cell", "bt")
        val HeaderSpecs = HeaderOrder.toSet()
        private val SliderWidth = 64.dp
    }
}

@Composable
private fun SunIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val c = center
        drawCircle(tint, radius = size.minDimension * 0.2f, center = c)
        val inner = size.minDimension * 0.34f
        val outer = size.minDimension * 0.48f
        for (i in 0 until 8) {
            val a = i * (PI / 4).toFloat()
            val dx = cos(a)
            val dy = sin(a)
            drawLine(
                tint,
                Offset(c.x + dx * inner, c.y + dy * inner),
                Offset(c.x + dx * outer, c.y + dy * outer),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
