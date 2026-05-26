import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import {
  applyClockIncrement,
  clockExpiry,
  CLOCK_INCREMENT_MS,
  INITIAL_CLOCK_MS,
  tickClocks,
} from "./clockControl";

describe("clockControl", () => {
  it("ticks only for players without a choice", () => {
    const t0 = Timestamp.fromMillis(0);
    const t5 = Timestamp.fromMillis(5_000);
    const result = tickClocks(
      { player1ClockMs: 60_000, player2ClockMs: 60_000, clocksUpdatedAt: t0 },
      { player1Choice: "ROCK" },
      t5,
    );
    assert.equal(result.player1ClockMs, 60_000);
    assert.equal(result.player2ClockMs, 55_000);
  });

  it("adds increment after each resolved round", () => {
    assert.deepEqual(applyClockIncrement(10_000, 20_000), {
      player1ClockMs: 10_000 + CLOCK_INCREMENT_MS,
      player2ClockMs: 20_000 + CLOCK_INCREMENT_MS,
    });
  });

  it("detects clock expiry", () => {
    assert.equal(clockExpiry(0, 5_000, {}), "player1");
    assert.equal(clockExpiry(5_000, 0, { player1Choice: "ROCK" }), "player2");
    assert.equal(clockExpiry(0, 0, {}), "both");
    assert.equal(clockExpiry(INITIAL_CLOCK_MS, INITIAL_CLOCK_MS, {}), null);
  });
});
