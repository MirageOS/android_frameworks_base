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

/** Snapshot of the Dynamic Island options chosen by the user in Settings. */
data class DynamicIslandSettingsModel(
    /** Master switch. When false the island must not be shown at all. */
    val enabled: Boolean = true,
    /** Tapping the island expands it (media player, recording, alarm, ...). */
    val expandOnTap: Boolean = true,
    val liveRecorder: Boolean = true,
    val liveScreenRecorder: Boolean = true,
    val liveFlashlight: Boolean = true,
    val liveCalls: Boolean = true,
    val liveMedia: Boolean = true,
    val liveAlarms: Boolean = true,
    /** Packages whose live updates must not use the island. */
    val blockedPackages: Set<String> = emptySet(),
) {
    /** Whether live updates coming from [packageName] may use the island. */
    fun isPackageAllowed(packageName: String?): Boolean =
        packageName == null || packageName !in blockedPackages

    companion object {
        /** Used until the real values are read, so the island never flashes on by mistake. */
        val Disabled = DynamicIslandSettingsModel(enabled = false)
    }
}
