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

import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandSettingsModel
import com.android.systemui.statusbar.dynamicisland.ui.model.DynamicIslandItem
import com.android.systemui.statusbar.dynamicisland.ui.model.DynamicIslandKind

/** Decides which ongoing activity chips the Dynamic Island takes over, based on the settings. */
object DynamicIslandItems {

    // Keys used by the chip view models (CallChipViewModel.KEY_PREFIX and
    // ScreenRecordChipViewModel.KEY).
    private const val CALL_KEY_PREFIX = "callChip-"
    private const val SCREEN_RECORD_KEY = "ScreenRecord"

    fun classify(chip: OngoingActivityChipModel.Active): DynamicIslandKind {
        val pkg = chip.managingPackageName.orEmpty()
        return when {
            chip.key.startsWith(CALL_KEY_PREFIX) -> DynamicIslandKind.CALL
            chip.key == SCREEN_RECORD_KEY -> DynamicIslandKind.SCREEN_RECORD
            pkg.contains("clock", ignoreCase = true) -> DynamicIslandKind.ALARM
            pkg.contains("recorder", ignoreCase = true) -> DynamicIslandKind.RECORDER
            else -> DynamicIslandKind.APP
        }
    }

    /**
     * Whether the island is responsible for [chip] (so the old status bar chip must go away),
     * whether or not the chip is hidden at the moment.
     */
    fun isHandled(
        chip: OngoingActivityChipModel.Active,
        settings: DynamicIslandSettingsModel,
    ): Boolean {
        if (!settings.enabled || !settings.isPackageAllowed(chip.managingPackageName)) {
            return false
        }
        return when (classify(chip)) {
            DynamicIslandKind.CALL -> settings.liveCalls
            DynamicIslandKind.SCREEN_RECORD -> settings.liveScreenRecorder
            DynamicIslandKind.ALARM -> settings.liveAlarms
            DynamicIslandKind.RECORDER -> settings.liveRecorder
            DynamicIslandKind.APP -> true
        }
    }

    /** The items the island shows right now, in the priority order of [chips]. */
    fun items(
        chips: List<OngoingActivityChipModel.Active>,
        settings: DynamicIslandSettingsModel,
    ): List<DynamicIslandItem> =
        chips
            .filter { !it.isHidden && isHandled(it, settings) }
            .map { DynamicIslandItem(classify(it), it) }
}
