package com.metrolist.music.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentPlaylistGroupTest {
    @Test
    fun `no persistent groups yields all null pairs`() {
        val result = reifyPersistentPlaylistGroups(listOf(null, null, null))
        assertEquals(listOf(null to null, null to null, null to null), result)
    }

    @Test
    fun `a persisted group of 2+ songs gets one shared fresh queueGroupId`() {
        val result = reifyPersistentPlaylistGroups(
            persistentGroupIds = listOf("db-group-1", "db-group-1", "db-group-1"),
            persistentGroupTitles = listOf("Album X", "Album X", "Album X"),
        )
        val ids = result.map { it.first }
        assertTrue(ids.all { it != null })
        assertEquals(1, ids.toSet().size) // all three share the same fresh id
        assertEquals(listOf("Album X", "Album X", "Album X"), result.map { it.second })
    }

    @Test
    fun `the fresh queueGroupId is never the persistent database group id`() {
        val result = reifyPersistentPlaylistGroups(
            persistentGroupIds = listOf("db-group-1", "db-group-1"),
        )
        result.forEach { (queueGroupId, _) ->
            assertNotEquals("db-group-1", queueGroupId)
        }
    }

    @Test
    fun `reifying the same persistent group twice produces two different queue group ids`() {
        val persistentIds = listOf("db-group-1", "db-group-1")
        val first = reifyPersistentPlaylistGroups(persistentIds)
        val second = reifyPersistentPlaylistGroups(persistentIds)
        assertNotEquals(first[0].first, second[0].first)
    }

    @Test
    fun `a lone persisted-group song is treated as an ordinary ungrouped item`() {
        val result = reifyPersistentPlaylistGroups(listOf(null, "db-group-1", null))
        assertEquals(listOf(null to null, null to null, null to null), result)
    }

    @Test
    fun `same persistent group id in two separated runs becomes two independent fresh groups`() {
        val result = reifyPersistentPlaylistGroups(
            listOf("db-group-1", "db-group-1", null, "db-group-1", "db-group-1"),
        )
        val firstRunId = result[0].first
        val secondRunId = result[3].first
        assertEquals(firstRunId, result[1].first)
        assertEquals(secondRunId, result[4].first)
        assertNotEquals(firstRunId, secondRunId)
        assertNull(result[2].first)
    }

    @Test
    fun `mixed normal entries and one persisted group preserve order and boundaries`() {
        // Normal, normal, [group of 3], normal
        val result = reifyPersistentPlaylistGroups(
            listOf(null, null, "db-group-1", "db-group-1", "db-group-1", null),
        )
        assertEquals(null, result[0].first)
        assertEquals(null, result[1].first)
        val groupId = result[2].first
        assertTrue(groupId != null)
        assertEquals(groupId, result[3].first)
        assertEquals(groupId, result[4].first)
        assertEquals(null, result[5].first)
    }

    @Test
    fun `multiple distinct persisted groups each get their own fresh id`() {
        val result = reifyPersistentPlaylistGroups(
            listOf("db-group-1", "db-group-1", null, "db-group-2", "db-group-2", "db-group-2"),
        )
        val groupAId = result[0].first
        val groupBId = result[3].first
        assertTrue(groupAId != null && groupBId != null)
        assertNotEquals(groupAId, groupBId)
        assertEquals(groupAId, result[1].first)
        assertEquals(listOf(groupBId, groupBId), listOf(result[4].first, result[5].first))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<Pair<String?, String?>>(), reifyPersistentPlaylistGroups(emptyList()))
    }

    @Test
    fun `result size and order always match input`() {
        val ids = listOf(null, "g1", "g1", null, "g2", "g2", "g2", null)
        val result = reifyPersistentPlaylistGroups(ids)
        assertEquals(ids.size, result.size)
    }
}
