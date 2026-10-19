// Port of app/src/main/java/com/gg/barcodes/scan/ConsensusTracker.kt. Keep the two in sync.

/** Axis-aligned rectangle in image pixel coordinates. */
export interface Box {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export const width = (b: Box) => b.right - b.left;
export const height = (b: Box) => b.bottom - b.top;
export const centerX = (b: Box) => (b.left + b.right) / 2;
export const centerY = (b: Box) => (b.top + b.bottom) / 2;

export function iou(a: Box, b: Box): number {
  const ix = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
  const iy = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
  const inter = ix * iy;
  const union = width(a) * height(a) + width(b) * height(b) - inter;
  return union <= 0 ? 0 : inter / union;
}

/** True when [other]'s center lies within half a width/height of [box]'s center. */
export function isNear(box: Box, other: Box): boolean {
  return (
    Math.abs(centerX(box) - centerX(other)) < width(box) / 2 &&
    Math.abs(centerY(box) - centerY(other)) < height(box) / 2
  );
}

/** One barcode decoded in one camera frame. */
export interface Detection {
  value: string;
  format: string;
  box: Box;
}

/** Public view of a physical barcode followed across frames. */
export interface TrackState {
  id: number;
  value: string;
  format: string;
  box: Box;
  confirmed: boolean;
  leaderVotes: number;
  totalVotes: number;
}

export interface FrameResult {
  tracks: TrackState[];
  /** Track id -> box for every track that was seen in this frame. */
  seenInFrame: Map<number, Box>;
  complete: boolean;
}

export interface TrackerOptions {
  requiredVotes?: number;
  /** Unconfirmed tracks unseen for this many frames are dropped as noise. */
  staleFrames?: number;
  /** A track seen within this many frames counts as currently visible. */
  activeFrames?: number;
  /** In auto mode, no new track may appear for this many frames before finishing. */
  settleFrames?: number;
}

class Track {
  // Insertion order matters: on a tie the first value seen leads, as with Kotlin's maxBy.
  readonly votes = new Map<string, number>();
  readonly formats = new Map<string, string>();
  total = 0;

  constructor(
    readonly id: number,
    public box: Box,
    public lastSeen: number,
  ) {}

  leader(): [string, number] {
    let best: [string, number] | null = null;
    for (const entry of this.votes) if (best === null || entry[1] > best[1]) best = entry;
    return best!;
  }

  /** Votes of the leading value minus votes of the next most frequent one. */
  lead(): number {
    let first = 0;
    let second = 0;
    for (const count of this.votes.values()) {
      if (count > first) {
        second = first;
        first = count;
      } else if (count > second) {
        second = count;
      }
    }
    return first - second;
  }

  vote(detection: Detection, frame: number) {
    this.votes.set(detection.value, (this.votes.get(detection.value) ?? 0) + 1);
    this.formats.set(detection.value, detection.format);
    this.total++;
    this.box = detection.box;
    this.lastSeen = frame;
  }
}

/**
 * Accepts a barcode value only after it has been decoded identically in many frames.
 *
 * Every physical barcode on the sticker becomes a *track*, matched across frames by position
 * (and by value when the phone moves). Each frame casts one vote for the value decoded at that
 * track. A track is confirmed when its leading value is ahead of the runner-up by at least
 * `requiredVotes` votes. See ConsensusTracker.kt for the reasoning.
 */
export class ConsensusTracker {
  private readonly requiredVotes: number;
  private readonly staleFrames: number;
  private readonly activeFrames: number;
  private readonly settleFrames: number;

  /** Number of barcodes expected on the sticker, or null to finish when the scene settles. */
  expectedCount: number | null = null;

  private tracks: Track[] = [];
  private frame = 0;
  private nextId = 0;
  private lastNewTrackFrame = 0;

  constructor({ requiredVotes = 6, staleFrames = 45, activeFrames = 10, settleFrames = 20 }: TrackerOptions = {}) {
    this.requiredVotes = requiredVotes;
    this.staleFrames = staleFrames;
    this.activeFrames = activeFrames;
    this.settleFrames = settleFrames;
  }

