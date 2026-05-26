export type Move = "ROCK" | "PAPER" | "SCISSORS";

export type MatchMode = "BO3" | "BO5" | "BO10";

export type SeriesOutcome =
  | { kind: "continue" }
  | { kind: "draw" }
  | { kind: "winner"; player: "player1" | "player2" };

export function parseMatchMode(value: unknown): MatchMode {
  if (value === "BO5") return "BO5";
  if (value === "BO10") return "BO10";
  return "BO3";
}

export function winsToFinish(mode: MatchMode): number {
  switch (mode) {
    case "BO5":
      return 3;
    case "BO10":
      return 6;
    default:
      return 2;
  }
}

export function bestOfRounds(mode: MatchMode): number {
  switch (mode) {
    case "BO5":
      return 5;
    case "BO10":
      return 10;
    default:
      return 3;
  }
}

/** BO10 can end tied at 5–5 after all rounds are played. */
export function seriesOutcomeAfterRound(
  mode: MatchMode,
  player1Wins: number,
  player2Wins: number,
  completedRoundNumber: number,
): SeriesOutcome {
  const need = winsToFinish(mode);
  if (player1Wins >= need) return { kind: "winner", player: "player1" };
  if (player2Wins >= need) return { kind: "winner", player: "player2" };

  if (mode !== "BO10" || completedRoundNumber < bestOfRounds(mode)) {
    return { kind: "continue" };
  }

  if (player1Wins === 5 && player2Wins === 5) return { kind: "draw" };
  if (player1Wins > player2Wins) return { kind: "winner", player: "player1" };
  if (player2Wins > player1Wins) return { kind: "winner", player: "player2" };
  return { kind: "draw" };
}

export function parseMatchModes(value: unknown, legacyMode?: unknown): MatchMode[] {
  if (Array.isArray(value)) {
    const modes = value.filter(
      (entry): entry is MatchMode => entry === "BO3" || entry === "BO5" || entry === "BO10",
    );
    if (modes.length > 0) return modes;
  }
  return [parseMatchMode(legacyMode)];
}

const SHARED_MODE_ORDER: MatchMode[] = ["BO3", "BO5", "BO10"];

/** Picks uniformly at random among formats both players queued for. */
export function pickSharedMatchMode(
  modesA: MatchMode[],
  modesB: MatchMode[],
  random: () => number = Math.random,
): MatchMode | null {
  const shared = SHARED_MODE_ORDER.filter((mode) => modesA.includes(mode) && modesB.includes(mode));
  if (shared.length === 0) return null;
  return shared[Math.floor(random() * shared.length)];
}

export function resolveRound(p1: Move, p2: Move): "player1" | "player2" | "tie" {
  if (p1 === p2) return "tie";
  if (
    (p1 === "ROCK" && p2 === "SCISSORS") ||
    (p1 === "PAPER" && p2 === "ROCK") ||
    (p1 === "SCISSORS" && p2 === "PAPER")
  ) {
    return "player1";
  }
  return "player2";
}

export function calculateElo(
  ratingA: number,
  ratingB: number,
  scoreA: number,
  k = 32,
): { newA: number; newB: number; deltaA: number; deltaB: number } {
  const expectedA = 1 / (1 + Math.pow(10, (ratingB - ratingA) / 400));
  const expectedB = 1 - expectedA;
  const deltaA = Math.round(k * (scoreA - expectedA));
  const deltaB = Math.round(k * (1 - scoreA - expectedB));
  return {
    newA: ratingA + deltaA,
    newB: ratingB + deltaB,
    deltaA,
    deltaB,
  };
}

export function isValidMove(value: string): value is Move {
  return value === "ROCK" || value === "PAPER" || value === "SCISSORS";
}
