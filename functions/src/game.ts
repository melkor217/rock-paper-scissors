export type Move = "ROCK" | "PAPER" | "SCISSORS";

export type MatchMode = "BO3" | "BO5";

export function parseMatchMode(value: unknown): MatchMode {
  return value === "BO5" ? "BO5" : "BO3";
}

export function winsToFinish(mode: MatchMode): number {
  return mode === "BO5" ? 3 : 2;
}

export function parseMatchModes(value: unknown, legacyMode?: unknown): MatchMode[] {
  if (Array.isArray(value)) {
    const modes = value.filter((entry): entry is MatchMode => entry === "BO3" || entry === "BO5");
    if (modes.length > 0) return modes;
  }
  return [parseMatchMode(legacyMode)];
}

export function pickSharedMatchMode(modesA: MatchMode[], modesB: MatchMode[]): MatchMode | null {
  if (modesA.includes("BO3") && modesB.includes("BO3")) return "BO3";
  if (modesA.includes("BO5") && modesB.includes("BO5")) return "BO5";
  return null;
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
