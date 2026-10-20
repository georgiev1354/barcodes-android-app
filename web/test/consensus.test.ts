// Same cases as app/src/test/java/com/gg/barcodes/scan/ConsensusTrackerTest.kt.

import { describe, expect, it } from "vitest";
import { ConsensusTracker, sortedInReadingOrder, type Box, type Detection } from "../src/scan/consensus";

// Two stacked 1D barcodes, close together like on a shipping sticker.
const top: Box = { left: 100, top: 100, right: 500, bottom: 160 };
const bottom: Box = { left: 100, top: 180, right: 500, bottom: 240 };

const det = (value: string, box: Box): Detection => ({ value, format: "Code 128", box });
const code39 = (value: string, box: Box): Detection => ({ value, format: "Code 39", box, weakCheck: true });

function tracker(options: ConstructorParameters<typeof ConsensusTracker>[0], expectedCount: number | null = null) {
  const t = new ConsensusTracker(options);
  t.expectedCount = expectedCount;
  return t;
}

describe("ConsensusTracker", () => {
  it("confirms after required votes and completes with expected count", () => {
    const t = tracker({ requiredVotes: 5 }, 2);
    for (let i = 0; i < 4; i++) expect(t.onFrame([det("A", top), det("B", bottom)]).complete).toBe(false);
    const result = t.onFrame([det("A", top), det("B", bottom)]);
    expect(result.complete).toBe(true);
    expect(result.tracks.map((x) => x.value)).toEqual(["A", "B"]);
  });

  it("misread at same position delays confirmation until outvoted", () => {
    const t = tracker({ requiredVotes: 5 }, 1);
    t.onFrame([det("A1", top)]);
    for (let i = 0; i < 5; i++) t.onFrame([det("A", top)]);
    // 5 votes against 1: a lead of 4.
    expect(t.onFrame([]).complete).toBe(false);
    t.onFrame([det("A", top)]);
    const result = t.onFrame([]);
    expect(result.complete).toBe(true);
    expect(result.tracks).toHaveLength(1);
    expect(result.tracks[0].value).toBe("A");
  });

  it("scattered misreads of a faded barcode do not stall it", () => {
    const t = tracker({ requiredVotes: 5 }, 1);
    for (const v of ["A1", "A2", "A3"]) t.onFrame([det(v, top)]);
    for (let i = 0; i < 5; i++) expect(t.onFrame([det("A", top)]).complete).toBe(false);
    expect(t.onFrame([det("A", top)]).complete).toBe(true);
  });

  it("competing values never confirm", () => {
    const t = tracker({ requiredVotes: 5 }, 1);
    for (let i = 0; i < 20; i++) {
      expect(t.onFrame([det("A", top)]).complete).toBe(false);
      expect(t.onFrame([det("B", top)]).complete).toBe(false);
    }
  });

  it("neighbouring barcodes stay in separate tracks", () => {
    const t = tracker({ requiredVotes: 3 });
    for (let i = 0; i < 4; i++) t.onFrame([det("A", top), det("B", bottom)]);
    const result = t.onFrame([det("A", top), det("B", bottom)]);
    expect(result.tracks).toHaveLength(2);
    expect(result.tracks.every((x) => x.confirmed && x.leaderVotes === x.totalVotes)).toBe(true);
  });

  it("waits for expected count", () => {
    const t = tracker({ requiredVotes: 3 }, 3);
    for (let i = 0; i < 10; i++) expect(t.onFrame([det("A", top), det("B", bottom)]).complete).toBe(false);
  });

  it("auto mode completes once the scene settles", () => {
    const t = tracker({ requiredVotes: 3, settleFrames: 5 });
    for (let i = 0; i < 5; i++) expect(t.onFrame([det("A", top)]).complete).toBe(false);
    expect(t.onFrame([det("A", top)]).complete).toBe(true);
  });

  it("follows a barcode when the phone moves", () => {
    const t = tracker({ requiredVotes: 4 });
    t.onFrame([det("A", top)]);
    t.onFrame([det("A", { ...top, left: 300, right: 700 })]);
    t.onFrame([det("A", { ...top, top: 400, bottom: 460 })]);
    const result = t.onFrame([det("A", top)]);
    expect(result.tracks).toHaveLength(1);
    expect(result.tracks[0].confirmed).toBe(true);
  });

  it("format without check digit needs a larger lead", () => {
    const t = tracker({ requiredVotes: 3, weakRequiredVotes: 5 }, 1);
    for (let i = 0; i < 4; i++) expect(t.onFrame([code39("88F7C7C4ADD8", top)]).complete).toBe(false);
    expect(t.onFrame([code39("88F7C7C4ADD8", top)]).complete).toBe(true);
  });

  it("short values of a format without check digit are ignored", () => {
    const t = tracker({ requiredVotes: 3, weakRequiredVotes: 3, minWeakLength: 3 });
    for (let i = 0; i < 5; i++) t.onFrame([code39("A1", top), det("A1", bottom)]);
    const result = t.onFrame([code39("A1", top)]);
    expect(result.tracks.map((x) => x.box)).toEqual([bottom]);
  });

  it("slanted read across stacked barcodes is ignored", () => {
    const t = tracker({ requiredVotes: 3, weakRequiredVotes: 3 });
    for (let i = 0; i < 3; i++) t.onFrame([code39("88F7C7C4ADD8", top), code39("88F7C7C4ADD9", bottom)]);
    const across: Box = { left: 110, top: 90, right: 510, bottom: 250 };
    for (let i = 0; i < 3; i++) t.onFrame([code39("88F7C74ADD87", across)]);
    const result = t.onFrame([code39("88F7C74ADD87", across), code39("88F7C7C4ADD8", top)]);
    expect(result.tracks.map((x) => x.value)).toEqual(["88F7C7C4ADD8", "88F7C7C4ADD9"]);
    expect(result.tracks.every((x) => x.leaderVotes === x.totalVotes)).toBe(true);
  });

  it("reading order is rows then columns", () => {
    const boxes: Box[] = [
      { left: 300, top: 105, right: 400, bottom: 155 },
      { left: 0, top: 300, right: 100, bottom: 350 },
      { left: 0, top: 100, right: 100, bottom: 150 },
    ];
    expect(sortedInReadingOrder(boxes, (b) => b)).toEqual([boxes[2], boxes[0], boxes[1]]);
  });
});
