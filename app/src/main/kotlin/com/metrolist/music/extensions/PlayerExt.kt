/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import com.metrolist.music.models.MediaMetadata
import java.util.ArrayDeque

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode =
        when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        return emptyList()
    }
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    val currentMediaItemIndex: Int = currentMediaItemIndex
    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    val shuffleModeEnabled = shuffleModeEnabled
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex =
                timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(
                firstMediaItemIndex,
                REPEAT_MODE_OFF,
                shuffleModeEnabled
            )
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}

/**
 * The queueGroupId of every item in the queue, in raw window-index order - i.e. exactly the
 * shape com.metrolist.music.queue.buildGroupAwareShuffleOrder()/buildGroupAwarePooledShuffleOrder()
 * expect for their `groupIds` parameter.
 */
fun Player.queueGroupIds(): List<String?> = List(mediaItemCount) { getMediaItemAt(it).metadata?.queueGroupId }

/** The queueGroupTitle of every item in the queue, in raw window-index order. See [queueGroupIds]. */
fun Player.queueGroupTitles(): List<String?> = List(mediaItemCount) { getMediaItemAt(it).metadata?.queueGroupTitle }

/**
 * The full current shuffle traversal order as raw window indices - the same sequence
 * [getQueueWindows] returns, but as plain Ints instead of [Timeline.Window] objects. Meaningful
 * only while shuffleModeEnabled. Used to snapshot "the current shuffle order" before mutating the
 * timeline (e.g. before MusicService.playNext() inserts new items), so a pure function
 * (com.metrolist.music.queue.buildShuffleOrderForPlayNext) can compute the post-mutation order
 * without touching ExoPlayer itself.
 */
fun Player.shuffleOrderIndices(): List<Int> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val currentMediaItemIndex: Int = currentMediaItemIndex

    val before = mutableListOf<Int>()
    var idx = currentMediaItemIndex
    while (true) {
        idx = timeline.getPreviousWindowIndex(idx, REPEAT_MODE_OFF, shuffleModeEnabled)
        if (idx == C.INDEX_UNSET) break
        before.add(idx)
    }
    before.reverse()

    val after = mutableListOf<Int>()
    idx = currentMediaItemIndex
    while (true) {
        idx = timeline.getNextWindowIndex(idx, REPEAT_MODE_OFF, shuffleModeEnabled)
        if (idx == C.INDEX_UNSET) break
        after.add(idx)
    }

    return before + currentMediaItemIndex + after
}

fun Player.getCurrentQueueIndex(): Int {
    if (currentTimeline.isEmpty) {
        return -1
    }
    var index = 0
    var currentMediaItemIndex = currentMediaItemIndex
    while (currentMediaItemIndex != C.INDEX_UNSET) {
        currentMediaItemIndex = currentTimeline.getPreviousWindowIndex(
            currentMediaItemIndex,
            REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (currentMediaItemIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

val Player.currentMetadata: MediaMetadata?
    get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
    get() =
        object : AbstractList<MediaItem>() {
            override val size: Int
                get() = mediaItemCount

            override fun get(index: Int): MediaItem = getMediaItemAt(index)
        }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}

fun Player.setOffloadEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences
                .Builder()
                .setAudioOffloadMode(
                    if (enabled) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
        ).build()
}
