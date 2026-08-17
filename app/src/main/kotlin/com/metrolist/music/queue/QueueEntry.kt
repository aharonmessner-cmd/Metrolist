/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.queue

/**
 * A logical unit within the playback queue: either one ordinary song, or a contiguous run of
 * songs that were queued together as a group (e.g. an album or playlist).
 *
 * This is a pure, derived view over a flat queue's group ids - it holds no reference to
 * MediaItem/Player/Compose and can be computed fresh from [queueGroupEntries] whenever the
 * queue changes.
 */
sealed class QueueEntry {
    /** [index] is the position of this song within the flat queue. */
    data class Single(
        val index: Int,
    ) : QueueEntry()

    /**
     * A run of 2+ consecutive queue positions sharing [groupId]. [indices] is always sorted
     * ascending and contiguous, in the queue's original relative order.
     */
    data class Group(
        val groupId: String,
        val groupTitle: String?,
        val indices: List<Int>,
    ) : QueueEntry()
}

/** All queue positions covered by this entry, in original relative order. */
val QueueEntry.entryIndices: List<Int>
    get() = when (this) {
        is QueueEntry.Single -> listOf(index)
        is QueueEntry.Group -> indices
    }

/**
 * Groups a flat queue into [QueueEntry] units.
 *
 * Consecutive positions sharing the same non-null [groupIds] value form one [QueueEntry.Group].
 * A group id reappearing later after an interruption (a different id, or null, in between)
 * starts a *new* group rather than merging with the earlier run. A run of length 1 is emitted
 * as [QueueEntry.Single] rather than a one-item group, so a lone grouped song behaves exactly
 * like an ordinary song everywhere downstream (shuffle, reorder, UI).
 *
 * [groupTitles], if provided, must be the same size as [groupIds]; the title used for a group
 * is read from the first position of its run.
 */
fun queueGroupEntries(
    groupIds: List<String?>,
    groupTitles: List<String?>? = null,
): List<QueueEntry> {
    val entries = mutableListOf<QueueEntry>()
    val n = groupIds.size
    var i = 0
    while (i < n) {
        val id = groupIds[i]
        if (id == null) {
            entries += QueueEntry.Single(i)
            i++
            continue
        }
        val start = i
        val title = groupTitles?.get(i)
        while (i < n && groupIds[i] == id) i++
        val runIndices = (start until i).toList()
        entries += if (runIndices.size == 1) {
            QueueEntry.Single(runIndices[0])
        } else {
            QueueEntry.Group(id, title, runIndices)
        }
    }
    return entries
}

/** Flattens entries back into a full, in-order list of queue positions. */
fun List<QueueEntry>.flattenIndices(): List<Int> = flatMap { it.entryIndices }

/** The entry that contains [index], or null if [index] is out of range of any entry in this list. */
fun List<QueueEntry>.entryContaining(index: Int): QueueEntry? = firstOrNull { index in it.entryIndices }
