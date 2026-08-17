/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_song_map",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistSongMap(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val playlistId: String,
    @ColumnInfo(index = true) val songId: String,
    val position: Int = 0,
    val setVideoId: String? = null,
    // Persistent playlist grouping ("Add to Playlist as Group"): when non-null, this entry is
    // part of a group of songs that were added to the playlist together (e.g. a whole album).
    // Consecutive rows (by position) sharing the same playlistGroupId form one persisted group.
    // This is intentionally a separate id space from MediaMetadata.queueGroupId - a *runtime*,
    // per-queue-instance id minted fresh every time a stored group is loaded into the playback
    // queue (see com.metrolist.music.queue.reifyPersistentPlaylistGroups). Null (the default)
    // means an ordinary playlist entry, matching all pre-existing playlist rows.
    val playlistGroupId: String? = null,
    val playlistGroupTitle: String? = null,
)
