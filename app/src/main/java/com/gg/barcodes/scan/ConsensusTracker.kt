package com.gg.barcodes.scan

import kotlin.math.abs

/** Axis-aligned rectangle in upright image pixel coordinates. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun iou(other: Box): Float {
        val ix = maxOf(0f, minOf(right, other.right) - maxOf(left, other.left))
        val iy = maxOf(0f, minOf(bottom, other.bottom) - maxOf(top, other.top))
        val inter = ix * iy
        val union = width * height + other.width * other.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    /** Share of this box's area that lies inside [other]. */
    fun coveredBy(other: Box): Float {
        val ix = maxOf(0f, minOf(right, other.right) - maxOf(left, other.left))
        val iy = maxOf(0f, minOf(bottom, other.bottom) - maxOf(top, other.top))
        val area = width * height
        return if (area <= 0f) 0f else ix * iy / area
    }

    /** True when [other]'s center lies within half a width/height of this box's center. */
    fun isNear(other: Box): Boolean =
        abs(centerX - other.centerX) < width / 2f && abs(centerY - other.centerY) < height / 2f
}

/**
 * One barcode decoded in one camera frame. [weakCheck] marks a format without a mandatory check
 * digit (Code 39, Codabar, ITF), whose values the tracker accepts only on stricter terms.
 */
data class Detection(val value: String, val format: String, val box: Box, val weakCheck: Boolean = false)

/** Public view of a physical barcode followed across frames. */
data class TrackState(
    val id: Int,
    val value: String,
    val format: String,
    val box: Box,
    val confirmed: Boolean,
    val leaderVotes: Int,
    val totalVotes: Int,
)

data class FrameResult(
    val tracks: List<TrackState>,
    /** Track id -> box for every track that was seen in this frame. */
    val seenInFrame: Map<Int, Box>,
    val complete: Boolean,
)

/**
 * Accepts a barcode value only after it has been decoded identically in many frames.
 *
 * Every physical barcode on the sticker becomes a *track*, matched across frames by position
 * (and by value when the phone moves). Each frame casts one vote for the value decoded at that
 * track. A track is confirmed when its leading value is ahead of the runner-up by at least
 * [requiredVotes] votes. A wrong value would need that many more reads than the right one, which
 * doesn't happen in practice. An occasional misread of a faded barcode only delays confirmation
 * by one frame, where a share-of-all-votes rule would stall until it is heavily outvoted.
 *
 * A value of a format without a check digit needs a lead of [weakRequiredVotes] instead, since
 * one wrongly read bar still decodes to a valid-looking value. Such values shorter than
 * [minWeakLength] are dropped: they are what bar fragments and printed text decode as. So is
 * such a value whose box covers most of two or more other barcodes: a slanted scan line through
 * stacked barcodes, which mixes their characters into a valid-looking value.
 *
 * Not thread-safe; call from a single thread.
 */
