import { S } from "../strings";
import { toast } from "./dom";

export async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // Older browsers, or the Clipboard API blocked: fall back to a hidden textarea.
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.append(area);
    area.select();
    document.execCommand("copy");
    area.remove();
  }
  toast(S.copied);
}
