// Same cases as app/src/test/java/com/gg/barcodes/scan/WifiCodeTest.kt.

import { describe, expect, it } from "vitest";
import { isOpen, parseWifi } from "../src/scan/wifi";

describe("parseWifi", () => {
  it("parses a typical router sticker", () => {
    expect(parseWifi("WIFI:S:ZTE_2.4G_Q5754N;T:WPA;P:PRyK9EtQ;;")).toEqual({
      ssid: "ZTE_2.4G_Q5754N",
      password: "PRyK9EtQ",
      security: "WPA",
      hidden: false,
    });
  });

  it("ignores field order and case", () => {
    const code = parseWifi("wifi:p:secret;s:Home;t:WPA;h:true;;")!;
    expect(code.ssid).toBe("Home");
    expect(code.password).toBe("secret");
    expect(code.hidden).toBe(true);
  });

  it("unescapes special characters", () => {
    const code = parseWifi(String.raw`WIFI:S:My\;Net\:5G;T:WPA;P:pa\\ss\;word\,1;;`)!;
    expect(code.ssid).toBe("My;Net:5G");
    expect(code.password).toBe(String.raw`pa\ss;word,1`);
  });

  it("strips quotes", () => {
    expect(parseWifi('WIFI:S:"Cafe Wifi";T:nopass;;')!.ssid).toBe("Cafe Wifi");
  });

  it("treats an open network as having no password", () => {
    const code = parseWifi("WIFI:T:nopass;S:Guest;;")!;
    expect(code.password).toBeNull();
    expect(isOpen(code)).toBe(true);
    expect(isOpen(parseWifi("WIFI:S:x;T:WPA;P:y;;")!)).toBe(false);
  });

  it("rejects other values", () => {
    expect(parseWifi("2430011117002865")).toBeNull();
    expect(parseWifi("https://example.com")).toBeNull();
    expect(parseWifi("WIFI:T:WPA;P:secret;;")).toBeNull();
  });
});
