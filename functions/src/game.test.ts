import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { calculateElo, resolveRound } from "./game";

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
