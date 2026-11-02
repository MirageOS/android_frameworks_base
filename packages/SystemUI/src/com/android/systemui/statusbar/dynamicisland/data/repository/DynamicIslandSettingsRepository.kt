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

package com.android.systemui.statusbar.dynamicisland.data.repository

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandLayoutSettings
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandSettingsKeys
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandSettingsModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the Dynamic Island options written by Settings and emits a new [DynamicIslandSettingsModel]
 * whenever any of them changes.
 */
@SysUISingleton
class DynamicIslandSettingsRepository
@Inject
constructor(
    @Main private val context: Context,
    @Application scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {

    val settings: StateFlow<DynamicIslandSettingsModel> =
        changes()
            .onStart { emit(Unit) }
            .map { read() }
            .distinctUntilChanged()
            .flowOn(bgDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), DynamicIslandSettingsModel.Disabled)

    private fun changes() =
        callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                val resolver = context.contentResolver
                (DynamicIslandSettingsKeys.BOOLEAN_KEYS +
                        DynamicIslandSettingsKeys.INT_KEYS +
                        DynamicIslandSettingsKeys.BLOCKED_PACKAGES)
                    .forEach { key ->
                        resolver.registerContentObserver(
                            Settings.System.getUriFor(key),
                            /* notifyForDescendants= */ false,
                            observer,
                            UserHandle.USER_ALL,
                        )
                    }
                awaitClose { resolver.unregisterContentObserver(observer) }
            }
            .conflate()

    private fun read(): DynamicIslandSettingsModel =
        DynamicIslandSettingsModel(
            enabled = flag(DynamicIslandSettingsKeys.ENABLED),
            expandOnTap = flag(DynamicIslandSettingsKeys.EXPAND_ON_TAP),
            liveRecorder = flag(DynamicIslandSettingsKeys.LIVE_RECORDER),
            liveScreenRecorder = flag(DynamicIslandSettingsKeys.LIVE_SCREEN_RECORDER),
            liveFlashlight = flag(DynamicIslandSettingsKeys.LIVE_FLASHLIGHT),
            liveCalls = flag(DynamicIslandSettingsKeys.LIVE_CALLS),
            liveMedia = flag(DynamicIslandSettingsKeys.LIVE_MEDIA),
            liveAlarms = flag(DynamicIslandSettingsKeys.LIVE_ALARMS),
            blockedPackages = blockedPackages(),
            layout = layout(),
        )

    private fun layout(): DynamicIslandLayoutSettings =
        DynamicIslandLayoutSettings(
            autoPosition = flag(DynamicIslandSettingsKeys.AUTO_POSITION),
            positionXDp =
                int(
                    DynamicIslandSettingsKeys.POSITION_X,
                    DynamicIslandLayoutSettings.DEFAULT_POSITION_X,
                    DynamicIslandLayoutSettings.POSITION_X_RANGE,
                ),
            positionYDp =
                int(
                    DynamicIslandSettingsKeys.POSITION_Y,
                    DynamicIslandLayoutSettings.DEFAULT_POSITION_Y,
                    DynamicIslandLayoutSettings.POSITION_Y_RANGE,
                ),
            sizePercent =
                int(
                    DynamicIslandSettingsKeys.SIZE_PERCENT,
                    DynamicIslandLayoutSettings.DEFAULT_SIZE_PERCENT,
                    DynamicIslandLayoutSettings.SIZE_PERCENT_RANGE,
                ),
            cameraGapDp =
                int(
                    DynamicIslandSettingsKeys.CAMERA_GAP,
                    DynamicIslandLayoutSettings.DEFAULT_CAMERA_GAP,
                    DynamicIslandLayoutSettings.CAMERA_GAP_RANGE,
                ),
        )

    /** An integer option, kept inside [range] so a bad value can never break the layout. */
    private fun int(key: String, def: Int, range: IntRange): Int =
        Settings.System.getIntForUser(
                context.contentResolver,
                key,
                def,
                UserHandle.USER_CURRENT,
            )
            .coerceIn(range)

    /** Every option is enabled unless Settings explicitly wrote 0. */
    private fun flag(key: String): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver,
            key,
            /* def= */ 1,
            UserHandle.USER_CURRENT,
        ) != 0

    private fun blockedPackages(): Set<String> =
        Settings.System.getStringForUser(
                context.contentResolver,
                DynamicIslandSettingsKeys.BLOCKED_PACKAGES,
                UserHandle.USER_CURRENT,
            )
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
}
