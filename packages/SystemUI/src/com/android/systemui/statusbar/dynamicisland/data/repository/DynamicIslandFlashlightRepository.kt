/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.dynamicisland.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.policy.FlashlightController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the flashlight is on and the Dynamic Island should show it. Follows the Dynamic Island
 * settings (master switch and the flashlight live update option).
 */
@SysUISingleton
class DynamicIslandFlashlightRepository
@Inject
constructor(
    private val controller: FlashlightController,
    @Application scope: CoroutineScope,
    settingsRepository: DynamicIslandSettingsRepository,
) {
    val isOn: StateFlow<Boolean> =
        combine(flashlightState(), settingsRepository.settings) { on, settings ->
                on && settings.enabled && settings.liveFlashlight
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    /** Turns the flashlight off, used by the island's tap action. */
    fun turnOff() {
        controller.setFlashlight(false)
    }

    private fun flashlightState(): Flow<Boolean> =
        callbackFlow {
            val listener =
                object : FlashlightController.FlashlightListener {
                    override fun onFlashlightChanged(enabled: Boolean) {
                        trySend(enabled)
                    }

                    override fun onFlashlightError() {
                        trySend(false)
                    }

                    override fun onFlashlightAvailabilityChanged(available: Boolean) {
                        trySend(available && controller.isEnabled())
                    }
                }
            controller.addCallback(listener)
            trySend(controller.isAvailable() && controller.isEnabled())
            awaitClose { controller.removeCallback(listener) }
        }
}
