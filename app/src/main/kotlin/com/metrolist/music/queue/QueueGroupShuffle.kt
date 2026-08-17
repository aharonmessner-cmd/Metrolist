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
    val remainder = excludingCurrentFromItsEntry(entries, currentIndex)
    val shuffledRemainder = remainder.shuffled(random)

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

/**
 * Group-aware variant of [buildGroupAwareShuffleOrder] for MusicService's `shufflePlaylistFirst`
 * feature: items are split into two independent pools by [poolBoundary] - the originally-queued
 * items `[0, poolBoundary)` and everything added to the queue afterward `[poolBoundary,
 * totalCount)`. Each pool is shuffled independently (groups are still kept contiguous and
 * internally ordered within their own pool), and the result always places the "original" pool's
 * block before the "added" pool's block, exactly matching MusicService's existing
 * shufflePlaylistFirst ordering. [currentIndex] is always first, exactly as in
 * [buildGroupAwareShuffleOrder].
 *
 * If a group's members happen to fall on both sides of [poolBoundary] (not expected from any
 * current MusicService call site, since a group is always formed by one atomic batch-add and
 * `originalQueueSize` is fixed at that same moment), each side is grouped independently rather
 * than merged or rejected: still always a valid, complete permutation, just two smaller groups
 * instead of one that would have had to cross pools.
 *
 * @throws IllegalArgumentException if [groupIds].size != [totalCount], or [currentIndex] is out
 * of range for a non-empty queue.
 */
fun buildGroupAwarePooledShuffleOrder(
    totalCount: Int,
    currentIndex: Int,
    groupIds: List<String?>,
    poolBoundary: Int,
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
    val boundary = poolBoundary.coerceIn(0, totalCount)

    fun entriesForRange(start: Int, end: Int): List<QueueEntry> {
        if (start >= end) return emptyList()
        val idsSlice = groupIds.subList(start, end)
        val titlesSlice = groupTitles?.subList(start, end)
        return queueGroupEntries(idsSlice, titlesSlice).map { it.offsetBy(start) }
    }

    val originalPoolEntries = entriesForRange(0, boundary)
    val addedPoolEntries = entriesForRange(boundary, totalCount)

    val (originalPool, addedPool) = if (currentIndex < boundary) {
        excludingCurrentFromItsEntry(originalPoolEntries, currentIndex) to addedPoolEntries
    } else {
        originalPoolEntries to excludingCurrentFromItsEntry(addedPoolEntries, currentIndex)
    }

    val shuffledOriginal = originalPool.shuffled(random)
    val shuffledAdded = addedPool.shuffled(random)

    val result = IntArray(totalCount)
    var pos = 0
    result[pos++] = currentIndex
    for (entry in shuffledOriginal) for (index in entry.entryIndices) result[pos++] = index
    for (entry in shuffledAdded) for (index in entry.entryIndices) result[pos++] = index
    return result
}

/**
 * Computes MusicService.playNext()'s new shuffle order after inserting [newItemsCount] items at
 * [insertIndex] (always `currentIndex + 1` at every real call site), given the shuffle traversal
 * order that existed immediately before the insertion ([oldOrder] - a snapshot of the full
 * current shuffle sequence as raw queue positions, captured via a Player extension such as
 * `Player.shuffleOrderIndices()` while the timeline still had its pre-insertion shape).
 *
 * The result places the newly inserted items as one contiguous, internally-ordered block
 * immediately after [currentIndex] - wherever in the sequence [currentIndex] currently sits
 * (not necessarily first: mid-playback in shuffle mode, "current" can be anywhere in the
 * established shuffle order) - while preserving every other item's existing relative order
 * exactly. Because nothing else is reshuffled, any Queue Group that was already a contiguous
 * block in [oldOrder] stays contiguous, and the newly inserted batch is itself always one
 * contiguous unit in its original (insertion) order - it is never split.
 *
 * @throws IllegalArgumentException if [oldOrder] is empty, is not a permutation of
 * `0 until oldOrder.size`, or does not contain [currentIndex].
 */
fun buildShuffleOrderForPlayNext(
    oldOrder: List<Int>,
    currentIndex: Int,
    insertIndex: Int,
    newItemsCount: Int,
): IntArray {
    require(oldOrder.isNotEmpty()) { "oldOrder must not be empty" }
    require(oldOrder.toSet() == (0 until oldOrder.size).toSet()) {
        "oldOrder must be a permutation of 0 until ${oldOrder.size}"
    }
    val currentPos = oldOrder.indexOf(currentIndex)
    require(currentPos != -1) { "currentIndex ($currentIndex) not present in oldOrder" }
    if (newItemsCount <= 0) return oldOrder.toIntArray()

    fun shift(index: Int) = if (index >= insertIndex) index + newItemsCount else index

    val result = IntArray(oldOrder.size + newItemsCount)
    var pos = 0
    for (i in 0 until currentPos) result[pos++] = shift(oldOrder[i])
    result[pos++] = shift(currentIndex)
    for (i in insertIndex until insertIndex + newItemsCount) result[pos++] = i
    for (i in currentPos + 1 until oldOrder.size) result[pos++] = shift(oldOrder[i])
    return result
}

private fun QueueEntry.offsetBy(offset: Int): QueueEntry = when (this) {
    is QueueEntry.Single -> QueueEntry.Single(index + offset)
    is QueueEntry.Group -> QueueEntry.Group(groupId, groupTitle, indices.map { it + offset })
}

/**
 * Returns [entries] with whichever entry contains [currentIndex] replaced by "the rest of that
 * entry" (its other members, minus [currentIndex] itself) - or omitted entirely if nothing
 * remains. All other entries are returned unchanged, in their original relative order. If no
 * entry contains [currentIndex], [entries] is returned unchanged.
 */
private fun excludingCurrentFromItsEntry(entries: List<QueueEntry>, currentIndex: Int): List<QueueEntry> {
    val currentEntry = entries.entryContaining(currentIndex) ?: return entries
    val result = mutableListOf<QueueEntry>()
    for (entry in entries) {
        if (entry !== currentEntry) {
            result += entry
            continue
        }
        val rest = entry.entryIndices.filter { it != currentIndex }
        if (rest.isEmpty()) continue
        result += if (rest.size == 1) {
            QueueEntry.Single(rest.single())
        } else {
            // entry is necessarily a Group here: a Single's only index is currentIndex, so
            // `rest` would already be empty and handled above.
            val group = entry as QueueEntry.Group
            QueueEntry.Group(group.groupId, group.groupTitle, rest)
        }
    }
    return result
}
