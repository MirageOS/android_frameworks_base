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

package com.android.systemui.statusbar.dynamicisland.ui.model

import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel

/** What kind of live update an island item represents. Decides which Settings option gates it. */
enum class DynamicIslandKind {
    CALL,
    SCREEN_RECORD,
    ALARM,
    RECORDER,
    APP,
}

/** One live update shown by the Dynamic Island, backed by an ongoing activity chip. */
data class DynamicIslandItem(
    val kind: DynamicIslandKind,
    val chip: OngoingActivityChipModel.Active,
)
