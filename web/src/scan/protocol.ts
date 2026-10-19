// Messages between the page and decoder.worker.ts.

import type { Detection } from "./consensus";

export type DecoderKind = "native" | "zxing";

export type ToWorker =
  | { type: "config"; formats: string[]; forceZxing: boolean }
  | { type: "frame"; bitmap: ImageBitmap; enhanced: boolean };

/** The frame's bitmap comes back so the page can keep it as the result image. */
export type FromWorker = { type: "result"; bitmap: ImageBitmap; detections: Detection[]; decoder: DecoderKind };
