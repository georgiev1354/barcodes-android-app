// getUserMedia wrapper with the controls the scan screen needs (zoom, torch).

// Not in TypeScript's DOM lib yet; supported by Chrome on Android, partly by Safari.
interface ExtendedCapabilities extends MediaTrackCapabilities {
  zoom?: { min: number; max: number; step?: number };
  torch?: boolean;
  focusMode?: string[];
}

export type CameraError = "insecure" | "denied" | "notFound" | "other";

/** Beyond this, digital zoom only enlarges blur and stops helping the decoder. */
const MAX_USEFUL_ZOOM = 8;

export class Camera {
  private constructor(
    readonly stream: MediaStream,
    private readonly track: MediaStreamTrack,
    private readonly capabilities: ExtendedCapabilities,
  ) {}

  static async open(): Promise<Camera> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw "insecure" satisfies CameraError;
    let stream: MediaStream;
    try {
      // 1080p like the app: enough detail for small 1D codes, still fast to decode.
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      });
    } catch (error) {
      const name = (error as DOMException)?.name;
      if (name === "NotAllowedError" || name === "SecurityError") throw "denied" satisfies CameraError;
      if (name === "NotFoundError" || name === "OverconstrainedError") throw "notFound" satisfies CameraError;
      throw "other" satisfies CameraError;
    }
    const track = stream.getVideoTracks()[0];
    const capabilities = (track.getCapabilities?.() ?? {}) as ExtendedCapabilities;
    const camera = new Camera(stream, track, capabilities);
    if (capabilities.focusMode?.includes("continuous")) {
      await camera.apply({ focusMode: "continuous" });
    }
    return camera;
  }

  /** Zoom range clamped to what is useful, or null when the browser can't zoom this camera. */
  get zoomRange(): { min: number; max: number } | null {
    const zoom = this.capabilities.zoom;
    if (!zoom || zoom.max <= zoom.min) return null;
    return { min: zoom.min, max: Math.min(zoom.max, MAX_USEFUL_ZOOM) };
  }

  get zoom(): number {
    return (this.track.getSettings() as { zoom?: number }).zoom ?? 1;
  }

  async setZoom(ratio: number) {
    const range = this.zoomRange;
    if (!range) return;
    await this.apply({ zoom: Math.min(range.max, Math.max(range.min, ratio)) });
  }

  get hasTorch(): boolean {
    return this.capabilities.torch === true;
  }

  async setTorch(on: boolean) {
    if (this.hasTorch) await this.apply({ torch: on });
  }

  stop() {
    for (const t of this.stream.getTracks()) t.stop();
  }

  private async apply(constraint: Record<string, unknown>) {
    try {
      await this.track.applyConstraints({ advanced: [constraint as MediaTrackConstraintSet] });
    } catch {
      // Unsupported on this device; the control simply has no effect.
    }
  }
}
