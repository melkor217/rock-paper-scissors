import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { calculateElo, parseMatchMode, parseMatchModes, pickSharedMatchMode, resolveRound, winsToFinish } from "./game";

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

  it("parses multiple queue modes with legacy fallback", () => {
    assert.deepEqual(parseMatchModes(["BO5", "BO3"]), ["BO5", "BO3"]);
    assert.deepEqual(parseMatchModes(undefined, "BO5"), ["BO5"]);
    assert.deepEqual(parseMatchModes([], "BO3"), ["BO3"]);
  });

  it("picks the shared mode, preferring BO3", () => {
    assert.equal(pickSharedMatchMode(["BO3", "BO5"], ["BO5"]), "BO5");
    assert.equal(pickSharedMatchMode(["BO3", "BO5"], ["BO3", "BO5"]), "BO3");
    assert.equal(pickSharedMatchMode(["BO3"], ["BO5"]), null);
  });
});
