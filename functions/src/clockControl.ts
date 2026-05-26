import { Timestamp } from "firebase-admin/firestore";

/** Initial thinking budget per player for the whole match. */
export const INITIAL_CLOCK_MS = 60_000;

/** Bonus time added to both players after each resolved round. */
export const CLOCK_INCREMENT_MS = 5_000;

export interface ClockFields {
  player1ClockMs?: number;
  player2ClockMs?: number;
  clocksUpdatedAt?: Timestamp;
}

export interface OpenRoundChoices {
  player1Choice?: string;
  player2Choice?: string;
}

export function clockMs(value: number | undefined): number {
  return value ?? INITIAL_CLOCK_MS;
}

export function initialClockFields(now: Timestamp): Required<Pick<ClockFields, "player1ClockMs" | "player2ClockMs" | "clocksUpdatedAt">> {
  return {
    player1ClockMs: INITIAL_CLOCK_MS,
    player2ClockMs: INITIAL_CLOCK_MS,
    clocksUpdatedAt: now,
  };
}

/**
 * Deduct elapsed time from each player who has not submitted this round.
 * Submitted players' clocks are frozen until the next round starts.
 */
export function tickClocks(
  clocks: ClockFields,
  round: OpenRoundChoices,
  now: Timestamp,
): Required<Pick<ClockFields, "player1ClockMs" | "player2ClockMs" | "clocksUpdatedAt">> {
  const lastTick = clocks.clocksUpdatedAt ?? now;
  const elapsed = Math.max(0, now.toMillis() - lastTick.toMillis());

  let player1ClockMs = clockMs(clocks.player1ClockMs);
  let player2ClockMs = clockMs(clocks.player2ClockMs);

  if (!round.player1Choice) {
    player1ClockMs = Math.max(0, player1ClockMs - elapsed);
  }
  if (!round.player2Choice) {
    player2ClockMs = Math.max(0, player2ClockMs - elapsed);
  }

  return { player1ClockMs, player2ClockMs, clocksUpdatedAt: now };
}

export function applyClockIncrement(player1ClockMs: number, player2ClockMs: number): {
  player1ClockMs: number;
  player2ClockMs: number;
} {
  return {
    player1ClockMs: player1ClockMs + CLOCK_INCREMENT_MS,
    player2ClockMs: player2ClockMs + CLOCK_INCREMENT_MS,
  };
}

export type ClockExpiry = "player1" | "player2" | "both" | null;

/** Who ran out of thinking time without locking in a move this round. */
export function clockExpiry(
  player1ClockMs: number,
  player2ClockMs: number,
  round: OpenRoundChoices,
): ClockExpiry {
  const p1Out = player1ClockMs <= 0 && !round.player1Choice;
  const p2Out = player2ClockMs <= 0 && !round.player2Choice;
  if (p1Out && p2Out) return "both";
  if (p1Out) return "player1";
  if (p2Out) return "player2";
  return null;
}
