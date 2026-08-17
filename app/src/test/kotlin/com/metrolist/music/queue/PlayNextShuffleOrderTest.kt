package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for [buildShuffleOrderForPlayNext], the pure core of MusicService.playNext()'s
 * shuffle-order splice: insert a new contiguous batch right after wherever "current" currently
 * sits in the existing shuffle sequence, without disturbing anything else's relative order.
 */
class PlayNextShuffleOrderTest {
    private fun isPermutationOf(result: IntArray, totalCount: Int) {
        assertEquals(totalCount, result.size)
        assertEquals((0 until totalCount).toSet(), result.toSet())
    }

    @Test
    fun `empty oldOrder throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildShuffleOrderForPlayNext(oldOrder = emptyList(), currentIndex = 0, insertIndex = 1, newItemsCount = 1)
        }
    }

    @Test
    fun `non-permutation oldOrder throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildShuffleOrderForPlayNext(oldOrder = listOf(0, 0, 2), currentIndex = 0, insertIndex = 1, newItemsCount = 1)
        }
    }

    @Test
    fun `currentIndex missing from oldOrder throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildShuffleOrderForPlayNext(oldOrder = listOf(0, 1, 2), currentIndex = 5, insertIndex = 1, newItemsCount = 1)
        }
    }

    @Test
    fun `zero new items returns the old order unchanged`() {
        val oldOrder = listOf(2, 0, 1, 3)
        val result = buildShuffleOrderForPlayNext(oldOrder, currentIndex = 1, insertIndex = 2, newItemsCount = 0)
        assertEquals(oldOrder, result.toList())
    }

    @Test
    fun `worked example - current first in old order`() {
        // oldOrder = [2,0,1,3], current=2 (already first). Insert 2 items at index 3.
        val result = buildShuffleOrderForPlayNext(
            oldOrder = listOf(2, 0, 1, 3), currentIndex = 2, insertIndex = 3, newItemsCount = 2,
        )
        assertEquals(listOf(2, 3, 4, 0, 1, 5), result.toList())
    }

    @Test
    fun `worked example - current mid-sequence, not first`() {
        // oldOrder = [1,3,0,2], current=0 is at position 2 (mid-playback). Insert 1 item at index 1.
        val result = buildShuffleOrderForPlayNext(
            oldOrder = listOf(1, 3, 0, 2), currentIndex = 0, insertIndex = 1, newItemsCount = 1,
        )
        assertEquals(listOf(2, 4, 0, 1, 3), result.toList())
    }

    @Test
    fun `new block is always contiguous, in insertion order, immediately after current`() {
        repeat(200) { seed ->
            val random = Random(seed.toLong())
            val oldSize = 2 + random.nextInt(10)
            val oldOrder = (0 until oldSize).shuffled(random)
            val currentIndex = oldOrder[random.nextInt(oldSize)]
            val insertIndex = currentIndex + 1
            val newItemsCount = 1 + random.nextInt(4)

            val result = buildShuffleOrderForPlayNext(oldOrder, currentIndex, insertIndex, newItemsCount)
            isPermutationOf(result, oldSize + newItemsCount)

            val currentPos = result.indexOf(currentIndex)
            val expectedBlock = (insertIndex until insertIndex + newItemsCount).toList()
            val actualBlock = (1..newItemsCount).map { result[currentPos + it] }
            assertEquals("seed=$seed", expectedBlock, actualBlock)
        }
    }

    @Test
    fun `every other item keeps its relative order from the old sequence`() {
        repeat(200) { seed ->
            val random = Random(seed.toLong())
            val oldSize = 2 + random.nextInt(10)
            val oldOrder = (0 until oldSize).shuffled(random)
            val currentIndex = oldOrder[random.nextInt(oldSize)]
            val insertIndex = currentIndex + 1
            val newItemsCount = 1 + random.nextInt(4)

            val result = buildShuffleOrderForPlayNext(oldOrder, currentIndex, insertIndex, newItemsCount)

            fun unshift(index: Int) = if (index >= insertIndex + newItemsCount) index - newItemsCount else index

            val newBlockRange = insertIndex until (insertIndex + newItemsCount)
            val resultWithoutNewItems = result.filter { it !in newBlockRange }.map { unshift(it) }
            assertEquals("seed=$seed", oldOrder, resultWithoutNewItems)
        }
    }

    @Test
    fun `a group already contiguous in the old order stays contiguous after the splice`() {
        // Old queue (5 items): group A = indices 1,2,3 (already contiguous, in order, somewhere
        // in the existing shuffle sequence). current = 0. Insert 2 new items right after it.
        val oldOrder = listOf(4, 0, 1, 2, 3) // group A (1,2,3) already contiguous+ordered here
        val result = buildShuffleOrderForPlayNext(oldOrder, currentIndex = 0, insertIndex = 1, newItemsCount = 2)
        isPermutationOf(result, 7)

        val positionOf = IntArray(result.size)
        result.forEachIndexed { pos, value -> positionOf[value] = pos }
        // Old raw indices 1,2,3 shift by +2 (insertIndex=1) to become 3,4,5.
        val shiftedGroupPositions = listOf(3, 4, 5).map { positionOf[it] }.sorted()
        assertEquals((shiftedGroupPositions.first()..shiftedGroupPositions.last()).toList(), shiftedGroupPositions)
        assertEquals(listOf(3, 4, 5), shiftedGroupPositions.map { result[it] })
    }

    @Test
    fun `negative new item count is treated as a no-op like zero`() {
        val oldOrder = listOf(0, 1, 2)
        val result = buildShuffleOrderForPlayNext(oldOrder, currentIndex = 1, insertIndex = 2, newItemsCount = -1)
        assertEquals(oldOrder, result.toList())
    }

    @Test
    fun `single item queue with a playNext insertion`() {
        val result = buildShuffleOrderForPlayNext(oldOrder = listOf(0), currentIndex = 0, insertIndex = 1, newItemsCount = 3)
        assertEquals(listOf(0, 1, 2, 3), result.toList())
    }
}
