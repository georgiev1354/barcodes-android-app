// Port of app/src/main/java/com/gg/barcodes/scan/WifiCode.kt.

/**
 * A Wi-Fi network QR code: `WIFI:S:<ssid>;T:<WPA|WEP|SAE|nopass>;P:<password>;H:<true>;;`.
 * `\`, `;`, `,`, `:` and `"` inside values are escaped with a backslash.
 */
export interface WifiCode {
  ssid: string;
  password: string | null;
  /** As written in the code (WPA, WEP, SAE, nopass…), or null if absent. */
  security: string | null;
  hidden: boolean;
}

const PREFIX = "WIFI:";

export function isOpen(code: WifiCode): boolean {
  return code.security?.toLowerCase() === "nopass" || !code.password;
}

/** Returns null for anything that isn't a Wi-Fi code with a network name. */
export function parseWifi(value: string): WifiCode | null {
  if (value.slice(0, PREFIX.length).toUpperCase() !== PREFIX) return null;
  const fields = new Map<string, string>();
  for (const field of splitUnescaped(value.slice(PREFIX.length), ";")) {
    const colon = indexOfUnescaped(field, ":");
    if (colon <= 0) continue;
    const key = field.slice(0, colon).toUpperCase();
    if (!fields.has(key)) fields.set(key, unescape(field.slice(colon + 1)));
  }
  const ssid = unquoted(fields.get("S") ?? "");
  if (!ssid) return null;
  const password = unquoted(fields.get("P") ?? "");
  return {
    ssid,
    password: password || null,
    security: fields.get("T") || null,
    hidden: fields.get("H")?.toLowerCase() === "true",
  };
}

function splitUnescaped(text: string, separator: string): string[] {
  const parts: string[] = [];
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "\\") i++;
    else if (text[i] === separator) {
      parts.push(text.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(text.slice(Math.min(start, text.length)));
  return parts.filter((p) => p.length > 0);
}

function indexOfUnescaped(text: string, char: string): number {
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "\\") i++;
    else if (text[i] === char) return i;
  }
  return -1;
}

function unescape(text: string): string {
  let out = "";
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "\\" && i + 1 < text.length) i++;
    out += text[i];
  }
  return out;
}

function unquoted(text: string): string {
  return text.length >= 2 && text.startsWith('"') && text.endsWith('"') ? text.slice(1, -1) : text;
}
