/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.queue

/**
 * Moves the [QueueEntry] at [fromEntryIndex] to sit at [toEntryIndex] within [entries], treating
 * each entry (single song or whole group) as one indivisible unit.
 *
 * Because this operates at entry granularity rather than flat queue-position granularity, a
 * [QueueEntry.Group] can never be split by this function - moving a group always relocates all
 * of its member positions together, in their original internal order. This is the primitive the
 * queue UI's "drag a group header" gesture is expected to call; flatten the result with
 * [flattenIndices] to get the new flat queue-position order to hand to the player.
 *
 * Indices are entry-list positions (0 until entries.size), not flat queue positions.
 * Out-of-range or no-op indices return [entries] unchanged.
 */
fun moveQueueGroupBlock(
    entries: List<QueueEntry>,
    fromEntryIndex: Int,
    toEntryIndex: Int,
): List<QueueEntry> {
    if (entries.isEmpty()) return entries
    if (fromEntryIndex !in entries.indices || toEntryIndex !in entries.indices) return entries
    if (fromEntryIndex == toEntryIndex) return entries

    val mutable = entries.toMutableList()
    val moved = mutable.removeAt(fromEntryIndex)
    mutable.add(toEntryIndex, moved)
    return mutable
}

/**
 * When the user drags an individual song row (as opposed to a group header) at flat queue
 * position [draggedFlatIndex] toward [targetFlatIndex], clamps the target so a drag can never
 * pull the song out of its own group and never lets it cross into/past a neighboring group.
 *
 * If the dragged position belongs to a [QueueEntry.Group], the result is [targetFlatIndex]
 * clamped to that group's own flat-index bounds (its first and last member positions).
 * If it belongs to a [QueueEntry.Single] (an ordinary, ungrouped song, or a would-be one-song
 * group which is already represented as a Single), [targetFlatIndex] is returned unchanged -
 * ungrouped reordering is intentionally unconstrained, matching today's behavior.
 *
 * This only computes a clamped index; it does not itself reorder anything.
 */
fun clampReorderTargetWithinGroup(
    entries: List<QueueEntry>,
    draggedFlatIndex: Int,
    targetFlatIndex: Int,
): Int {
    val entry = entries.entryContaining(draggedFlatIndex) ?: return targetFlatIndex
    if (entry !is QueueEntry.Group) return targetFlatIndex
    val lo = entry.indices.first()
    val hi = entry.indices.last()
    return targetFlatIndex.coerceIn(lo, hi)
}
