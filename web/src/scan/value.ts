// Port of app/src/main/java/com/gg/barcodes/scan/BarcodeValue.kt.

import type { Detection } from "./consensus";

// AIM symbology identifier, e.g. "]C1" in front of a GS1-128 code. It describes the symbol,
// it is not part of the data.
const symbologyId = /^\][A-Za-z][0-9A-Za-z]/;

// A printed-style label in front of the value: "S/N:", "S/N 123", "SN: 123", "P/N#123".
// "SN"/"PN" without a separator are left alone, since "SN1234" may well be the value itself.
const label = /^\s*(?:[SP]\s*\/\s*N\s*[:#.]?|[SP]N\s*[:#.])\s*/i;

/** Turns a decoded value into what the user wants to copy; null if nothing is left. */
export function cleanValue(raw: string, stripLabels: boolean): string | null {
  let value = raw.replace(symbologyId, "");
  if (stripLabels) value = value.replace(label, "");
  return value.trim() === "" ? null : value;
}

/**
 * Applies what is known about the detection's format. An ITF value is digits in pairs, at
 * least 6 of them: anything else is a misread (often a partial scan) and gives null. An
 * ITF-14 whose GTIN check digit is valid counts as checked, so it confirms as fast as Code 128.
 */
export function checkFormat(detection: Detection): Detection | null {
  if (detection.format !== "ITF") return detection;
  const value = detection.value;
  if (value.length < 6 || value.length % 2 !== 0 || !/^[0-9]+$/.test(value)) return null;
  return value.length === 14 && hasValidGtinCheckDigit(value) ? { ...detection, weakCheck: false } : detection;
}

/** GS1 mod-10: weights 3, 1, 3, … from the digit left of the check digit. */
function hasValidGtinCheckDigit(digits: string): boolean {
  let sum = 0;
  for (let i = 0; i < digits.length - 1; i++) {
    const fromRight = digits.length - 2 - i;
    sum += Number(digits[i]) * (fromRight % 2 === 0 ? 3 : 1);
  }
  return (10 - (sum % 10)) % 10 === Number(digits[digits.length - 1]);
}
