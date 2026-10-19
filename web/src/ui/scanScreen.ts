// Counterpart of ScanScreen.kt: camera preview, track overlay, expected count, zoom and torch.

import type { LiveScan, ScanSession } from "../scan/session";
import { S } from "../strings";
import { Camera } from "./camera";
import { h, icon } from "./dom";

const CONFIRMED_COLOR = "#00C853";
const PENDING_COLOR = "#FFAB00";
const ZOOM_PRESETS = [1, 2, 4];

export interface ScanScreen {
  element: HTMLElement;
  dispose: () => void;
}

export function scanScreen(session: ScanSession, camera: Camera, onShowSettings: () => void): ScanScreen {
  const video = h("video", { className: "preview", muted: true, playsInline: true, autoplay: true });
  // Safari needs the attributes, not just the properties, to play inline without a gesture.
  video.setAttribute("muted", "");
  video.setAttribute("playsinline", "");
  video.srcObject = camera.stream;
  void video.play().catch(() => {});

  const overlay = h("canvas", { className: "overlay" });
  let live: LiveScan = { imageWidth: 0, imageHeight: 0, tracks: [], decoder: null };

  // Top bar: expected count, settings, torch.
  const countChips = ([null, 2, 3, 4] as (number | null)[]).map((count) => {
    const chip = h("button", { className: "chip", type: "button" }, count?.toString() ?? S.expectedAuto);
    chip.onclick = () => {
      session.expectedCount = count;
      updateChips();
    };
    return { chip, count };
  });
  const updateChips = () => {
    for (const { chip, count } of countChips) chip.classList.toggle("selected", session.expectedCount === count);
  };
  updateChips();

  const settingsButton = h("button", { className: "icon-button", type: "button", title: S.settingsTitle }, icon("settings"));
  settingsButton.setAttribute("aria-label", S.settingsTitle);
  settingsButton.onclick = onShowSettings;

  let torchOn = false;
  const torchButton = h("button", { className: "icon-button toggle", type: "button", title: S.torch }, icon("flash"));
  torchButton.setAttribute("aria-label", S.torch);
  torchButton.hidden = !camera.hasTorch;
  torchButton.onclick = async () => {
    torchOn = !torchOn;
    torchButton.classList.toggle("selected", torchOn);
    await camera.setTorch(torchOn);
  };

  const topBar = h(
    "div",
    { className: "bar top" },
    h("span", {}, S.expectedLabel),
    ...countChips.map((c) => c.chip),
    h("span", { className: "spacer" }),
    settingsButton,
    torchButton,
  );

  // Bottom bar: zoom, progress, restart.
  const status = h("div", { className: "status" }, S.scanHint);
  const restart = h("button", { className: "outlined", type: "button" }, S.scanRestart);
  restart.onclick = () => session.restart();
  const zoomRow = zoomControls(session, camera);
  const bottomBar = h("div", { className: "bar bottom" }, zoomRow?.element, status, restart);

  const element = h("div", { className: "screen scan" }, video, overlay, topBar, bottomBar);

  const draw = () => {
    const rect = overlay.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    if (overlay.width !== Math.round(rect.width * dpr) || overlay.height !== Math.round(rect.height * dpr)) {
      overlay.width = Math.round(rect.width * dpr);
      overlay.height = Math.round(rect.height * dpr);
    }
    const ctx = overlay.getContext("2d")!;
    ctx.clearRect(0, 0, overlay.width, overlay.height);
    if (live.imageWidth === 0 || live.imageHeight === 0) return;
    // object-fit: cover, the same transform as the app's FILL_CENTER preview.
    const scale = Math.max(overlay.width / live.imageWidth, overlay.height / live.imageHeight);
    const dx = (overlay.width - live.imageWidth * scale) / 2;
    const dy = (overlay.height - live.imageHeight * scale) / 2;
    ctx.lineWidth = 3 * dpr;
    for (const track of live.tracks) {
      const b = track.box;
      ctx.strokeStyle = track.confirmed ? CONFIRMED_COLOR : PENDING_COLOR;
      ctx.strokeRect(dx + b.left * scale, dy + b.top * scale, (b.right - b.left) * scale, (b.bottom - b.top) * scale);
    }
  };

  session.onLive = (next) => {
    live = next;
    const confirmed = next.tracks.filter((t) => t.confirmed).length;
    status.textContent = next.tracks.length === 0 ? S.scanHint : S.scanProgress(confirmed, next.tracks.length);
    draw();
  };
  const onResize = () => draw();
  window.addEventListener("resize", onResize);

  session.attach(video);

  return {
    element,
    dispose: () => {
      session.attach(null);
      session.onLive = () => {};
      window.removeEventListener("resize", onResize);
      zoomRow?.dispose();
      video.srcObject = null;
    },
  };
}

/** Preset buttons plus a slider, and pinch on the preview; null when the camera can't zoom. */
function zoomControls(session: ScanSession, camera: Camera) {
  const range = camera.zoomRange;
  if (!range) return null;

  const presets = ZOOM_PRESETS.filter((p) => p >= range.min && p <= range.max).map((preset) => {
    const chip = h("button", { className: "chip", type: "button" }, `${preset}×`);
    chip.onclick = () => setZoom(preset);
    return { chip, preset };
  });
  const slider = h("input", {
    type: "range",
    min: String(range.min),
    max: String(range.max),
    step: "0.1",
    className: "slider",
  });
  slider.setAttribute("aria-label", S.zoom);
  slider.oninput = () => setZoom(Number(slider.value));
  const label = h("span", { className: "zoom-label" });

  const show = (ratio: number) => {
    slider.value = String(ratio);
    label.textContent = `${ratio.toFixed(1)}×`;
    for (const { chip, preset } of presets) chip.classList.toggle("selected", Math.abs(ratio - preset) < 0.05);
  };
  const setZoom = (ratio: number) => {
    const clamped = Math.min(range.max, Math.max(range.min, ratio));
    session.zoomRatio = clamped;
    show(clamped);
    void camera.setZoom(clamped);
  };
  // Restore the zoom used in the last scan.
  setZoom(session.zoomRatio);

  // Pinch-to-zoom on the whole screen.
  const pointers = new Map<number, { x: number; y: number }>();
  let pinchStart = 0;
  let zoomStart = 1;
  const distance = () => {
    const [a, b] = [...pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  };
  const onDown = (e: PointerEvent) => {
    if (e.target instanceof HTMLElement && e.target.closest(".bar")) return;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size === 2) {
      pinchStart = distance();
      zoomStart = session.zoomRatio;
    }
  };
  const onMove = (e: PointerEvent) => {
    if (!pointers.has(e.pointerId)) return;
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.size === 2 && pinchStart > 0) setZoom(zoomStart * (distance() / pinchStart));
  };
  const onUp = (e: PointerEvent) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinchStart = 0;
  };
  window.addEventListener("pointerdown", onDown);
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onUp);
  window.addEventListener("pointercancel", onUp);

  return {
    element: h("div", { className: "zoom" }, ...presets.map((p) => p.chip), slider, label),
    dispose: () => {
      window.removeEventListener("pointerdown", onDown);
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointercancel", onUp);
    },
  };
}
