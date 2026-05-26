import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { computeMoveMs, roundStartTime, ROUND_TIMEOUT_MS } from "./moveTiming";

describe("move timing", () => {
  it("uses startedAt when present", () => {
    const start = Timestamp.fromMillis(1_000);
    const now = Timestamp.fromMillis(4_500);
    const round = { roundNumber: 1, startedAt: start, deadline: Timestamp.fromMillis(61_000) };
    assert.equal(computeMoveMs(round, now, start), 3_500);
  });

  it("derives start from deadline minus round timeout", () => {
    const deadline = Timestamp.fromMillis(61_000);
    const now = Timestamp.fromMillis(10_000);
    const round = { roundNumber: 1, deadline };
    assert.equal(roundStartTime(round, now).toMillis(), 1_000);
    assert.equal(computeMoveMs(round, now, now), 9_000);
  });

  it("caps move time at round timeout", () => {
    const start = Timestamp.fromMillis(0);
    const now = Timestamp.fromMillis(ROUND_TIMEOUT_MS + 5_000);
    assert.equal(computeMoveMs({ roundNumber: 1, startedAt: start }, now, start), ROUND_TIMEOUT_MS);
  });
});
