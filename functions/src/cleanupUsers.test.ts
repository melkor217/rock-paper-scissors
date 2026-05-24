import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { Timestamp } from "firebase-admin/firestore";
import { getLastActivityAt, isInactiveUser } from "./cleanupUsers";

describe("getLastActivityAt", () => {
  it("prefers lastActive over createdAt", () => {
    const lastActive = Timestamp.fromMillis(2_000);
    const createdAt = Timestamp.fromMillis(1_000);
    assert.equal(
      getLastActivityAt({ uid: "u1", lastActive, createdAt })?.toMillis(),
      2_000,
    );
  });

  it("falls back to createdAt when lastActive is missing", () => {
    const createdAt = Timestamp.fromMillis(1_000);
    assert.equal(
      getLastActivityAt({ uid: "u1", createdAt })?.toMillis(),
      1_000,
    );
  });
});

describe("isInactiveUser", () => {
  const cutoff = Timestamp.fromMillis(10_000);

  it("returns true when last activity is before cutoff", () => {
    assert.equal(
      isInactiveUser(
        { uid: "u1", lastActive: Timestamp.fromMillis(9_000) },
        cutoff,
      ),
      true,
    );
  });

  it("returns false when last activity is after cutoff", () => {
    assert.equal(
      isInactiveUser(
        { uid: "u1", lastActive: Timestamp.fromMillis(11_000) },
        cutoff,
      ),
      false,
    );
  });

  it("returns false when user has an active match", () => {
    assert.equal(
      isInactiveUser(
        {
          uid: "u1",
          lastActive: Timestamp.fromMillis(1_000),
          activeMatchId: "match-1",
        },
        cutoff,
      ),
      false,
    );
  });

  it("uses createdAt for legacy profiles without lastActive", () => {
    assert.equal(
      isInactiveUser(
        { uid: "u1", createdAt: Timestamp.fromMillis(5_000) },
        cutoff,
      ),
      true,
    );
  });
});
