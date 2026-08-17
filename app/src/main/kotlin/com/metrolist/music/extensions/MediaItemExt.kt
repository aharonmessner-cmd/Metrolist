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
import com.metrolist.music.queue.newQueueGroupId
import com.metrolist.music.ui.utils.resize

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem() = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(song.id)
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata())
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

fun SongItem.toMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(id)
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata())
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

/**
 * Stamps a fresh, shared Queue Group (see com.metrolist.music.queue.QueueEntry) onto every item
 * in this list: one new id (com.metrolist.music.queue.newQueueGroupId - never any permanent
 * album/playlist id) and [groupTitle], applied to a copy of each item's existing tag via
 * [MediaItem.buildUpon]. Everything else about each item (media id, URI, media3 metadata,
 * artwork, ...) is left untouched.
 *
 * This is the "as Group" counterpart to building a normal, flat item list: build the exact same
 * MediaItems you would for a normal Play/Play Next/Add to Queue (`songs.map { it.toMediaItem() }`
 * etc.), then call `.asQueueGroup(title)` on the result only when the caller explicitly chose a
 * grouped action. An empty list is returned unchanged (no group id is wasted on nothing).
 * A single-item list is still stamped - see com.metrolist.music.queue.queueGroupEntries, which
 * treats a run of exactly one grouped item as an ordinary ungrouped [QueueEntry.Single] anyway,
 * so this is harmless and keeps the batch/id-generation logic in exactly one place here.
 */
fun List<MediaItem>.asQueueGroup(groupTitle: String): List<MediaItem> {
    if (isEmpty()) return this
    val groupId = newQueueGroupId()
    return map { item ->
        val tag = item.metadata ?: return@map item
        item.buildUpon()
            .setTag(tag.copy(queueGroupId = groupId, queueGroupTitle = groupTitle))
            .build()
    }
}

/**
 * Stamps a precomputed, position-parallel list of (queueGroupId, queueGroupTitle) pairs onto
 * this list of MediaItems - one pair per item, in order. Unlike [asQueueGroup] (which mints ONE
 * fresh group id shared by every item), this is for the case where per-item group ids/titles were
 * already computed elsewhere - e.g. com.metrolist.music.queue.reifyPersistentPlaylistGroups
 * turning a playlist's persisted groups into fresh runtime queue groups (one id per persisted
 * group, `null to null` for ungrouped songs), so a playlist with two separate persisted groups
 * queues as two separate runtime groups instead of being flattened into one.
 *
 * [groupIdsAndTitles] must be the same size as this list.
 */
fun List<MediaItem>.withQueueGroups(groupIdsAndTitles: List<Pair<String?, String?>>): List<MediaItem> {
    require(size == groupIdsAndTitles.size) {
        "withQueueGroups requires one (queueGroupId, queueGroupTitle) pair per item: " +
            "$size items but ${groupIdsAndTitles.size} pairs"
    }
    return mapIndexed { index, item ->
        val (groupId, groupTitle) = groupIdsAndTitles[index]
        val tag = item.metadata ?: return@mapIndexed item
        item.buildUpon()
            .setTag(tag.copy(queueGroupId = groupId, queueGroupTitle = groupTitle))
            .build()
    }
}

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
