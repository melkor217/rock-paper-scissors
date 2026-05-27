import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { isQueueEntryActive, QUEUE_STALE_MS, queueLastActiveMs } from "./queue";

function ts(ms: number) {
  return { toMillis: () => ms };
}

describe("queue", () => {
  it("prefers lastHeartbeatAt over joinedAt", () => {
    const now = 1_000_000;
    assert.equal(
      queueLastActiveMs({
        joinedAt: ts(now - 120_000),
        lastHeartbeatAt: ts(now - 10_000),
      }),
      now - 10_000,
    );
  });

  it("treats entry as stale after QUEUE_STALE_MS without heartbeat", () => {
    const now = 1_000_000;
    assert.equal(
      isQueueEntryActive({ joinedAt: ts(now - QUEUE_STALE_MS - 1) }, now),
      false,
    );
    assert.equal(
      isQueueEntryActive({ lastHeartbeatAt: ts(now - QUEUE_STALE_MS) }, now),
      true,
    );
  });

  it("keeps entry active when heartbeat is fresh", () => {
    const now = 1_000_000;
    assert.equal(
      isQueueEntryActive(
        { joinedAt: ts(now - 600_000), lastHeartbeatAt: ts(now - 15_000) },
        now,
      ),
      true,
    );
  });
});