class ConsensusTracker(
    private val requiredVotes: Int = 6,
    private val weakRequiredVotes: Int = 10,
    private val minWeakLength: Int = 3,
    /** Unconfirmed tracks unseen for this many frames are dropped as noise. */
    private val staleFrames: Int = 45,
    /** A track seen within this many frames counts as currently visible. */
    private val activeFrames: Int = 10,
    /** In auto mode, no new track may appear for this many frames before finishing. */
    private val settleFrames: Int = 20,
) {
    private class Track(val id: Int, var box: Box, var lastSeen: Int) {
        val votes = LinkedHashMap<String, Int>()
        val formats = HashMap<String, String>()
        val weakValues = HashSet<String>()
        var total = 0

        fun leader(): Map.Entry<String, Int> = votes.maxBy { it.value }

        /** Votes of the leading value minus votes of the next most frequent one. */
        fun lead(): Int {
            var first = 0
            var second = 0
            for (count in votes.values) {
                if (count > first) {
                    second = first
                    first = count
                } else if (count > second) {
                    second = count
                }
            }
            return first - second
        }

        fun vote(detection: Detection, frame: Int) {
            votes.merge(detection.value, 1, Int::plus)
            formats[detection.value] = detection.format
            if (detection.weakCheck) weakValues += detection.value
            total++
            box = detection.box
            lastSeen = frame
        }
    }

    /** Number of barcodes expected on the sticker, or null to finish when the scene settles. */
    var expectedCount: Int? = null

    private val tracks = mutableListOf<Track>()
    private var frame = 0
    private var nextId = 0
    private var lastNewTrackFrame = 0

    fun reset() {
        tracks.clear()
        frame = 0
        nextId = 0
        lastNewTrackFrame = 0
    }

    fun onFrame(frameDetections: List<Detection>): FrameResult {
        frame++
        val detections = frameDetections.filter { d ->
            !d.weakCheck || (d.value.length >= minWeakLength && !isCrossRead(d, frameDetections))
        }
        val seen = HashMap<Int, Box>()
        val matchedTracks = HashSet<Track>()
        val unmatched = assign(detections, matchedTracks) { track, detection ->
            track.vote(detection, frame)
            seen[track.id] = detection.box
        }
        for (detection in unmatched) {
            val track = Track(nextId++, detection.box, frame)
            track.vote(detection, frame)
            tracks += track
            seen[track.id] = detection.box
            lastNewTrackFrame = frame
        }
        tracks.removeAll { !it.isConfirmed() && frame - it.lastSeen > staleFrames }

        return FrameResult(
            tracks = tracks.map { it.toState() },
            seenInFrame = seen,
            complete = isComplete(),
        )
    }

    /** Greedily pairs detections with existing tracks; returns detections left without a track. */
    private fun assign(
        detections: List<Detection>,
        matchedTracks: MutableSet<Track>,
        onMatch: (Track, Detection) -> Unit,
    ): List<Detection> {
        data class Candidate(val detection: Detection, val track: Track, val score: Float)

        val candidates = buildList {
            for (detection in detections) {
                for (track in tracks) {
                    val iou = track.box.iou(detection.box)
                    val near = track.box.isNear(detection.box)
                    val knownValue = detection.value in track.votes
                    if (iou < 0.2f && !near && !knownValue) continue
                    val sameLeader = track.leader().key == detection.value
                    val score = iou + (if (near) 0.1f else 0f) + (if (sameLeader) 1f else 0f)
                    add(Candidate(detection, track, score))
                }
            }
        }.sortedByDescending { it.score }

        val matchedDetections = HashSet<Detection>()
        for (candidate in candidates) {
            if (candidate.detection in matchedDetections || candidate.track in matchedTracks) continue
            matchedDetections += candidate.detection
            matchedTracks += candidate.track
            onMatch(candidate.track, candidate.detection)
        }
        return detections.filter { it !in matchedDetections }
    }

    private fun isCrossRead(detection: Detection, frameDetections: List<Detection>): Boolean {
        val others = tracks.map { it.box } + frameDetections.filter { it !== detection }.map { it.box }
        return others.count { it.coveredBy(detection.box) >= 0.5f } >= 2
    }

    private fun Track.isConfirmed(): Boolean =
        lead() >= if (leader().key in weakValues) weakRequiredVotes else requiredVotes

    /** An unconfirmed track that is visible now and not a one-off glitch. */
    private fun Track.isPending(): Boolean =
        !isConfirmed() && total >= 2 && frame - lastSeen <= activeFrames

    private fun isComplete(): Boolean {
        val confirmed = tracks.count { it.isConfirmed() }
        if (confirmed == 0 || tracks.any { it.isPending() }) return false
        val expected = expectedCount
        return if (expected != null) {
            confirmed >= expected
        } else {
            frame - lastNewTrackFrame >= settleFrames
        }
    }

    private fun Track.toState(): TrackState {
        val leader = leader()
        return TrackState(
            id = id,
            value = leader.key,
            format = formats.getValue(leader.key),
            box = box,
            confirmed = isConfirmed(),
            leaderVotes = leader.value,
            totalVotes = total,
        )
    }
}

/** Orders boxes in reading order: rows top to bottom, left to right within a row. */
fun <T> List<T>.sortedInReadingOrder(box: (T) -> Box): List<T> {
    val remaining = sortedBy { box(it).centerY }.toMutableList()
    val result = ArrayList<T>(size)
    while (remaining.isNotEmpty()) {
        val first = box(remaining.first())
        val row = remaining.filter { abs(box(it).centerY - first.centerY) < first.height / 2f }
        remaining.removeAll(row.toSet())
        result += row.sortedBy { box(it).centerX }
    }
    return result
}
