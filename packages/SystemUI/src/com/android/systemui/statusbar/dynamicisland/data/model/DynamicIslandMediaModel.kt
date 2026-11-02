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

import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock

/** The media session shown by the Dynamic Island. */
data class DynamicIslandMediaModel(
    val packageName: String,
    val title: CharSequence?,
    val artist: CharSequence?,
    val art: Bitmap?,
    val isPlaying: Boolean,
    /** Playback position at [positionUpdateElapsedMs]. */
    val positionMs: Long,
    /** Track duration, or 0 when unknown (e.g. a live stream). */
    val durationMs: Long,
    /** [SystemClock.elapsedRealtime] when [positionMs] was captured. */
    val positionUpdateElapsedMs: Long,
    val speed: Float,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    /** Extra buttons of the app (like, shuffle, ...). */
    val customActions: List<PlaybackState.CustomAction>,
    /** Name of the output device, or null when the audio plays on this phone. */
    val outputName: CharSequence?,
    val controller: MediaController,
) {
    /** The playback position right now, extrapolated from the last update while playing. */
    fun currentPositionMs(): Long {
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        if (!isPlaying) return positionMs.coerceIn(0L, max)
        val elapsed = ((SystemClock.elapsedRealtime() - positionUpdateElapsedMs) * speed).toLong()
        return (positionMs + elapsed).coerceIn(0L, max)
    }

    /** Seeks to [fraction] (0..1) of the track. Does nothing when the duration is unknown. */
    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0) return
        controller.transportControls.seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
    }
}
