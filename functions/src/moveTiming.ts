import { Timestamp } from "firebase-admin/firestore";

export const ROUND_TIMEOUT_MS = 60_000;

export interface RoundTimingFields {
  roundNumber: number;
  startedAt?: Timestamp;
  deadline?: Timestamp;
  player1MoveMs?: number;
  player2MoveMs?: number;
}

export function roundStartTime(round: RoundTimingFields, fallback: Timestamp): Timestamp {
  if (round.startedAt) return round.startedAt;
  if (round.deadline) {
    return Timestamp.fromMillis(round.deadline.toMillis() - ROUND_TIMEOUT_MS);
  }
  return fallback;
}

/** Milliseconds from round start until the move was submitted (capped at round timeout). */
export function computeMoveMs(round: RoundTimingFields, now: Timestamp, fallbackStart: Timestamp): number {
  const start = roundStartTime(round, fallbackStart);
  const elapsed = now.toMillis() - start.toMillis();
  return Math.max(0, Math.min(ROUND_TIMEOUT_MS, elapsed));
}

export type MoveTimingSlot = "player1" | "player2";

export function existingMoveMs(round: RoundTimingFields, slot: MoveTimingSlot): number | undefined {
  return slot === "player1" ? round.player1MoveMs : round.player2MoveMs;
}

export function withMoveMs(
  round: RoundTimingFields,
  slot: MoveTimingSlot,
  moveMs: number,
): RoundTimingFields {
  if (slot === "player1") {
    return { ...round, player1MoveMs: moveMs };
  }
  return { ...round, player2MoveMs: moveMs };
}
