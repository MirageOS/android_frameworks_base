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

package com.android.systemui.statusbar.dynamicisland.data.model

/**
 * Where the Dynamic Island sits and how big it is, as chosen in Settings (Personalise > Dynamic
 * Island > Position and size).
 *
 * With [autoPosition] on the island fits itself to the display cutout and the other values are
 * ignored. The ranges and defaults must match DynamicIslandSettings.java in packages/apps/Settings.
 */
data class DynamicIslandLayoutSettings(
    val autoPosition: Boolean = true,
    /** Horizontal shift from the center of the screen, in dp. Negative moves it to the left. */
    val positionXDp: Int = DEFAULT_POSITION_X,
    /** Distance from the top edge of the screen, in dp. */
    val positionYDp: Int = DEFAULT_POSITION_Y,
    /** Size of the pill, in percent of the normal size. */
    val sizePercent: Int = DEFAULT_SIZE_PERCENT,
    /** Space left in the middle of the pill for the front camera, in dp. */
    val cameraGapDp: Int = DEFAULT_CAMERA_GAP,
) {
    companion object {
        const val DEFAULT_POSITION_X = 0
        const val DEFAULT_POSITION_Y = 4
        const val DEFAULT_SIZE_PERCENT = 100
        const val DEFAULT_CAMERA_GAP = 56

        val POSITION_X_RANGE = -100..100
        val POSITION_Y_RANGE = 0..24
        val SIZE_PERCENT_RANGE = 70..130
        val CAMERA_GAP_RANGE = 32..120
    }
}
