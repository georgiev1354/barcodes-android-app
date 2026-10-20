// Counterpart of ScanViewModel.kt: feeds camera frames to the decoder worker, runs the consensus
// tracker and builds the annotated result.

import { ConsensusTracker, sortedInReadingOrder, type Box, type TrackState } from "./consensus";
import type { DecoderKind, FromWorker, ToWorker } from "./protocol";
import * as settings from "./settings";
import { checkFormat, cleanValue } from "./value";

export interface LiveScan {
  imageWidth: number;
  imageHeight: number;
  tracks: TrackState[];
  decoder: DecoderKind | null;
}

export interface ScannedBarcode {
  number: number;
  value: string;
  format: string;
}

/** Final result: the frozen, annotated camera frame plus the confirmed values. */
export interface ScanResult {
  image: HTMLCanvasElement;
  barcodes: ScannedBarcode[];
}

const CONFIRMED_COLOR = "#00C853";

export class ScanSession {
  formats = settings.loadFormats();
  stripLabels = settings.loadStripLabels();
  readableWifi = settings.loadReadableWifi();
  expectedCount: number | null = null;
  /** Last camera zoom, re-applied when the camera starts again. */
  zoomRatio = 1;

  onLive: (live: LiveScan) => void = () => {};
  onResult: (result: ScanResult) => void = () => {};

  private readonly worker: Worker;
  private readonly tracker = new ConsensusTracker();
  private readonly forceZxing = new URLSearchParams(location.search).get("decoder") === "zxing";

  private video: HTMLVideoElement | null = null;
  private busy = false;
  private frameCount = 0;
  /** Bumped on restart so a frame decoded before it is ignored. */
  private generation = 0;
  private inFlightGeneration = 0;
  private finished = false;
  /** Decoder the worker used for the last frame; null before the first one. */
  decoder: DecoderKind | null = null;

  // Frame in which the most tracks were visible at once; becomes the result image.
  private bestFrame: ImageBitmap | null = null;
  private bestFrameBoxes = new Map<number, Box>();

  constructor() {
    this.worker = new Worker(new URL("./decoder.worker.ts", import.meta.url), { type: "module" });
    this.worker.onmessage = (event: MessageEvent<FromWorker>) => this.onDecoded(event.data);
    this.sendConfig();
  }

  /** Starts pulling frames from [video]; null stops. */
  attach(video: HTMLVideoElement | null) {
    this.video = video;
    if (video) this.pump();
  }

  setFormats(ids: string[]) {
    if (ids.length === 0 || sameSet(ids, this.formats)) return;
    this.formats = ids;
    settings.saveFormats(ids);
    this.sendConfig();
    // Tracks collected under the old formats shouldn't count towards the next result.
    this.restart();
  }

  setStripLabels(on: boolean) {
    if (on === this.stripLabels) return;
    this.stripLabels = on;
    settings.saveStripLabels(on);
    // Votes for "S/N: 123" and "123" would otherwise end up in the same track as rivals.
    this.restart();
  }

  /** Display only: scanning and the decoded values are unaffected. */
  setReadableWifi(on: boolean) {
    this.readableWifi = on;
    settings.saveReadableWifi(on);
  }

  restart() {
    this.generation++;
    this.tracker.reset();
    this.bestFrame?.close();
    this.bestFrame = null;
    this.bestFrameBoxes = new Map();
    this.finished = false;
    this.onLive({ imageWidth: 0, imageHeight: 0, tracks: [], decoder: this.decoder });
    if (this.video) this.pump();
  }

  private sendConfig() {
    const message: ToWorker = { type: "config", formats: this.formats, forceZxing: this.forceZxing };
    this.worker.postMessage(message);
  }

  /** Sends the next frame once the worker is idle: one frame in flight gives the back-pressure. */
  private pump() {
    const video = this.video;
    if (!video || this.busy || this.finished) return;
    if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0) {
      requestAnimationFrame(() => this.pump());
      return;
    }
    this.busy = true;
    const generation = this.generation;
    createImageBitmap(video).then(
      (bitmap) => {
        if (generation !== this.generation || !this.video) {
          bitmap.close();
          this.busy = false;
          this.pump();
          return;
        }
        this.inFlightGeneration = generation;
        const message: ToWorker = { type: "frame", bitmap, enhanced: this.frameCount++ % 2 === 1 };
        this.worker.postMessage(message, [bitmap]);
      },
      () => {
        this.busy = false;
        requestAnimationFrame(() => this.pump());
      },
    );
  }

  private onDecoded({ bitmap, detections, decoder }: FromWorker) {
    this.busy = false;
    this.decoder = decoder;
    // Frames still in flight across a restart or after the scan finished are dropped.
    if (this.finished || this.inFlightGeneration !== this.generation) {
      bitmap.close();
      this.pump();
      return;
    }

    this.tracker.expectedCount = this.expectedCount;
    const frame = this.tracker.onFrame(
      detections.flatMap((d) => {
        const value = cleanValue(d.value, this.stripLabels);
        const checked = value === null ? null : checkFormat({ ...d, value });
        return checked === null ? [] : [checked];
      }),
    );

    if (frame.seenInFrame.size > 0 && frame.seenInFrame.size >= this.bestFrameBoxes.size) {
      this.bestFrame?.close();
      this.bestFrame = bitmap;
      this.bestFrameBoxes = frame.seenInFrame;
    } else {
      bitmap.close();
    }
    this.onLive({ imageWidth: bitmap.width, imageHeight: bitmap.height, tracks: frame.tracks, decoder });

    if (frame.complete) {
      this.finished = true;
      this.onResult(this.buildResult(frame.tracks));
    } else {
      this.pump();
    }
  }

  private buildResult(tracks: TrackState[]): ScanResult {
    const confirmed = sortedInReadingOrder(
      tracks.filter((t) => t.confirmed).map((t) => ({ ...t, box: this.bestFrameBoxes.get(t.id) ?? t.box })),
      (t) => t.box,
    );
    const image = annotate(this.bestFrame!, confirmed);
    const barcodes = confirmed.map((t, i) => ({ number: i + 1, value: t.value, format: t.format }));
    return { image, barcodes };
  }
}

/** Draws each confirmed box with its list number onto a copy of the frame. */
function annotate(frame: ImageBitmap, tracks: TrackState[]): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = frame.width;
  canvas.height = frame.height;
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(frame, 0, 0);
  const unit = Math.max(canvas.width, canvas.height) / 400;
  ctx.lineWidth = 2.5 * unit;
  ctx.strokeStyle = CONFIRMED_COLOR;
  ctx.font = `bold ${10 * unit}px sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  tracks.forEach((track, i) => {
    const b = track.box;
    ctx.strokeRect(b.left, b.top, b.right - b.left, b.bottom - b.top);
    ctx.fillStyle = CONFIRMED_COLOR;
    ctx.beginPath();
    ctx.arc(b.left, b.top, 8 * unit, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#fff";
    ctx.fillText(String(i + 1), b.left, b.top);
  });
  return canvas;
}

function sameSet(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((x) => b.includes(x));
}
