/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.queue

import java.util.UUID

/**
 * Generates a fresh, unique id for one "add this album/playlist to the queue as a batch"
 * action. Call this once per batch-queueing action (Play/Shuffle/Play Next/Add to Queue of a
 * whole album or playlist) and stamp the same value onto every song in that batch via
 * [com.metrolist.music.models.MediaMetadata.queueGroupId] - never the album/playlist's own
 * permanent id, so re-adding the same collection later produces a distinct group instead of
 * merging into the earlier one.
 */
fun newQueueGroupId(): String = UUID.randomUUID().toString()
