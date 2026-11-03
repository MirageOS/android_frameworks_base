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

package com.android.systemui.qs.panels.data.repository

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

/** The Quick Settings style options written by Settings (Personalise > Quick Settings). */
data class QsStyleSettingsModel(
    /** Use the Motorola style layout instead of the default one. */
    val motorolaStyle: Boolean = false,
    /** Show the label of every tile. */
    val showLabels: Boolean = true,
)

/**
 * Keys of the QS style options in [Settings.System]. Keep them in sync with QsStyleSettings.java
 * in packages/apps/Settings.
 */
object QsStyleSettingsKeys {
    const val STYLE_MOTOROLA = "qs_style_motorola"
    const val SHOW_LABELS = "qs_show_labels"
}

/** Emits a new [QsStyleSettingsModel] whenever one of the QS style options changes. */
@SysUISingleton
class QsStyleRepository
@Inject
constructor(
    @Main private val context: Context,
    @Application scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {

    val settings: StateFlow<QsStyleSettingsModel> =
        changes()
            .onStart { emit(Unit) }
            .map { read() }
            .distinctUntilChanged()
            .flowOn(bgDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), QsStyleSettingsModel())

    private fun changes() =
        callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                val resolver = context.contentResolver
                listOf(QsStyleSettingsKeys.STYLE_MOTOROLA, QsStyleSettingsKeys.SHOW_LABELS)
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

    private fun read() =
        QsStyleSettingsModel(
            motorolaStyle = flag(QsStyleSettingsKeys.STYLE_MOTOROLA, def = false),
            showLabels = flag(QsStyleSettingsKeys.SHOW_LABELS, def = true),
        )

    private fun flag(key: String, def: Boolean): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver,
            key,
            if (def) 1 else 0,
            UserHandle.USER_CURRENT,
        ) != 0
}
