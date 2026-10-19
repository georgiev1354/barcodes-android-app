// Same as BarcodeAnalyzer.enhanced() in the Android app.

/** Above 1 darkens midtones: a faded bar at 60% gray maps to about 40%. */
const GAMMA = 1.6;

/**
 * Turns RGBA pixels in place into grayscale with the 1st–99th luminance percentiles stretched to
 * full range, then darkened in the midtones (gamma), so pale bars come out close to black.
 */
export function enhance(data: Uint8ClampedArray) {
  const size = data.length / 4;
  const luma = new Uint8Array(size);
  const histogram = new Uint32Array(256);
  for (let i = 0, p = 0; i < size; i++, p += 4) {
    const v = (77 * data[p] + 150 * data[p + 1] + 29 * data[p + 2]) >> 8;
    luma[i] = v;
    histogram[v]++;
  }
  const low = percentile(histogram, Math.floor(size / 100));
  const high = Math.max(percentile(histogram, size - Math.floor(size / 100)), low + 1);

  const lut = new Uint8Array(256);
  for (let v = 0; v < 256; v++) {
    const t = Math.min(1, Math.max(0, (v - low) / (high - low)));
    lut[v] = Math.floor(255 * Math.pow(t, GAMMA));
  }
  for (let i = 0, p = 0; i < size; i++, p += 4) {
    const g = lut[luma[i]];
    data[p] = g;
    data[p + 1] = g;
    data[p + 2] = g;
    data[p + 3] = 255;
  }
}

/** Smallest luminance with at least [count] pixels at or below it. */
function percentile(histogram: Uint32Array, count: number): number {
  let sum = 0;
  for (let v = 0; v < 256; v++) {
    sum += histogram[v];
    if (sum >= count) return v;
  }
  return 255;
}
