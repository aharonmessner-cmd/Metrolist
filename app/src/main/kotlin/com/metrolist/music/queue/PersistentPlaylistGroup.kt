/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.queue

/**
 * Converts PERSISTENT (database-stored) playlist group ids into fresh, per-queue-instance
 * queueGroupIds: each contiguous run of the same non-null [persistentGroupIds] value becomes ONE
 * freshly generated queue group id (see [newQueueGroupId]), shared by every item in that run.
 * Items with a null persistent group id (ordinary playlist entries) map to `null to null`.
 *
 * This is the only place a persisted playlist group (see
 * com.metrolist.music.db.entities.PlaylistSongMap.playlistGroupId) is allowed to become a
 * runtime queue group. The persistent database group id itself is NEVER reused as the
 * queueGroupId - playlist storage identity and queue-instance identity stay fully independent,
 * so loading the same grouped playlist into the queue twice produces two different queue groups,
 * exactly like re-adding an album "as Group" twice does (see [newQueueGroupId]).
 *
 * A run of length 1 is treated as an ordinary ungrouped item (`null to null`), matching
 * [queueGroupEntries]'s singleton rule - one stray persisted-group song behaves exactly like a
 * normal playlist entry everywhere downstream.
 *
 * The result is a list of (queueGroupId, queueGroupTitle) pairs, same size and order as
 * [persistentGroupIds], suitable for stamping onto each corresponding queue item.
 */
fun reifyPersistentPlaylistGroups(
    persistentGroupIds: List<String?>,
    persistentGroupTitles: List<String?>? = null,
): List<Pair<String?, String?>> {
    val entries = queueGroupEntries(persistentGroupIds, persistentGroupTitles)
    val result = MutableList<Pair<String?, String?>>(persistentGroupIds.size) { null to null }
    for (entry in entries) {
        when (entry) {
            is QueueEntry.Single -> result[entry.index] = null to null
            is QueueEntry.Group -> {
                val freshQueueGroupId = newQueueGroupId()
                for (index in entry.indices) {
                    result[index] = freshQueueGroupId to entry.groupTitle
                }
            }
        }
    }
    return result
}
