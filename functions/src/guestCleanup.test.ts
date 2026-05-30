import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import type { UserRecord } from "firebase-admin/auth";
import {
  isAnonymousAuthUser,
  isGuestDisplayName,
  profileHasZeroMatches,
  profileLastActiveMs,
  shouldDeleteZeroMatchGuest,
} from "./guestCleanup";

function authUser(overrides: Partial<UserRecord> = {}): UserRecord {
  return {
    uid: "guest123",
    providerData: [],
    email: undefined,
    phoneNumber: undefined,
    metadata: { creationTime: "2026-01-01T00:00:00.000Z" },
    ...overrides,
  } as UserRecord;
}

describe("guestCleanup", () => {
  it("detects anonymous auth users", () => {
    assert.equal(isAnonymousAuthUser(authUser()), true);
    assert.equal(
      isAnonymousAuthUser(authUser({ providerData: [{ providerId: "google.com" } as never] })),
      false,
    );
    assert.equal(isAnonymousAuthUser(authUser({ email: "a@b.com" })), false);
  });

  it("detects guest display names", () => {
    assert.equal(isGuestDisplayName("Guest abc123"), true);
    assert.equal(isGuestDisplayName("Player"), false);
  });

  it("treats missing profile as zero matches", () => {
    assert.equal(profileHasZeroMatches(undefined), true);
  });

  it("rejects profiles with match or round stats", () => {
    assert.equal(profileHasZeroMatches({ wins: 1, losses: 0, draws: 0 }), false);
    assert.equal(profileHasZeroMatches({ wins: 0, roundsWon: 1 }), false);
    assert.equal(profileHasZeroMatches({ activeMatchId: "m1" }), false);
    assert.equal(
      profileHasZeroMatches({
        displayName: "Guest abc123",
        wins: 0,
        losses: 0,
        draws: 0,
        roundsWon: 0,
        roundsLost: 0,
        roundsDraw: 0,
      }),
      true,
    );
  });

  it("uses lastSeen then auth creation time", () => {
    const user = authUser();
    assert.equal(
      profileLastActiveMs({ lastSeen: Timestamp.fromMillis(1_700_000_000_000) }, user),
      1_700_000_000_000,
    );
    assert.equal(profileLastActiveMs(undefined, user), Date.parse("2026-01-01T00:00:00.000Z"));
  });

  it("deletes stale anonymous guests with zero matches", () => {
    const nowMs = 1_800_000_000_000;
    const decision = shouldDeleteZeroMatchGuest({
      authUser: authUser(),
      profile: {
        displayName: "Guest guest1",
        wins: 0,
        losses: 0,
        draws: 0,
        lastSeen: Timestamp.fromMillis(nowMs - 2 * 60 * 60 * 1000),
      },
      profileExists: true,
      nowMs,
      minAgeMs: 60 * 60 * 1000,
    });
    assert.equal(decision.delete, true);
    assert.equal(decision.reason, "eligible");
  });

  it("skips recent guests and non-guest profiles", () => {
    const nowMs = 1_800_000_000_000;
    assert.equal(
      shouldDeleteZeroMatchGuest({
        authUser: authUser(),
        profile: {
          displayName: "Guest guest1",
          wins: 0,
          lastSeen: Timestamp.fromMillis(nowMs - 5 * 60 * 1000),
        },
        profileExists: true,
        nowMs,
        minAgeMs: 60 * 60 * 1000,
      }).reason,
      "too_recent",
    );
    assert.equal(
      shouldDeleteZeroMatchGuest({
        authUser: authUser(),
        profile: { displayName: "Player", wins: 0 },
        profileExists: true,
        nowMs,
        minAgeMs: 0,
      }).reason,
      "not_guest_profile",
    );
  });
});
