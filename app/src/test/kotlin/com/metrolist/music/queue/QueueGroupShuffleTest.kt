package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueGroupShuffleTest {
    private fun isPermutationOf(result: IntArray, totalCount: Int) {
        assertEquals(totalCount, result.size)
        assertEquals((0 until totalCount).toSet(), result.toSet())
    }

    /**
     * Every group's member positions appear contiguously and in original relative order in
     * [result] - except for [currentIndex] itself, which the algorithm deliberately pins to the
     * very front of the result (it's the song playing right now), pulling it out of its group's
     * block. Groups are derived via [queueGroupEntries] (the same run-based grouping the
     * production code uses), not by raw group-id value, so two separated runs sharing an id are
     * correctly treated as independent groups here too.
     */
    private fun assertGroupsContiguousAndOrdered(result: IntArray, groupIds: List<String?>, currentIndex: Int) {
        val positionInResult = IntArray(result.size)
        result.forEachIndexed { pos, originalIndex -> positionInResult[originalIndex] = pos }

        for (entry in queueGroupEntries(groupIds)) {
            if (entry !is QueueEntry.Group) continue
            val memberIndices = entry.indices.filter { it != currentIndex }
            if (memberIndices.size < 2) continue // 0-1 remaining members: trivially contiguous
            val resultPositions = memberIndices.map { positionInResult[it] }.sorted()
            // Contiguous: no gaps between the min and max result position for this group.
            assertEquals(
                (resultPositions.first()..resultPositions.last()).toList(),
                resultPositions,
            )
            // Internally ordered: walking the contiguous block left-to-right yields the
            // original indices in their original ascending order.
            val orderedOriginalIndices = resultPositions.map { result[it] }
            assertEquals(memberIndices.sorted(), orderedOriginalIndices)
        }
    }

    @Test
    fun `empty queue yields empty permutation`() {
        val result = buildGroupAwareShuffleOrder(totalCount = 0, currentIndex = 0, groupIds = emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `mismatched groupIds size throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGroupAwareShuffleOrder(totalCount = 3, currentIndex = 0, groupIds = listOf(null, null))
        }
    }

    @Test
    fun `out of range currentIndex throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGroupAwareShuffleOrder(totalCount = 2, currentIndex = 5, groupIds = listOf(null, null))
        }
    }

    @Test
    fun `single item queue is trivially permuted`() {
        val result = buildGroupAwareShuffleOrder(totalCount = 1, currentIndex = 0, groupIds = listOf(null))
        assertEquals(intArrayOf(0).toList(), result.toList())
    }

    @Test
    fun `current index is always first regardless of grouping`() {
        val groupIds = listOf("A", "A", "A", null, "B", "B")
        repeat(50) { seed ->
            for (current in groupIds.indices) {
                val result = buildGroupAwareShuffleOrder(
                    totalCount = groupIds.size,
                    currentIndex = current,
                    groupIds = groupIds,
                    random = Random(seed.toLong() * 100 + current),
                )
                assertEquals("seed=$seed current=$current", current, result[0])
            }
        }
    }

    @Test
    fun `result is always a valid permutation and groups stay contiguous and ordered`() {
        val groupIds = listOf("A", "A", "A", null, "B", "B", null, "C", "C", "C", "C")
        repeat(200) { seed ->
            val current = seed % groupIds.size
            val result = buildGroupAwareShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = current,
                groupIds = groupIds,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            assertGroupsContiguousAndOrdered(result, groupIds, currentIndex = current)
        }
    }

    @Test
    fun `current index in the middle of a group still keeps the rest of that group contiguous and ordered`() {
        // Group A = [0,1,2,3], current = 1 (A2). Expect: 1 first, then somewhere among the
        // shuffled remainder, [0,2,3] appears as one contiguous run in that original order.
        val groupIds = listOf("A", "A", "A", "A", null, "B", "B")
        repeat(100) { seed ->
            val result = buildGroupAwareShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = 1,
                groupIds = groupIds,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            assertEquals(1, result[0])

            val positionInResult = IntArray(result.size)
            result.forEachIndexed { pos, originalIndex -> positionInResult[originalIndex] = pos }
            val remainderOfA = listOf(0, 2, 3)
            val positions = remainderOfA.map { positionInResult[it] }.sorted()
            assertEquals((positions.first()..positions.last()).toList(), positions)
            assertEquals(remainderOfA, positions.map { result[it] })
        }
    }

    @Test
    fun `no groups present is equivalent in shape to the pre-Queue-Groups algorithm`() {
        // All null group ids: this must reduce to "current pinned first, everything else
        // shuffled" with no group-contiguity constraints at all - i.e. a plain permutation.
        val groupIds = List<String?>(8) { null }
        val result = buildGroupAwareShuffleOrder(
            totalCount = groupIds.size,
            currentIndex = 3,
            groupIds = groupIds,
            random = Random(42),
        )
        isPermutationOf(result, groupIds.size)
        assertEquals(3, result[0])
    }

    @Test
    fun `shuffling is not a no-op for a large mixed queue across many seeds`() {
        // Sanity check that the function actually randomizes (regression guard against an
        // accidental identity permutation), without asserting any specific ordering.
        val groupIds = listOf("A", "A", "A", null, "B", "B", null, null, "C", "C")
        val identity = groupIds.indices.toList()
        var sawNonIdentity = false
        for (seed in 0 until 20) {
            val result = buildGroupAwareShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = 0,
                groupIds = groupIds,
                random = Random(seed.toLong()),
            )
            if (result.toList() != identity) sawNonIdentity = true
        }
        assertTrue(sawNonIdentity)
    }
}
