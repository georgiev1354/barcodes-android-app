// Counterpart of ResultScreen.kt: annotated frame plus copyable values.

import type { ScanResult, ScannedBarcode } from "../scan/session";
import { isOpen, parseWifi, type WifiCode } from "../scan/wifi";
import { S } from "../strings";
import { copyToClipboard } from "./clipboard";
import { h, icon } from "./dom";

export function resultScreen(result: ScanResult, readableWifi: boolean, onScanAgain: () => void): HTMLElement {
  result.image.className = "result-image";
  result.image.setAttribute("role", "img");
  result.image.setAttribute("aria-label", S.resultImageDescription);

  const cards = result.barcodes.map((barcode) => {
    const wifi = readableWifi ? parseWifi(barcode.value) : null;
    return wifi ? wifiCard(barcode, wifi) : plainCard(barcode);
  });

  const copyAll = h("button", { className: "tonal", type: "button" }, S.copyAll);
  // Always the raw values, one per line.
  copyAll.onclick = () => copyToClipboard(result.barcodes.map((b) => b.value).join("\n"));
  const again = h("button", { className: "filled", type: "button" }, S.scanAgain);
  again.onclick = onScanAgain;

  return h(
    "div",
    { className: "screen page" },
    h("h1", {}, S.resultTitle),
    result.image,
    ...cards,
    h("div", { className: "actions" }, copyAll, again),
  );
}

function plainCard(barcode: ScannedBarcode): HTMLElement {
  const card = h(
    "div",
    { className: "card clickable" },
    number(barcode.number),
    h("div", { className: "grow" }, h("div", { className: "value" }, barcode.value), h("div", { className: "small" }, barcode.format)),
    copyButton(() => copyToClipboard(barcode.value)),
  );
  card.onclick = () => copyToClipboard(barcode.value);
  return card;
}

/** A Wi-Fi QR code split into name, password and security, each copyable, plus the raw text. */
function wifiCard(barcode: ScannedBarcode, wifi: WifiCode): HTMLElement {
  return h(
    "div",
    { className: "card column" },
    h(
      "div",
      { className: "row" },
      number(barcode.number),
      h("div", {}, h("div", { className: "title" }, S.wifiTitle), h("div", { className: "small" }, barcode.format)),
    ),
    h(
      "div",
      { className: "fields" },
      labeledValue(S.wifiSsid, wifi.ssid, () => copyToClipboard(wifi.ssid)),
      wifi.password !== null && labeledValue(S.wifiPassword, wifi.password, () => copyToClipboard(wifi.password!)),
      labeledValue(S.wifiSecurity, securityLabel(wifi), null),
      wifi.hidden && labeledValue(S.wifiHidden, S.wifiHiddenYes, null),
      labeledValue(S.wifiRaw, barcode.value, () => copyToClipboard(barcode.value), true),
    ),
  );
}

function securityLabel(wifi: WifiCode): string {
  const security = wifi.security?.toUpperCase();
  if (isOpen(wifi)) return S.wifiOpen;
  if (security === "WPA") return "WPA/WPA2";
  if (security === "SAE") return "WPA3";
  return wifi.security ?? "—";
}

function labeledValue(label: string, value: string, onCopy: (() => void) | null, small = false): HTMLElement {
  return h(
    "div",
    { className: "row labeled" },
    h("div", { className: "grow" }, h("div", { className: "label" }, label), h("div", { className: small ? "value small" : "value" }, value)),
    onCopy && copyButton(onCopy),
  );
}

function number(n: number): HTMLElement {
  return h("div", { className: "number" }, String(n));
}

function copyButton(onClick: () => void): HTMLElement {
  const button = h("button", { className: "icon-button", type: "button", title: S.copy }, icon("copy"));
  button.setAttribute("aria-label", S.copy);
  button.onclick = (e) => {
    e.stopPropagation();
    onClick();
  };
  return button;
}
