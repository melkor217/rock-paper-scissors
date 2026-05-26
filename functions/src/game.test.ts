import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { calculateElo, parseMatchMode, resolveRound, winsToFinish } from "./game";

describe("resolveRound", () => {
  it("detects ties", () => {
    assert.equal(resolveRound("ROCK", "ROCK"), "tie");
  });

  it("rock beats scissors", () => {
    assert.equal(resolveRound("ROCK", "SCISSORS"), "player1");
    assert.equal(resolveRound("SCISSORS", "ROCK"), "player2");
  });
});

describe("calculateElo", () => {
  it("updates ratings for a win", () => {
    const result = calculateElo(1000, 1000, 1);
    assert.equal(result.deltaA, 16);
    assert.equal(result.deltaB, -16);
  });
});

describe("match modes", () => {
  it("defaults unknown values to BO3", () => {
    assert.equal(parseMatchMode(undefined), "BO3");
    assert.equal(parseMatchMode("BO5"), "BO5");
  });

  it("maps wins to finish by mode", () => {
    assert.equal(winsToFinish("BO3"), 2);
    assert.equal(winsToFinish("BO5"), 3);
  });
});
