/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.utils.resize

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

/**
 * [queueGroupId]/[queueGroupTitle] stamp this item as part of a Queue Group (see
 * com.metrolist.music.queue.QueueEntry): callers that queue a whole album/playlist as one
 * batch pass the same freshly-generated id (see com.metrolist.music.queue.newQueueGroupId) and
 * title for every item in that batch. Left null (the default) for an ordinary, ungrouped item -
 * this is what every pre-existing single-song call site still gets unchanged.
 */
fun Song.toMediaItem(queueGroupId: String? = null, queueGroupTitle: String? = null) = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(song.id)
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata().copy(queueGroupId = queueGroupId, queueGroupTitle = queueGroupTitle))
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setSubtitle(orderedArtists.joinToString { it.name })
            .setArtist(orderedArtists.joinToString { it.name })
            .setArtworkUri(song.thumbnailUrl?.toUri())
            .setAlbumTitle(song.albumName)
            .setAlbumArtist(orderedArtists.firstOrNull()?.name)
            .setDisplayTitle(song.title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString("artwork_uri", song.thumbnailUrl)
            })
            .build()
    )
    .build()

fun SongItem.toMediaItem(queueGroupId: String? = null, queueGroupTitle: String? = null) = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata().copy(queueGroupId = queueGroupId, queueGroupTitle = queueGroupTitle))
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnail.resize(1080, 1080).toUri())
            .setAlbumTitle(album?.name)
            .setAlbumArtist(artists.firstOrNull()?.name)
            .setDisplayTitle(title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString("artwork_uri", thumbnail.resize(1080, 1080))
            })
            .build()
    )
    .build()

// Defaults preserve whatever this MediaMetadata already carries (e.g. a queue restored from
// PersistQueue may already have queueGroupId/queueGroupTitle set) instead of stamping every
// generic single-item call site (QueueMenu, queue restoration, etc.) back to ungrouped.
fun MediaMetadata.toMediaItem(
    queueGroupId: String? = this.queueGroupId,
    queueGroupTitle: String? = this.queueGroupTitle,
) = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(this.copy(queueGroupId = queueGroupId, queueGroupTitle = queueGroupTitle))
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnailUrl?.toUri())
            .setAlbumTitle(album?.title)
            .setAlbumArtist(artists.firstOrNull()?.name)
            .setDisplayTitle(title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                thumbnailUrl?.let { putString("artwork_uri", it) }
            })
            .build()
    )
    .build()
