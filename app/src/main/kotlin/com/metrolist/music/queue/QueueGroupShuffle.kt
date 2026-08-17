/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.queue

import kotlin.random.Random

/**
 * Builds a group-aware shuffle permutation for a flat queue of [totalCount] items.
 *
 * The result is an [IntArray] of the same shape MusicService already feeds into Media3's
 * `DefaultShuffleOrder(IntArray, seed)`: `result[i]` is the original queue position of the
 * i-th item to play in shuffled order. This function only computes that permutation - it does
 * not touch ExoPlayer/Media3 in any way.
 *
 * Behavior:
 * - [currentIndex] is always first in the result (matching MusicService's existing
 *   "pin the currently playing item to the front of the shuffle order" behavior).
 * - Every [QueueEntry.Group] other than the one [currentIndex] belongs to is kept as a
 *   contiguous block, in its original internal order, and groups are shuffled relative to each
 *   other and to ungrouped songs as units.
 * - If [currentIndex] is inside a group, the *rest* of that group (excluding [currentIndex],
 *   which is playing right now) still moves as one contiguous, internally-ordered block among
 *   the shuffled entries - it is never scattered song-by-song.
 * - If [groupIds] contains no group ids at all, this reduces to "shuffle everything except
 *   the pinned current item", which is statistically equivalent to today's ungrouped algorithm
 *   (shuffle all n items, then swap whichever position holds the current item to the front) via
 *   the standard swap-to-front symmetry: both produce a uniformly random permutation of the
 *   other n-1 items with the current item fixed first.
 *
 * @throws IllegalArgumentException if [groupIds].size != [totalCount], or [currentIndex] is out
 * of range for a non-empty queue.
 */
fun buildGroupAwareShuffleOrder(
    totalCount: Int,
    currentIndex: Int,
    groupIds: List<String?>,
    groupTitles: List<String?>? = null,
    random: Random = Random.Default,
): IntArray {
    require(groupIds.size == totalCount) {
        "groupIds.size (${groupIds.size}) must equal totalCount ($totalCount)"
    }
    if (totalCount == 0) return IntArray(0)
    require(currentIndex in 0 until totalCount) {
        "currentIndex ($currentIndex) out of range for totalCount ($totalCount)"
    }

    val entries = queueGroupEntries(groupIds, groupTitles)
    val currentEntry = entries.entryContaining(currentIndex)
        ?: error("currentIndex ($currentIndex) not covered by any queue entry")

    val remainderEntries = mutableListOf<QueueEntry>()
    for (entry in entries) {
        if (entry !== currentEntry) {
            remainderEntries += entry
            continue
        }
        val rest = entry.entryIndices.filter { it != currentIndex }
        if (rest.isEmpty()) continue
        remainderEntries += if (rest.size == 1) {
            QueueEntry.Single(rest.single())
        } else {
            // entry is necessarily a Group here: a Single's only index is currentIndex, so
            // `rest` would already be empty and handled above.
            val group = entry as QueueEntry.Group
            QueueEntry.Group(group.groupId, group.groupTitle, rest)
        }
    }

    val shuffledRemainder = remainderEntries.shuffled(random)

    val result = IntArray(totalCount)
    var pos = 0
    result[pos++] = currentIndex
    for (entry in shuffledRemainder) {
        for (index in entry.entryIndices) {
            result[pos++] = index
        }
    }
    return result
}
