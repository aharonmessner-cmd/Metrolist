package com.metrolist.music.extensions

import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the Queue Groups metadata-stamping added to Song/SongItem/MediaMetadata.toMediaItem():
 * a batch caller passes the same queueGroupId/queueGroupTitle to every item, an ungrouped caller
 * passes nothing and gets a null-tagged (ordinary) item, and MediaMetadata.toMediaItem() (used by
 * queue restoration and other generic single-item call sites) never clobbers group info that's
 * already on the receiver when called with no explicit override.
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
    fun `Song toMediaItem with no args stays ungrouped`() {
        val item = song("s1").toMediaItem()
        assertNull(item.metadata?.queueGroupId)
        assertNull(item.metadata?.queueGroupTitle)
    }

    @Test
    fun `Song toMediaItem stamps the given group onto the tag`() {
        val item = song("s1").toMediaItem("group-1", "Album X")
        assertEquals("group-1", item.metadata?.queueGroupId)
        assertEquals("Album X", item.metadata?.queueGroupTitle)
    }

    @Test
    fun `a batch of Songs all share the same group id and title`() {
        val groupId = "group-1"
        val items = listOf(song("s1"), song("s2"), song("s3")).map { it.toMediaItem(groupId, "Album X") }

        assertEquals(listOf("group-1", "group-1", "group-1"), items.map { it.metadata?.queueGroupId })
        assertEquals(listOf("Album X", "Album X", "Album X"), items.map { it.metadata?.queueGroupTitle })
    }

    @Test
    fun `SongItem toMediaItem with no args stays ungrouped`() {
        val item = songItem("s1").toMediaItem()
        assertNull(item.metadata?.queueGroupId)
        assertNull(item.metadata?.queueGroupTitle)
    }

    @Test
    fun `SongItem toMediaItem stamps the given group onto the tag`() {
        val item = songItem("s1").toMediaItem("group-2", "Playlist Y")
        assertEquals("group-2", item.metadata?.queueGroupId)
        assertEquals("Playlist Y", item.metadata?.queueGroupTitle)
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
