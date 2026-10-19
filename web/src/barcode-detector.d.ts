// Shape Detection API, not yet in TypeScript's DOM lib.
// https://developer.mozilla.org/docs/Web/API/BarcodeDetector

interface DetectedBarcode {
  boundingBox: DOMRectReadOnly;
  cornerPoints: { x: number; y: number }[];
  format: string;
  rawValue: string;
}

declare class BarcodeDetector {
  constructor(options?: { formats: string[] });
  static getSupportedFormats(): Promise<string[]>;
  detect(image: ImageBitmapSource): Promise<DetectedBarcode[]>;
}

declare const __APP_VERSION__: string;
