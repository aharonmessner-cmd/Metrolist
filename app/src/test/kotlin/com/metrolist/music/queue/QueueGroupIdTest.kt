package com.metrolist.music.queue

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGroupIdTest {
    @Test
    fun `generated ids are non-blank`() {
        assertTrue(newQueueGroupId().isNotBlank())
    }

    @Test
    fun `two calls never produce the same id`() {
        // This is what guarantees re-adding the same album/playlist creates a separate group
        // instead of merging into a previous one with the same id.
        assertNotEquals(newQueueGroupId(), newQueueGroupId())
    }

    @Test
    fun `many calls are all unique`() {
        val ids = List(1_000) { newQueueGroupId() }
        assertTrue(ids.toSet().size == ids.size)
    }
}
