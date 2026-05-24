export type Move = "ROCK" | "PAPER" | "SCISSORS";

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

export function moveCountField(choice: Move): "rockCount" | "paperCount" | "scissorsCount" {
  switch (choice) {
    case "ROCK":
      return "rockCount";
    case "PAPER":
      return "paperCount";
    case "SCISSORS":
      return "scissorsCount";
  }
}
