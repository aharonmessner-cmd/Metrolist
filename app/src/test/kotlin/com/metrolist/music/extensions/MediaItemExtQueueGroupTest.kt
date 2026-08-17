package com.metrolist.music.extensions

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Verifies Queue Groups metadata stamping is strictly opt-in (Part 3.5): normal
 * Song/SongItem/MediaMetadata.toMediaItem() calls are always ungrouped, and grouping is only
 * ever applied afterward, explicitly, via List<MediaItem>.asQueueGroup() - the same MediaItems a
 * normal (non-grouped) action would build, with group metadata stamped on top. This is what lets
 * "Play"/"Play Next"/"Add to Queue" stay completely flat while "...as Group" opts in.
 */
class MediaItemExtQueueGroupTest {
    private fun song(id: String) = Song(song = SongEntity(id = id, title = "Song $id"), artists = emptyList())

    private fun songItem(id: String) = SongItem(id = id, title = "Song $id", artists = emptyList(), thumbnail = "")

    private fun mediaMetadata(id: String, queueGroupId: String? = null, queueGroupTitle: String? = null) =
        MediaMetadata(
            id = id,
            title = "Song $id",
            artists = emptyList(),
            duration = 180,
            queueGroupId = queueGroupId,
            queueGroupTitle = queueGroupTitle,
        )

    @Test
    fun `Song toMediaItem is always ungrouped - no group parameters exist`() {
        val item = song("s1").toMediaItem()
        assertNull(item.metadata?.queueGroupId)
        assertNull(item.metadata?.queueGroupTitle)
    }

    @Test
    fun `SongItem toMediaItem is always ungrouped - no group parameters exist`() {
        val item = songItem("s1").toMediaItem()
        assertNull(item.metadata?.queueGroupId)
        assertNull(item.metadata?.queueGroupTitle)
    }

    @Test
    fun `asQueueGroup stamps a shared fresh group id and the given title onto every item`() {
        val items = listOf(song("s1"), song("s2"), song("s3")).map { it.toMediaItem() }
        val grouped = items.asQueueGroup("Album X")

        val ids = grouped.map { it.metadata?.queueGroupId }
        assertEquals(3, ids.size)
        assertEquals(1, ids.toSet().size) // all share the same id
        assertNotNull(ids.first())
        assertEquals(listOf("Album X", "Album X", "Album X"), grouped.map { it.metadata?.queueGroupTitle })
    }

    @Test
    fun `asQueueGroup does not mutate the original ungrouped items`() {
        val items = listOf(song("s1"), song("s2")).map { it.toMediaItem() }
        items.asQueueGroup("Album X")
        assertNull(items[0].metadata?.queueGroupId)
        assertNull(items[1].metadata?.queueGroupId)
    }

    @Test
    fun `asQueueGroup called twice on equivalent batches produces two different group ids`() {
        val batch1 = listOf(song("s1"), song("s2")).map { it.toMediaItem() }.asQueueGroup("Album X")
        val batch2 = listOf(song("s1"), song("s2")).map { it.toMediaItem() }.asQueueGroup("Album X")
        assertNotEquals(batch1[0].metadata?.queueGroupId, batch2[0].metadata?.queueGroupId)
    }

    @Test
    fun `asQueueGroup on an empty list is a no-op`() {
        val empty = emptyList<androidx.media3.common.MediaItem>()
        assertSame(empty, empty.asQueueGroup("Album X"))
    }

    @Test
    fun `asQueueGroup preserves everything else about each item`() {
        val original = song("s1").toMediaItem()
        val grouped = listOf(original).asQueueGroup("Album X").single()
        assertEquals(original.mediaId, grouped.mediaId)
        assertEquals(original.metadata?.title, grouped.metadata?.title)
    }

    @Test
    fun `MediaMetadata toMediaItem with no args preserves an already-grouped receiver`() {
        // This is the queue-restoration path: PersistQueue's List<MediaMetadata> already carries
        // whatever group info was persisted, and .toMediaItem() is called with no override.
        val item = mediaMetadata("s1", queueGroupId = "group-3", queueGroupTitle = "Album Z").toMediaItem()
        assertEquals("group-3", item.metadata?.queueGroupId)
        assertEquals("Album Z", item.metadata?.queueGroupTitle)
    }

    @Test
    fun `MediaMetadata toMediaItem with no args stays ungrouped for an ungrouped receiver`() {
        val item = mediaMetadata("s1").toMediaItem()
        assertNull(item.metadata?.queueGroupId)
        assertNull(item.metadata?.queueGroupTitle)
    }

    @Test
    fun `MediaMetadata toMediaItem explicit override replaces the receiver's group`() {
        val item = mediaMetadata("s1", queueGroupId = "old-group", queueGroupTitle = "Old Album")
            .toMediaItem(queueGroupId = "new-group", queueGroupTitle = "New Album")
        assertEquals("new-group", item.metadata?.queueGroupId)
        assertEquals("New Album", item.metadata?.queueGroupTitle)
    }
}
