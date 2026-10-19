/// <reference lib="webworker" />
// Decodes camera frames off the main thread. Counterpart of BarcodeAnalyzer.kt.
//
// The browser's BarcodeDetector is used when it supports every selected format (Chrome on Android,
// where it is backed by the same ML Kit model as the app). Otherwise ZXing-C++ compiled to
// WebAssembly is used (iOS Safari, Firefox, desktop). Every other frame is decoded from a
// contrast-enhanced grayscale copy for faded print, as in the app.

import wasmUrl from "zxing-wasm/reader/zxing_reader.wasm?url";
import type { ReaderOptions } from "zxing-wasm/reader";
import type { Box, Detection } from "./consensus";
import { enhance } from "./enhance";
import { formatsById, labelFor, type ScanFormat } from "./formats";
import type { DecoderKind, FromWorker, ToWorker } from "./protocol";

declare const self: DedicatedWorkerGlobalScope;

type ReadBarcodes = typeof import("zxing-wasm/reader").readBarcodes;

let formats: ScanFormat[] = [];
let forceZxing = false;
let native: BarcodeDetector | null = null;
let nativeChecked = false;
let readBarcodes: ReadBarcodes | null = null;
let canvas: OffscreenCanvas | null = null;
let context: OffscreenCanvasRenderingContext2D | null = null;

self.onmessage = async (event: MessageEvent<ToWorker>) => {
  const message = event.data;
  if (message.type === "config") {
    formats = formatsById(message.formats);
    forceZxing = message.forceZxing;
    native = null;
    nativeChecked = false;
    return;
  }
  const { bitmap, enhanced } = message;
  let detections: Detection[] = [];
  let decoder: DecoderKind = "zxing";
  try {
    const detector = await nativeDetector();
    if (detector) {
      decoder = "native";
      detections = await decodeNative(detector, bitmap, enhanced);
    } else {
      detections = await decodeZxing(bitmap, enhanced);
    }
  } catch (error) {
    if (decoder === "native") {
      // Seen on some devices: the detector exists but throws. Fall back for good.
      forceZxing = true;
      native = null;
    }
    console.warn("decode failed", error);
  }
  const reply: FromWorker = { type: "result", bitmap, detections, decoder };
  self.postMessage(reply, [bitmap]);
};

async function nativeDetector(): Promise<BarcodeDetector | null> {
  if (nativeChecked) return native;
  nativeChecked = true;
  if (forceZxing || !("BarcodeDetector" in self)) return null;
  try {
    const supported = new Set(await BarcodeDetector.getSupportedFormats());
    const wanted = formats.map((f) => f.detector);
    if (wanted.every((f) => supported.has(f))) native = new BarcodeDetector({ formats: wanted });
  } catch {
    native = null;
  }
  return native;
}

function pixels(bitmap: ImageBitmap): ImageData {
  if (!canvas || canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
    canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
    context = canvas.getContext("2d", { willReadFrequently: true });
  }
  context!.drawImage(bitmap, 0, 0);
  return context!.getImageData(0, 0, bitmap.width, bitmap.height);
}

async function decodeNative(detector: BarcodeDetector, bitmap: ImageBitmap, enhanced: boolean) {
  let source: ImageBitmapSource = bitmap;
  if (enhanced) {
    const image = pixels(bitmap);
    enhance(image.data);
    source = image;
  }
  const barcodes = await detector.detect(source);
  return barcodes
    .filter((b) => b.rawValue)
    .map<Detection>((b) => ({
      value: b.rawValue,
      format: labelFor(b.format),
      box: { left: b.boundingBox.left, top: b.boundingBox.top, right: b.boundingBox.right, bottom: b.boundingBox.bottom },
    }));
}

async function decodeZxing(bitmap: ImageBitmap, enhanced: boolean) {
  if (!readBarcodes) {
    const zxing = await import("zxing-wasm/reader");
    zxing.prepareZXingModule({
      overrides: { locateFile: (path: string, prefix: string) => (path.endsWith(".wasm") ? wasmUrl : prefix + path) },
    });
    readBarcodes = zxing.readBarcodes;
  }
  const image = pixels(bitmap);
  if (enhanced) enhance(image.data);
  const options: ReaderOptions = {
    formats: formats.map((f) => f.zxing) as ReaderOptions["formats"],
    tryHarder: true,
    tryRotate: true,
    tryInvert: false,
    maxNumberOfSymbols: 8,
    // Raw content like ML Kit's rawValue, not "(01)…" human-readable GS1 text.
    textMode: "Plain",
  };
  const results = await readBarcodes(image, options);
  return results
    .filter((r) => r.isValid && r.text)
    .map<Detection>((r) => ({ value: r.text, format: labelFor(r.format), box: boundingBox(r.position) }));
}

function boundingBox(position: { topLeft: Point; topRight: Point; bottomLeft: Point; bottomRight: Point }): Box {
  const points = [position.topLeft, position.topRight, position.bottomLeft, position.bottomRight];
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  let box = { left: Math.min(...xs), top: Math.min(...ys), right: Math.max(...xs), bottom: Math.max(...ys) };
  // ZXing reports a 1D code as a thin line across the bars; give it some height so the tracker's
  // IoU/proximity matching behaves as with ML Kit's boxes.
  const minHeight = (box.right - box.left) * 0.15;
  if (box.bottom - box.top < minHeight) {
    const cy = (box.top + box.bottom) / 2;
    box = { ...box, top: cy - minHeight / 2, bottom: cy + minHeight / 2 };
  }
  return box;
}

interface Point {
  x: number;
  y: number;
}
