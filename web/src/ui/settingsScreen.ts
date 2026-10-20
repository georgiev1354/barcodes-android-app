// Counterpart of SettingsScreen.kt. The app's update section is replaced by version info.

import { DEFAULT_FORMATS, SCAN_FORMATS } from "../scan/formats";
import type { DecoderKind } from "../scan/protocol";
import type { ScanSession } from "../scan/session";
import { S } from "../strings";
import { h, icon } from "./dom";

export function settingsScreen(session: ScanSession, decoder: DecoderKind | null, onBack: () => void): HTMLElement {
  const back = h("button", { className: "icon-button", type: "button", title: S.back }, icon("back"));
  back.setAttribute("aria-label", S.back);
  back.onclick = onBack;

  const formatRows = h("div", {});
  const resetDefaults = h("button", { className: "text", type: "button" }, S.settingsFormatsDefault);
  resetDefaults.onclick = () => {
    session.setFormats([...DEFAULT_FORMATS]);
    renderFormats();
  };

  const renderFormats = () => {
    const selected = session.formats;
    formatRows.replaceChildren(
      ...SCAN_FORMATS.map((format) => {
        const checked = selected.includes(format.id);
        // At least one format must stay on, or nothing could ever be scanned.
        const box = h("input", { type: "checkbox", checked, disabled: checked && selected.length === 1 });
        box.onchange = () => {
          session.setFormats(box.checked ? [...selected, format.id] : selected.filter((id) => id !== format.id));
          renderFormats();
        };
        return h(
          "label",
          { className: "check-row" },
          box,
          h("div", {}, h("div", {}, format.label), format.weakCheck && h("div", { className: "small muted" }, S.settingsFormatWeak)),
        );
      }),
    );
    const isDefault = selected.length === DEFAULT_FORMATS.length && DEFAULT_FORMATS.every((id) => selected.includes(id));
    resetDefaults.hidden = isDefault;
  };
  renderFormats();

  const decoderName = decoder === "native" ? S.decoderNative : decoder === "zxing" ? S.decoderZxing : S.decoderUnknown;

  return h(
    "div",
    { className: "screen page settings" },
    h("div", { className: "row header" }, back, h("h1", {}, S.settingsTitle)),
    h("h2", {}, S.settingsFormats),
    h("p", { className: "small muted" }, S.settingsFormatsHint),
    formatRows,
    resetDefaults,
    h("hr"),
    h("h2", {}, S.settingsResult),
    switchRow(S.settingsStripLabels, S.settingsStripLabelsSummary, session.stripLabels, (on) => session.setStripLabels(on)),
    switchRow(S.settingsReadableWifi, S.settingsReadableWifiSummary, session.readableWifi, (on) =>
      session.setReadableWifi(on),
    ),
    h("hr"),
    h("h2", {}, S.settingsApp),
    h("div", { className: "info-row" }, h("div", {}, S.settingsVersion(__APP_VERSION__))),
    h("div", { className: "info-row" }, h("div", {}, S.settingsDecoder), h("div", { className: "small muted" }, decoderName)),
  );
}

function switchRow(title: string, summary: string, checked: boolean, onChange: (on: boolean) => void): HTMLElement {
  const input = h("input", { type: "checkbox", className: "switch", checked });
  input.setAttribute("role", "switch");
  input.onchange = () => onChange(input.checked);
  return h(
    "label",
    { className: "switch-row" },
    h("div", { className: "grow" }, h("div", {}, title), h("div", { className: "small muted" }, summary)),
    input,
  );
}
