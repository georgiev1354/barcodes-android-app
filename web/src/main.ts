// Counterpart of MainActivity.kt: camera permission and switching between screens.

import "./styles.css";
import { ScanSession } from "./scan/session";
import { S } from "./strings";
import { Camera, type CameraError } from "./ui/camera";
import { h } from "./ui/dom";
import { resultScreen } from "./ui/resultScreen";
import { scanScreen, type ScanScreen } from "./ui/scanScreen";
import { settingsScreen } from "./ui/settingsScreen";

const root = document.getElementById("app")!;
const session = new ScanSession();

let camera: Camera | null = null;
let scan: ScanScreen | null = null;
/** Bumped whenever the screen changes, so a camera that opens late doesn't show up on another one. */
let screenId = 0;

type Screen = "scan" | "result" | "settings";
let current: Screen = "scan";

function stopScan() {
  scan?.dispose();
  scan = null;
  camera?.stop();
  camera = null;
}

function show(element: HTMLElement) {
  root.replaceChildren(element);
  window.scrollTo(0, 0);
}

async function showScan() {
  stopScan();
  current = "scan";
  const id = ++screenId;
  try {
    const opened = await Camera.open();
    if (id !== screenId) {
      opened.stop();
      return;
    }
    camera = opened;
    scan = scanScreen(session, opened, showSettings);
    show(scan.element);
  } catch (error) {
    if (id === screenId) showCameraProblem(error as CameraError);
  }
}

function showPermission() {
  const button = h("button", { className: "filled", type: "button" }, S.permissionGrant);
  button.onclick = () => void showScan();
  show(h("div", { className: "screen center" }, h("h1", {}, S.appName), h("p", {}, S.permissionRationale), button));
}

function showCameraProblem(error: CameraError) {
  const text = { insecure: S.insecureContext, denied: S.permissionDenied, notFound: S.noCamera, other: S.cameraError }[error] ?? S.cameraError;
  const retry = h("button", { className: "filled", type: "button" }, S.tryAgain);
  retry.onclick = () => void showScan();
  show(h("div", { className: "screen center" }, h("h1", {}, S.appName), h("p", {}, text), error !== "insecure" && retry));
}

function showSettings() {
  stopScan();
  screenId++;
  current = "settings";
  history.pushState({ screen: "settings" }, "");
  show(settingsScreen(session, session.decoder, () => history.back()));
}

session.onResult = (result) => {
  stopScan();
  screenId++;
  current = "result";
  history.pushState({ screen: "result" }, "");
  show(resultScreen(result, session.readableWifi, () => history.back()));
};

// The phone's back button leaves the result and settings screens, as in the app.
window.addEventListener("popstate", () => {
  if (current === "scan") return;
  // Changing formats or labels in settings restarts the session by itself.
  if (current === "result") session.restart();
  void showScan();
});

// Release the camera in the background; take it again when the page comes back.
document.addEventListener("visibilitychange", () => {
  if (current !== "scan") return;
  if (document.hidden) {
    stopScan();
    screenId++;
  } else {
    void showScan();
  }
});

async function start() {
  // Skip the explanation screen when the camera is already allowed.
  let granted = false;
  try {
    granted = (await navigator.permissions.query({ name: "camera" as PermissionName })).state === "granted";
  } catch {
    // Firefox and older Safari don't know the "camera" permission name.
  }
  if (granted) void showScan();
  else showPermission();
}

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  window.addEventListener("load", () => void navigator.serviceWorker.register("./sw.js"));
}

void start();
