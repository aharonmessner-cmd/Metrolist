package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class QueueGroupReorderTest {
    @Test
    fun `moving a group block relocates it as a unit without splitting it`() {
        // A1 A2 A3 X -> dragging Album A below X -> X A1 A2 A3
        val groupIds = listOf("A", "A", "A", null)
        val entries = queueGroupEntries(groupIds)
        // entries = [Group(A,[0,1,2]), Single(3)]; move entry 0 (the group) to position 1.
        val moved = moveQueueGroupBlock(entries, fromEntryIndex = 0, toEntryIndex = 1)

        assertEquals(
            listOf(QueueEntry.Single(3), QueueEntry.Group("A", null, listOf(0, 1, 2))),
            moved,
        )
        assertEquals(listOf(3, 0, 1, 2), moved.flattenIndices())
    }

    @Test
    fun `moving an ungrouped single reorders without affecting groups`() {
        // A1 A2 X B1 B2 -> move X (entry index 1) to the end -> A1 A2 B1 B2 X
        val groupIds = listOf("A", "A", null, "B", "B")
        val entries = queueGroupEntries(groupIds)
        val moved = moveQueueGroupBlock(entries, fromEntryIndex = 1, toEntryIndex = 2)

        assertEquals(
            listOf(
                QueueEntry.Group("A", null, listOf(0, 1)),
                QueueEntry.Group("B", null, listOf(3, 4)),
                QueueEntry.Single(2),
            ),
            moved,
        )
        assertEquals(listOf(0, 1, 3, 4, 2), moved.flattenIndices())
    }

    @Test
    fun `moving a group never splits its member indices apart, across all from-to combinations`() {
        val groupIds = listOf("A", "A", "A", null, "B", "B", null)
        val entries = queueGroupEntries(groupIds)
        val groupEntryIndex = entries.indexOfFirst { it is QueueEntry.Group && it.groupId == "A" }

        for (target in entries.indices) {
            val moved = moveQueueGroupBlock(entries, groupEntryIndex, target)
            val flat = moved.flattenIndices()
            val aPositions = listOf(0, 1, 2).map { flat.indexOf(it) }.sorted()
            // Group A's three original positions must still be contiguous and in order 0,1,2.
            assertEquals((aPositions.first()..aPositions.last()).toList(), aPositions)
            assertEquals(listOf(0, 1, 2), aPositions.map { flat[it] })
        }
    }

    @Test
    fun `no-op moves return the input unchanged`() {
        val entries = queueGroupEntries(listOf("A", "A", null))
        assertSame(entries, moveQueueGroupBlock(entries, 0, 0))
    }

    @Test
    fun `out of range move indices return the input unchanged`() {
        val entries = queueGroupEntries(listOf("A", "A", null))
        assertSame(entries, moveQueueGroupBlock(entries, -1, 1))
        assertSame(entries, moveQueueGroupBlock(entries, 0, 99))
    }

    @Test
    fun `empty entries list is a no-op`() {
        assertEquals(emptyList<QueueEntry>(), moveQueueGroupBlock(emptyList(), 0, 0))
    }

    @Test
    fun `dragging a song within a group clamps the target to the group's own bounds`() {
        // Group A occupies flat positions 1..3 (song at flat index 2 is being dragged).
        val groupIds = listOf(null, "A", "A", "A", null, null)
        val entries = queueGroupEntries(groupIds)

        assertEquals(1, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 2, targetFlatIndex = 0))
        assertEquals(3, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 2, targetFlatIndex = 5))
        assertEquals(1, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 2, targetFlatIndex = 1))
        assertEquals(3, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 2, targetFlatIndex = 3))
    }

    @Test
    fun `dragging an ungrouped song is never clamped`() {
        val groupIds = listOf(null, "A", "A", "A", null, null)
        val entries = queueGroupEntries(groupIds)

        assertEquals(0, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 0, targetFlatIndex = 0))
        assertEquals(5, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 0, targetFlatIndex = 5))
        assertEquals(4, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 4, targetFlatIndex = 4))
    }

    @Test
    fun `dragging within a one-item group run is unclamped because it is represented as a Single`() {
        val groupIds = listOf("A", null, null)
        val entries = queueGroupEntries(groupIds)

        assertEquals(2, clampReorderTargetWithinGroup(entries, draggedFlatIndex = 0, targetFlatIndex = 2))
    }
}