  reset() {
    this.tracks = [];
    this.frame = 0;
    this.nextId = 0;
    this.lastNewTrackFrame = 0;
  }

  onFrame(detections: Detection[]): FrameResult {
    const frame = ++this.frame;
    const seen = new Map<number, Box>();
    const unmatched = this.assign(detections, (track, detection) => {
      track.vote(detection, frame);
      seen.set(track.id, detection.box);
    });
    for (const detection of unmatched) {
      const track = new Track(this.nextId++, detection.box, frame);
      track.vote(detection, frame);
      this.tracks.push(track);
      seen.set(track.id, detection.box);
      this.lastNewTrackFrame = frame;
    }
    this.tracks = this.tracks.filter((t) => this.isConfirmed(t) || frame - t.lastSeen <= this.staleFrames);

    return {
      tracks: this.tracks.map((t) => this.toState(t)),
      seenInFrame: seen,
      complete: this.isComplete(),
    };
  }

  /** Greedily pairs detections with existing tracks; returns detections left without a track. */
  private assign(detections: Detection[], onMatch: (track: Track, detection: Detection) => void): Detection[] {
    const candidates: { detection: Detection; track: Track; score: number }[] = [];
    for (const detection of detections) {
      for (const track of this.tracks) {
        const overlap = iou(track.box, detection.box);
        const near = isNear(track.box, detection.box);
        const knownValue = track.votes.has(detection.value);
        if (overlap < 0.2 && !near && !knownValue) continue;
        const sameLeader = track.leader()[0] === detection.value;
        const score = overlap + (near ? 0.1 : 0) + (sameLeader ? 1 : 0);
        candidates.push({ detection, track, score });
      }
    }
    candidates.sort((a, b) => b.score - a.score);

    const matchedDetections = new Set<Detection>();
    const matchedTracks = new Set<Track>();
    for (const { detection, track } of candidates) {
      if (matchedDetections.has(detection) || matchedTracks.has(track)) continue;
      matchedDetections.add(detection);
      matchedTracks.add(track);
      onMatch(track, detection);
    }
    return detections.filter((d) => !matchedDetections.has(d));
  }

  private isConfirmed(track: Track): boolean {
    return track.lead() >= this.requiredVotes;
  }

  /** An unconfirmed track that is visible now and not a one-off glitch. */
  private isPending(track: Track): boolean {
    return !this.isConfirmed(track) && track.total >= 2 && this.frame - track.lastSeen <= this.activeFrames;
  }

  private isComplete(): boolean {
    const confirmed = this.tracks.filter((t) => this.isConfirmed(t)).length;
    if (confirmed === 0 || this.tracks.some((t) => this.isPending(t))) return false;
    return this.expectedCount !== null
      ? confirmed >= this.expectedCount
      : this.frame - this.lastNewTrackFrame >= this.settleFrames;
  }

  private toState(track: Track): TrackState {
    const [value, votes] = track.leader();
    return {
      id: track.id,
      value,
      format: track.formats.get(value)!,
      box: track.box,
      confirmed: this.isConfirmed(track),
      leaderVotes: votes,
      totalVotes: track.total,
    };
  }
}

/** Orders items in reading order: rows top to bottom, left to right within a row. */
export function sortedInReadingOrder<T>(items: T[], box: (item: T) => Box): T[] {
  const remaining = [...items].sort((a, b) => centerY(box(a)) - centerY(box(b)));
  const result: T[] = [];
  while (remaining.length > 0) {
    const first = box(remaining[0]);
    const row = remaining.filter((it) => Math.abs(centerY(box(it)) - centerY(first)) < height(first) / 2);
    for (const it of row) remaining.splice(remaining.indexOf(it), 1);
    result.push(...row.sort((a, b) => centerX(box(a)) - centerX(box(b))));
  }
  return result;
}
