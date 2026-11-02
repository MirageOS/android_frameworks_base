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
 * Keys of the Dynamic Island options in [android.provider.Settings.System]. They are written by
 * Settings (Personalise > Dynamic Island); keep them in sync with DynamicIslandSettings.java in
 * packages/apps/Settings.
 */
object DynamicIslandSettingsKeys {
    const val ENABLED = "dynamic_island_enabled"
    const val EXPAND_ON_TAP = "dynamic_island_expand_on_tap"
    const val LIVE_RECORDER = "dynamic_island_live_recorder"
    const val LIVE_SCREEN_RECORDER = "dynamic_island_live_screen_recorder"
    const val LIVE_FLASHLIGHT = "dynamic_island_live_flashlight"
    const val LIVE_CALLS = "dynamic_island_live_calls"
    const val LIVE_MEDIA = "dynamic_island_live_media"
    const val LIVE_ALARMS = "dynamic_island_live_alarms"
    const val BLOCKED_PACKAGES = "dynamic_island_blocked_packages"

    /** Every key that is a 0/1 flag. */
    val BOOLEAN_KEYS =
        listOf(
            ENABLED,
            EXPAND_ON_TAP,
            LIVE_RECORDER,
            LIVE_SCREEN_RECORDER,
            LIVE_FLASHLIGHT,
            LIVE_CALLS,
            LIVE_MEDIA,
            LIVE_ALARMS,
        )
}
