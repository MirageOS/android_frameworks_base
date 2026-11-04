/*
 * Copyright (C) 2026 The MirageOS Project
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

package com.android.systemui.statusbar.pipeline.wifi.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The Wi-Fi generation of the connected network (the 6 in "Wi-Fi 6") and whether the user wants it
 * shown in the status bar.
 *
 * The generation comes from [WifiInfo.getWifiStandard] in the transport info of the Wi-Fi network.
 * That field is not one of the location-sensitive ones the system redacts, so no extra permission
 * is needed. Both flows only listen while somebody collects them.
 */
@SysUISingleton
class WifiStandardRepository
@Inject
constructor(@Application private val context: Context, @Background scope: CoroutineScope) {

    /** The [ScanResult] WIFI_STANDARD_* value of the connected network, or unknown (0). */
    val wifiStandard: StateFlow<Int> =
        conflatedCallbackFlow {
                val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                if (connectivityManager == null) {
                    trySend(ScanResult.WIFI_STANDARD_UNKNOWN)
                    awaitClose {}
                    return@conflatedCallbackFlow
                }
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        private var tracked: Network? = null

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            tracked = network
                            val info = capabilities.transportInfo as? WifiInfo
                            trySend(info?.wifiStandard ?: ScanResult.WIFI_STANDARD_UNKNOWN)
                        }

                        override fun onLost(network: Network) {
                            if (network == tracked) {
                                tracked = null
                                trySend(ScanResult.WIFI_STANDARD_UNKNOWN)
                            }
                        }
                    }
                val request =
                    NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build()
                connectivityManager.registerNetworkCallback(request, callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), ScanResult.WIFI_STANDARD_UNKNOWN)

    /** Whether the user left "Wi-Fi standard" on in Settings > Personalise. On by default. */
    val isEnabled: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(readEnabled())
                        }
                    }
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(KEY_WIFI_STANDARD),
                    false,
                    observer,
                    UserHandle.USER_ALL,
                )
                trySend(readEnabled())
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(), true)

    private fun readEnabled(): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver,
            KEY_WIFI_STANDARD,
            1,
            UserHandle.USER_CURRENT,
        ) != 0

    companion object {
        /** Settings.System, 1 = show. Written by Settings, keep the name in sync. */
        const val KEY_WIFI_STANDARD = "status_bar_wifi_standard"

        /** "4", "5", "6" or "7"; null for legacy and unknown networks, which show nothing. */
        fun labelOf(standard: Int): String? =
            when (standard) {
                ScanResult.WIFI_STANDARD_11N -> "4"
                ScanResult.WIFI_STANDARD_11AC -> "5"
                ScanResult.WIFI_STANDARD_11AX -> "6"
                ScanResult.WIFI_STANDARD_11BE -> "7"
                else -> null
            }
    }
}
