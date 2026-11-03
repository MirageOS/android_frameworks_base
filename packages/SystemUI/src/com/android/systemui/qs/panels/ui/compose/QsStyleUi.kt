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

import androidx.compose.runtime.compositionLocalOf

/** The Quick Settings style options as seen by the tile composables. */
data class QsStyleUi(
    val motorolaStyle: Boolean = false,
    val showLabels: Boolean = true,
    /** Tiles drawn inside a shared card: their own background is transparent. */
    val flatTiles: Boolean = false,
)

/** Provided by [PaginatedGridLayout]. Defaults to the stock look when nothing provides it. */
val LocalQsStyle = compositionLocalOf { QsStyleUi() }
