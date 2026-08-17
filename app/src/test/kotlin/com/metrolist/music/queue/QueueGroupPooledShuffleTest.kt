package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for [buildGroupAwarePooledShuffleOrder], the group-aware version of MusicService's
 * `shufflePlaylistFirst` (original-queue-vs-added-items) shuffle pooling.
 */
class QueueGroupPooledShuffleTest {
    private fun isPermutationOf(result: IntArray, totalCount: Int) {
        assertEquals(totalCount, result.size)
        assertEquals((0 until totalCount).toSet(), result.toSet())
    }

    private fun positions(result: IntArray): IntArray {
        val positionInResult = IntArray(result.size)
        result.forEachIndexed { pos, originalIndex -> positionInResult[originalIndex] = pos }
        return positionInResult
    }

    /** Same contiguity/order check as QueueGroupShuffleTest, scoped to one contiguous index range. */
    private fun assertContiguousAndOrdered(result: IntArray, indices: List<Int>) {
        if (indices.size < 2) return
        val positionInResult = positions(result)
        val resultPositions = indices.map { positionInResult[it] }.sorted()
        assertEquals((resultPositions.first()..resultPositions.last()).toList(), resultPositions)
        assertEquals(indices.sorted(), resultPositions.map { result[it] })
    }

    @Test
    fun `empty queue yields empty permutation`() {
        val result = buildGroupAwarePooledShuffleOrder(
            totalCount = 0, currentIndex = 0, groupIds = emptyList(), poolBoundary = 0,
        )
        assertEquals(0, result.size)
    }

    @Test
    fun `mismatched groupIds size throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGroupAwarePooledShuffleOrder(totalCount = 3, currentIndex = 0, groupIds = listOf(null, null), poolBoundary = 1)
        }
    }

    @Test
    fun `out of range currentIndex throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            buildGroupAwarePooledShuffleOrder(totalCount = 2, currentIndex = 9, groupIds = listOf(null, null), poolBoundary = 1)
        }
    }

    @Test
    fun `current is always first and original pool block precedes added pool block`() {
        // Original pool (indices 0-4): Group A + one single. Added pool (5-7): Group B.
        val groupIds = listOf("A", "A", "A", null, null, "B", "B", "B")
        val poolBoundary = 5
        repeat(100) { seed ->
            for (current in groupIds.indices) {
                val result = buildGroupAwarePooledShuffleOrder(
                    totalCount = groupIds.size,
                    currentIndex = current,
                    groupIds = groupIds,
                    poolBoundary = poolBoundary,
                    random = Random(seed.toLong() * 10 + current),
                )
                isPermutationOf(result, groupIds.size)
                assertEquals(current, result[0])

                val pos = positions(result)
                val originalPoolPositions = (0 until poolBoundary).filter { it != current }.map { pos[it] }
                val addedPoolPositions = (poolBoundary until groupIds.size).filter { it != current }.map { pos[it] }
                if (originalPoolPositions.isNotEmpty() && addedPoolPositions.isNotEmpty()) {
                    // sorted().last()/.first() instead of max()/min() - version-portable across
                    // the standalone kotlinc fallback and the project's real Kotlin toolchain.
                    assertTrue(
                        "original pool must appear entirely before the added pool (current=$current)",
                        originalPoolPositions.sorted().last() < addedPoolPositions.sorted().first(),
                    )
                }
            }
        }
    }

    @Test
    fun `groups within each pool stay contiguous and internally ordered`() {
        val groupIds = listOf("A", "A", "A", null, null, "B", "B", null, "C", "C", "C")
        val poolBoundary = 5
        repeat(150) { seed ->
            val current = seed % groupIds.size
            val result = buildGroupAwarePooledShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = current,
                groupIds = groupIds,
                poolBoundary = poolBoundary,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            // Group A (0,1,2) is entirely in the original pool; Group C (8,9,10) entirely added.
            assertContiguousAndOrdered(result, listOf(0, 1, 2).filter { it != current })
            assertContiguousAndOrdered(result, listOf(8, 9, 10).filter { it != current })
        }
    }

    @Test
    fun `current inside a group in the original pool keeps the rest of that group contiguous`() {
        val groupIds = listOf("A", "A", "A", "A", null, "B", "B")
        val poolBoundary = 5
        repeat(80) { seed ->
            val result = buildGroupAwarePooledShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = 1, // inside group A
                groupIds = groupIds,
                poolBoundary = poolBoundary,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            assertEquals(1, result[0])
            assertContiguousAndOrdered(result, listOf(0, 2, 3))
        }
    }

    @Test
    fun `pool boundary at 0 behaves like everything is the added pool`() {
        val groupIds = listOf("A", "A", null, "B", "B")
        repeat(50) { seed ->
            val result = buildGroupAwarePooledShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = 0,
                groupIds = groupIds,
                poolBoundary = 0,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            assertEquals(0, result[0])
            assertContiguousAndOrdered(result, listOf(1)) // group A's remainder after current=0
            assertContiguousAndOrdered(result, listOf(3, 4))
        }
    }

    @Test
    fun `pool boundary at totalCount behaves like everything is the original pool`() {
        val groupIds = listOf("A", "A", null, "B", "B")
        repeat(50) { seed ->
            val result = buildGroupAwarePooledShuffleOrder(
                totalCount = groupIds.size,
                currentIndex = 0,
                groupIds = groupIds,
                poolBoundary = groupIds.size,
                random = Random(seed.toLong()),
            )
            isPermutationOf(result, groupIds.size)
            assertEquals(0, result[0])
        }
    }

    @Test
    fun `a group straddling the pool boundary is split into two independent groups without losing items`() {
        // Group "A" spans indices 2,3,4 but the pool boundary cuts it at 3: pool A = [0,1,2],
        // pool B = [3,4]. Not expected from any real MusicService call site, but must still be
        // a safe, complete, non-crashing permutation.
        val groupIds = listOf(null, null, "A", "A", "A")
        val result = buildGroupAwarePooledShuffleOrder(
            totalCount = groupIds.size,
            currentIndex = 0,
            groupIds = groupIds,
            poolBoundary = 3,
            random = Random(7),
        )
        isPermutationOf(result, groupIds.size)
        assertContiguousAndOrdered(result, listOf(2)) // pool A's half of "A"
        assertContiguousAndOrdered(result, listOf(3, 4)) // pool B's half of "A"
    }
}
