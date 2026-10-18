package com.gg.barcodes.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsensusTrackerTest {

    // Two stacked 1D barcodes, close together like on a shipping sticker.
    private val top = Box(100f, 100f, 500f, 160f)
    private val bottom = Box(100f, 180f, 500f, 240f)

    private fun det(value: String, box: Box) = Detection(value, "Code 128", box)

    @Test
    fun confirmsAfterRequiredVotesAndCompletesWithExpectedCount() {
        val tracker = ConsensusTracker(requiredVotes = 5).apply { expectedCount = 2 }
        repeat(4) {
            assertFalse(tracker.onFrame(listOf(det("A", top), det("B", bottom))).complete)
        }
        val result = tracker.onFrame(listOf(det("A", top), det("B", bottom)))
        assertTrue(result.complete)
        assertEquals(listOf("A", "B"), result.tracks.map { it.value })
    }

    @Test
    fun misreadAtSamePositionDelaysConfirmationUntilOutvoted() {
        val tracker = ConsensusTracker(requiredVotes = 5).apply { expectedCount = 1 }
        tracker.onFrame(listOf(det("A1", top)))
        repeat(5) { tracker.onFrame(listOf(det("A", top))) }
        // 5 votes against 1: a lead of 4.
        assertFalse(tracker.onFrame(listOf()).complete)
        tracker.onFrame(listOf(det("A", top)))
        val result = tracker.onFrame(listOf())
        assertTrue(result.complete)
        assertEquals("A", result.tracks.single().value)
    }

    @Test
    fun scatteredMisreadsOfFadedBarcodeDoNotStallIt() {
        val tracker = ConsensusTracker(requiredVotes = 5).apply { expectedCount = 1 }
        listOf("A1", "A2", "A3").forEach { tracker.onFrame(listOf(det(it, top))) }
        repeat(5) { assertFalse(tracker.onFrame(listOf(det("A", top))).complete) }
        assertTrue(tracker.onFrame(listOf(det("A", top))).complete)
    }

    @Test
    fun competingValuesNeverConfirm() {
        val tracker = ConsensusTracker(requiredVotes = 5).apply { expectedCount = 1 }
        repeat(20) {
            assertFalse(tracker.onFrame(listOf(det("A", top))).complete)
            assertFalse(tracker.onFrame(listOf(det("B", top))).complete)
        }
    }

    @Test
    fun neighbouringBarcodesStayInSeparateTracks() {
        val tracker = ConsensusTracker(requiredVotes = 3)
        repeat(4) { tracker.onFrame(listOf(det("A", top), det("B", bottom))) }
        val result = tracker.onFrame(listOf(det("A", top), det("B", bottom)))
        assertEquals(2, result.tracks.size)
        assertTrue(result.tracks.all { it.confirmed && it.leaderVotes == it.totalVotes })
    }

    @Test
    fun waitsForExpectedCount() {
        val tracker = ConsensusTracker(requiredVotes = 3).apply { expectedCount = 3 }
        repeat(10) {
            assertFalse(tracker.onFrame(listOf(det("A", top), det("B", bottom))).complete)
        }
    }

    @Test
    fun autoModeCompletesOnceSceneSettles() {
        val tracker = ConsensusTracker(requiredVotes = 3, settleFrames = 5)
        repeat(5) { assertFalse(tracker.onFrame(listOf(det("A", top))).complete) }
        assertTrue(tracker.onFrame(listOf(det("A", top))).complete)
    }

    @Test
    fun followsBarcodeWhenPhoneMoves() {
        val tracker = ConsensusTracker(requiredVotes = 4)
        tracker.onFrame(listOf(det("A", top)))
        tracker.onFrame(listOf(det("A", top.copy(left = 300f, right = 700f))))
        tracker.onFrame(listOf(det("A", top.copy(top = 400f, bottom = 460f))))
        val result = tracker.onFrame(listOf(det("A", top)))
        assertTrue(result.tracks.single().confirmed)
    }

    @Test
    fun readingOrderIsRowsThenColumns() {
        val boxes = listOf(
            Box(300f, 105f, 400f, 155f),
            Box(0f, 300f, 100f, 350f),
            Box(0f, 100f, 100f, 150f),
        )
        assertEquals(listOf(boxes[2], boxes[0], boxes[1]), boxes.sortedInReadingOrder { it })
    }
}
