package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEntryTest {
    @Test
    fun `empty queue produces no entries`() {
        assertEquals(emptyList<QueueEntry>(), queueGroupEntries(emptyList()))
    }

    @Test
    fun `all null group ids produce one Single per item`() {
        val entries = queueGroupEntries(listOf(null, null, null))
        assertEquals(
            listOf(QueueEntry.Single(0), QueueEntry.Single(1), QueueEntry.Single(2)),
            entries,
        )
    }

    @Test
    fun `consecutive same group id forms one contiguous group`() {
        val entries = queueGroupEntries(listOf("A", "A", "A"))
        assertEquals(
            listOf(QueueEntry.Group("A", null, listOf(0, 1, 2))),
            entries,
        )
    }

    @Test
    fun `same group id reappearing after an interruption forms two separate groups`() {
        // A A X A A -> Group(A, [0,1]), Single(2), Group(A, [3,4]) - never merged across the gap.
        val entries = queueGroupEntries(listOf("A", "A", null, "A", "A"))
        assertEquals(
            listOf(
                QueueEntry.Group("A", null, listOf(0, 1)),
                QueueEntry.Single(2),
                QueueEntry.Group("A", null, listOf(3, 4)),
            ),
            entries,
        )
    }

    @Test
    fun `same group id separated by a different group id forms two separate groups`() {
        val entries = queueGroupEntries(listOf("A", "B", "A"))
        assertEquals(
            listOf(
                QueueEntry.Single(0),
                QueueEntry.Single(1),
                QueueEntry.Single(2),
            ),
            entries,
        )
        // Each run here has length 1, so all three collapse to Single per the singleton rule
        // (covered explicitly below); this test only asserts they are NOT merged into one group.
        assertTrue(entries.none { it is QueueEntry.Group })
    }

    @Test
    fun `a run of length one is a Single, not a one-item group`() {
        val entries = queueGroupEntries(listOf("A"))
        assertEquals(listOf(QueueEntry.Single(0)), entries)
    }

    @Test
    fun `mixed groups and singles preserve order and boundaries`() {
        // A1 A2 A3 X B1 B2 -> Group A, Single X, Group B
        val entries = queueGroupEntries(listOf("A", "A", "A", null, "B", "B"))
        assertEquals(
            listOf(
                QueueEntry.Group("A", null, listOf(0, 1, 2)),
                QueueEntry.Single(3),
                QueueEntry.Group("B", null, listOf(4, 5)),
            ),
            entries,
        )
    }

    @Test
    fun `group title is read from the first position of the run`() {
        val entries = queueGroupEntries(
            groupIds = listOf("A", "A"),
            groupTitles = listOf("Album A", "ignored - not the run start"),
        )
        assertEquals(
            listOf(QueueEntry.Group("A", "Album A", listOf(0, 1))),
            entries,
        )
    }

    @Test
    fun `flattenIndices reconstructs the original flat order`() {
        val groupIds = listOf("A", "A", null, "B", "B", "B", null)
        val entries = queueGroupEntries(groupIds)
        assertEquals(groupIds.indices.toList(), entries.flattenIndices())
    }

    @Test
    fun `entryContaining finds the right entry for every position`() {
        val groupIds = listOf("A", "A", null, "B", "B")
        val entries = queueGroupEntries(groupIds)

        assertEquals(QueueEntry.Group("A", null, listOf(0, 1)), entries.entryContaining(0))
        assertEquals(QueueEntry.Group("A", null, listOf(0, 1)), entries.entryContaining(1))
        assertEquals(QueueEntry.Single(2), entries.entryContaining(2))
        assertEquals(QueueEntry.Group("B", null, listOf(3, 4)), entries.entryContaining(3))
        assertEquals(QueueEntry.Group("B", null, listOf(3, 4)), entries.entryContaining(4))
        assertEquals(null, entries.entryContaining(5))
    }
}
