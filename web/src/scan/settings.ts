// Port of app/src/main/java/com/gg/barcodes/scan/ScanSettings.kt, stored in localStorage.

import { DEFAULT_FORMATS, SCAN_FORMATS } from "./formats";

const KEY_FORMATS = "barcodes.formats";
const KEY_STRIP_LABELS = "barcodes.stripLabels";
const KEY_READABLE_WIFI = "barcodes.readableWifi";

// Private mode or blocked storage throws; the defaults then apply for this session only.
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore
  }
}

/** Format ids; never empty, falls back to the defaults. */
export function loadFormats(): string[] {
  try {
    const ids = (JSON.parse(read(KEY_FORMATS) ?? "null") as string[] | null)?.filter((id) =>
      SCAN_FORMATS.some((f) => f.id === id),
    );
    if (ids && ids.length > 0) return ids;
  } catch {
    // fall through
  }
  return [...DEFAULT_FORMATS];
}

export function saveFormats(ids: string[]) {
  write(KEY_FORMATS, JSON.stringify(ids));
}

/** Drop "S/N:", "P/N:" and similar labels in front of the value. */
export function loadStripLabels(): boolean {
  return read(KEY_STRIP_LABELS) !== "false";
}

export function saveStripLabels(on: boolean) {
  write(KEY_STRIP_LABELS, String(on));
}

/** Show Wi-Fi QR codes as network name and password instead of the raw text. */
export function loadReadableWifi(): boolean {
  return read(KEY_READABLE_WIFI) !== "false";
}

export function saveReadableWifi(on: boolean) {
  write(KEY_READABLE_WIFI, String(on));
}
