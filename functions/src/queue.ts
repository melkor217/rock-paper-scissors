import type { Timestamp } from "firebase-admin/firestore";

/** Client sends a queue heartbeat every 30s; allow up to two missed beats. */
export const QUEUE_STALE_MS = 90_000;

export function queueLastActiveMs(data: Record<string, unknown>): number | null {
  const heartbeat = data.lastHeartbeatAt as Timestamp | undefined;
  const joinedAt = data.joinedAt as Timestamp | undefined;
  const ts = heartbeat ?? joinedAt;
  return ts?.toMillis() ?? null;
}

export function isQueueEntryActive(
  data: Record<string, unknown>,
  nowMs: number = Date.now(),
): boolean {
  const lastActive = queueLastActiveMs(data);
  if (lastActive == null) return false;
  return nowMs - lastActive <= QUEUE_STALE_MS;
}

/**
 * Queue heartbeats or late join writes must not cancel a match the player is already in.
 */
export function shouldDropQueueForLiveMatch(
  uid: string,
  matchStatus: string,
  player1: string,
  player2: string,
): boolean {
  return (matchStatus === "active" || matchStatus === "lobby")
    && (player1 === uid || player2 === uid);
}
