// Same cases as app/src/test/java/com/gg/barcodes/scan/BarcodeValueTest.kt.

import { describe, expect, it } from "vitest";
import { cleanValue } from "../src/scan/value";

describe("cleanValue", () => {
  it("removes the symbology identifier", () => {
    expect(cleanValue("]C12430011117002865", false)).toBe("2430011117002865");
    expect(cleanValue("]E04006381333931", false)).toBe("4006381333931");
  });

  it("removes labels when enabled", () => {
    for (const raw of [
      "S/N: 2430011117002865",
      "s/n:2430011117002865",
      "S/N 2430011117002865",
      "SN: 2430011117002865",
      "P/N#2430011117002865",
      "]C1S/N: 2430011117002865",
    ]) {
      expect(cleanValue(raw, true), raw).toBe("2430011117002865");
    }
  });

  it("keeps labels when disabled", () => {
    expect(cleanValue("S/N: 123", false)).toBe("S/N: 123");
  });

  it("keeps values that only look like labels", () => {
    expect(cleanValue("SN1234", true)).toBe("SN1234");
    expect(cleanValue("PN-55", true)).toBe("PN-55");
    expect(cleanValue("ABC]C1", true)).toBe("ABC]C1");
  });

  it("returns null when nothing is left", () => {
    expect(cleanValue("]C1", true)).toBeNull();
    expect(cleanValue("S/N: ", true)).toBeNull();
  });
});
