// Port of app/src/main/java/com/gg/barcodes/scan/BarcodeValue.kt.

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
