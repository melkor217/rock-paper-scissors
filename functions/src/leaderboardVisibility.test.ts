import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  computeLeaderboardVisible,
  isGuestProfile,
  leaderboardVisibleAfterMatch,
} from "./leaderboardVisibility";

describe("leaderboardVisibility", () => {
  it("detects guest profiles", () => {
    assert.equal(isGuestProfile({ isGuest: true }), true);
    assert.equal(isGuestProfile({ isGuest: false, displayName: "Alice" }), false);
    assert.equal(isGuestProfile({ displayName: "Guest abc123" }), true);
  });

  it("hides guests and zero-match accounts", () => {
    assert.equal(
      computeLeaderboardVisible({ isGuest: false }, 0, 0, 0),
      false,
    );
    assert.equal(
      computeLeaderboardVisible({ isGuest: false }, 1, 0, 0),
      true,
    );
    assert.equal(
      computeLeaderboardVisible({ displayName: "Guest abc123" }, 2, 1, 0),
      false,
    );
  });

  it("projects visibility after a match update", () => {
    assert.equal(
      leaderboardVisibleAfterMatch({ isGuest: false, wins: 0, losses: 0 }, 0, 1, 0),
      true,
    );
    assert.equal(
      leaderboardVisibleAfterMatch({ isGuest: true, wins: 0, losses: 0 }, 1, 0, 0),
      false,
    );
  });
});
