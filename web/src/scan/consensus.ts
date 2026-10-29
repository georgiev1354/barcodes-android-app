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

/** Share of [box]'s area that lies inside [other]. */
export function coveredBy(box: Box, other: Box): number {
  const ix = Math.max(0, Math.min(box.right, other.right) - Math.max(box.left, other.left));
  const iy = Math.max(0, Math.min(box.bottom, other.bottom) - Math.max(box.top, other.top));
  const area = width(box) * height(box);
  return area <= 0 ? 0 : (ix * iy) / area;
}

/** True when [other]'s center lies within half a width/height of [box]'s center. */
export function isNear(box: Box, other: Box): boolean {
  return (
    Math.abs(centerX(box) - centerX(other)) < width(box) / 2 &&
    Math.abs(centerY(box) - centerY(other)) < height(box) / 2
  );
}

/**
 * One barcode decoded in one camera frame. `weakCheck` marks a format without a mandatory check
 * digit (Code 39, Codabar, ITF), whose values the tracker accepts only on stricter terms.
 * `textConfirmed` marks a value that the text printed next to the barcode reads the same.
 */
export interface Detection {
  value: string;
  format: string;
  box: Box;
  weakCheck?: boolean;
  textConfirmed?: boolean;
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
  /** Lead needed when the leading value is of a format without a check digit. */
  weakRequiredVotes?: number;
  /** Values of formats without a check digit shorter than this are dropped as noise. */
  minWeakLength?: number;
  /** Frames whose printed text must match a weak value before it needs only `requiredVotes`. */
  minTextVotes?: number;
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
  readonly weakValues = new Set<string>();
  readonly textVotes = new Map<string, number>();
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
    if (detection.weakCheck) this.weakValues.add(detection.value);
    if (detection.textConfirmed) this.textVotes.set(detection.value, (this.textVotes.get(detection.value) ?? 0) + 1);
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
 * `requiredVotes` votes (`weakRequiredVotes` for a format without a check digit, whose values
 * shorter than `minWeakLength`, or whose box covers most of two or more other barcodes, are
 * dropped, and which needs only `requiredVotes` once the printed text has matched it in
 * `minTextVotes` frames). See ConsensusTracker.kt for the reasoning.
 */
export class ConsensusTracker {
  private readonly requiredVotes: number;
  private readonly weakRequiredVotes: number;
  private readonly minWeakLength: number;
  private readonly minTextVotes: number;
  private readonly staleFrames: number;
  private readonly activeFrames: number;
  private readonly settleFrames: number;

  /** Number of barcodes expected on the sticker, or null to finish when the scene settles. */
  expectedCount: number | null = null;

  private tracks: Track[] = [];
  private frame = 0;
  private nextId = 0;
  private lastNewTrackFrame = 0;

  constructor({
    requiredVotes = 6,
    weakRequiredVotes = 10,
    minWeakLength = 3,
    minTextVotes = 2,
    staleFrames = 45,
    activeFrames = 10,
    settleFrames = 20,
  }: TrackerOptions = {}) {
    this.requiredVotes = requiredVotes;
    this.weakRequiredVotes = weakRequiredVotes;
    this.minWeakLength = minWeakLength;
    this.minTextVotes = minTextVotes;
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

  onFrame(frameDetections: Detection[]): FrameResult {
    const frame = ++this.frame;
    const detections = frameDetections.filter(
      (d) => !d.weakCheck || (d.value.length >= this.minWeakLength && !this.isCrossRead(d, frameDetections)),
    );
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

  private isCrossRead(detection: Detection, frameDetections: Detection[]): boolean {
    const others = [
      ...this.tracks.map((t) => t.box),
      ...frameDetections.filter((d) => d !== detection).map((d) => d.box),
    ];
    return others.filter((b) => coveredBy(b, detection.box) >= 0.5).length >= 2;
  }

  private isConfirmed(track: Track): boolean {
    const leader = track.leader()[0];
    const weak = track.weakValues.has(leader) && (track.textVotes.get(leader) ?? 0) < this.minTextVotes;
    const required = weak ? this.weakRequiredVotes : this.requiredVotes;
    return track.lead() >= required;
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
