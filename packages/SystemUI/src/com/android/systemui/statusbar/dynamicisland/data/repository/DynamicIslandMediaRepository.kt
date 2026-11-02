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

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.dynamicisland.data.model.DynamicIslandMediaModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * The media session the Dynamic Island shows, read straight from [MediaSessionManager] so it works
 * with both the legacy and the compose media pipelines.
 *
 * The session is shown while it plays, and kept for [HOLD_AFTER_PAUSE_MS] after it pauses so the
 * island does not vanish under the user's finger. It follows the Dynamic Island settings (master
 * switch, media live updates, blocked apps).
 */
@SysUISingleton
class DynamicIslandMediaRepository
@Inject
constructor(
    @Main private val context: Context,
    @Application scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    settingsRepository: DynamicIslandSettingsRepository,
) {
    private class Snapshot(
        val controller: MediaController,
        val metadata: MediaMetadata?,
        val playbackState: PlaybackState?,
    )

    // Only touched from the flow below, which is collected on one coroutine at a time.
    private var heldPackage: String? = null
    private var cachedArtKey: String? = null
    private var cachedArt: Bitmap? = null

    val media: StateFlow<DynamicIslandMediaModel?> =
        combine(snapshots().map { it?.let { s -> buildModel(s) } }, settingsRepository.settings) {
                model,
                settings ->
                model?.takeIf {
                    settings.enabled &&
                        settings.liveMedia &&
                        settings.isPackageAllowed(it.packageName)
                }
            }
            .transformLatest { model ->
                when {
                    model == null -> emit(null)
                    model.isPlaying -> {
                        heldPackage = model.packageName
                        emit(model)
                    }
                    // Paused: keep it for a while, but only if it was playing before.
                    model.packageName == heldPackage -> {
                        emit(model)
                        delay(HOLD_AFTER_PAUSE_MS)
                        heldPackage = null
                        emit(null)
                    }
                    else -> emit(null)
                }
            }
            .flowOn(bgDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    private fun snapshots(): Flow<Snapshot?> =
        callbackFlow {
                val manager = context.getSystemService(MediaSessionManager::class.java)
                if (manager == null) {
                    trySend(null)
                    awaitClose {}
                } else {
                    val watcher = SessionWatcher(manager) { trySend(it) }
                    watcher.start()
                    awaitClose { watcher.stop() }
                }
            }
            .conflate()

    /** Follows the active media sessions and reports the one that should be shown. */
    private inner class SessionWatcher(
        private val manager: MediaSessionManager,
        private val onChange: (Snapshot?) -> Unit,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val registered = HashMap<MediaSession.Token, MediaController>()
        private var controllers: List<MediaController> = emptyList()

        private val callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

                override fun onSessionDestroyed() =
                    update(manager.getActiveSessionsForUser(null as ComponentName?, UserHandle.ALL))
            }

        private val listener =
            MediaSessionManager.OnActiveSessionsChangedListener { list ->
                update(list ?: emptyList())
            }

        fun start() {
            manager.addOnActiveSessionsChangedListener(
                null as ComponentName?,
                UserHandle.ALL,
                { handler.post(it) },
                listener,
            )
            update(manager.getActiveSessionsForUser(null as ComponentName?, UserHandle.ALL))
        }

        fun stop() {
            manager.removeOnActiveSessionsChangedListener(listener)
            synchronized(registered) {
                registered.values.forEach { it.unregisterCallback(callback) }
                registered.clear()
            }
        }

        private fun update(list: List<MediaController>) {
            synchronized(registered) {
                val live = list.associateBy { it.sessionToken }
                registered
                    .filterKeys { it !in live }
                    .forEach { (token, controller) ->
                        controller.unregisterCallback(callback)
                        registered.remove(token)
                    }
                live.forEach { (token, controller) ->
                    if (token !in registered) {
                        controller.registerCallback(callback, handler)
                        registered[token] = controller
                    }
                }
                controllers = list
            }
            publish()
        }

        private fun publish() {
            val primary =
                synchronized(registered) {
                    controllers.firstOrNull { it.playbackState?.isActivelyPlaying() == true }
                        ?: controllers.firstOrNull { it.metadata != null }
                }
            onChange(primary?.let { Snapshot(it, it.metadata, it.playbackState) })
        }
    }

    private fun PlaybackState.isActivelyPlaying() =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun buildModel(snapshot: Snapshot): DynamicIslandMediaModel? {
        val metadata = snapshot.metadata ?: return null
        val state = snapshot.playbackState ?: return null
        val title =
            metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_TITLE)
        val artist =
            metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
        return DynamicIslandMediaModel(
            packageName = snapshot.controller.packageName,
            title = title,
            artist = artist,
            art = loadArt(snapshot.controller.packageName, metadata),
            isPlaying = state.isActivelyPlaying(),
            positionMs = max(state.position, 0L),
            durationMs = max(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION), 0L),
            positionUpdateElapsedMs = state.lastPositionUpdateTime,
            speed = state.playbackSpeed,
            canSkipPrevious = state.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = state.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            customActions = state.customActions.take(MAX_CUSTOM_ACTIONS),
            outputName = outputNameOrNull(),
            controller = snapshot.controller,
        )
    }

    private fun loadArt(packageName: String, metadata: MediaMetadata): Bitmap? {
        val bitmap =
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) return bitmap

        val uri =
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                ?: return null
        val key = "$packageName|$uri"
        if (key == cachedArtKey) return cachedArt
        val decoded =
            try {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val biggest = max(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(max(1, biggest / MAX_ART_SIZE_PX))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load album art from $uri", e)
                null
            }
        cachedArtKey = key
        cachedArt = decoded
        return decoded
    }

    /** The name of the audio output, or null when it is the phone itself (or unknown). */
    private fun outputNameOrNull(): CharSequence? =
        try {
            val route =
                MediaRouter2.getInstance(context).systemController.selectedRoutes.firstOrNull()
            if (route == null || route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) null
            else route.name
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val TAG = "DynamicIslandMedia"
        const val HOLD_AFTER_PAUSE_MS = 10_000L
        const val MAX_CUSTOM_ACTIONS = 2
        const val MAX_ART_SIZE_PX = 640
    }
}
