// Port of app/src/main/java/com/gg/barcodes/scan/ScanFormat.kt, with the names each web decoder uses.

export interface ScanFormat {
  id: string;
  label: string;
  /** ZXing-C++ format name (zxing-wasm). */
  zxing: string;
  /** Shape Detection API format name (BarcodeDetector). */
  detector: string;
  /**
   * No mandatory check digit: a partly faded code can decode as a wrong but valid-looking value,
   * so the tracker needs a larger lead before accepting it.
   */
  weakCheck?: boolean;
}

/** In the order shown in settings (most common first). */
export const SCAN_FORMATS: ScanFormat[] = [
  { id: "CODE_128", label: "Code 128", zxing: "Code128", detector: "code_128" },
  { id: "CODE_39", label: "Code 39", zxing: "Code39", detector: "code_39", weakCheck: true },
  { id: "ITF", label: "ITF", zxing: "ITF", detector: "itf", weakCheck: true },
  { id: "EAN_13", label: "EAN-13", zxing: "EAN13", detector: "ean_13" },
  { id: "QR", label: "QR", zxing: "QRCode", detector: "qr_code" },
  { id: "EAN_8", label: "EAN-8", zxing: "EAN8", detector: "ean_8" },
  { id: "UPC_A", label: "UPC-A", zxing: "UPCA", detector: "upc_a" },
  { id: "UPC_E", label: "UPC-E", zxing: "UPCE", detector: "upc_e" },
  { id: "CODE_93", label: "Code 93", zxing: "Code93", detector: "code_93" },
  { id: "CODABAR", label: "Codabar", zxing: "Codabar", detector: "codabar", weakCheck: true },
  { id: "DATA_MATRIX", label: "Data Matrix", zxing: "DataMatrix", detector: "data_matrix" },
  { id: "PDF417", label: "PDF417", zxing: "PDF417", detector: "pdf417" },
  { id: "AZTEC", label: "Aztec", zxing: "Aztec", detector: "aztec" },
];

/** What the stickers carry. Every extra format is another way to misread a faded 1D code. */
export const DEFAULT_FORMATS: readonly string[] = ["CODE_128", "CODE_39", "ITF", "EAN_13", "QR"];

export function formatsById(ids: Iterable<string>): ScanFormat[] {
  const wanted = new Set(ids);
  return SCAN_FORMATS.filter((f) => wanted.has(f.id));
}

/** Whether a decoder's format name is one without a mandatory check digit. */
export function isWeakCheck(name: string): boolean {
  return formatFor(name)?.weakCheck ?? false;
}

/**
 * Label for a decoder's format name. ZXing reports variants (e.g. "QRCodeModel2", "AztecCode"),
 * so a prefix match against the symbology name is accepted too.
 */
export function labelFor(name: string): string {
  return formatFor(name)?.label ?? name;
}

function formatFor(name: string): ScanFormat | undefined {
  return (
    SCAN_FORMATS.find((f) => f.zxing === name || f.detector === name) ??
    SCAN_FORMATS.find((f) => name.startsWith(f.zxing))
  );
}
