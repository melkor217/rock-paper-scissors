import * as fs from "fs";
import * as path from "path";

export type MatchModeName = "BO3" | "BO5" | "BO10";

export interface MatchModeRules {
  winsToFinish: number;
  bestOfRounds: number;
  tiedSeriesScore?: number;
}

export interface GameRulesFile {
  roundTimeoutSeconds: number;
  roundTimeoutMs: number;
  initialClockMs: number;
  maxClockMs: number;
  clockIncrementMs: number;
  matchModes: Record<MatchModeName, MatchModeRules>;
}

function loadGameRules(): GameRulesFile {
  const candidates = [
    path.join(__dirname, "game-rules.json"),
    path.join(__dirname, "../../shared/game-rules.json"),
  ];
  for (const rulesPath of candidates) {
    if (fs.existsSync(rulesPath)) {
      return JSON.parse(fs.readFileSync(rulesPath, "utf8")) as GameRulesFile;
    }
  }
  throw new Error("game-rules.json not found (run npm run build in functions/)");
}

export const GAME_RULES: GameRulesFile = loadGameRules();

export const ROUND_TIMEOUT_MS = GAME_RULES.roundTimeoutMs;
export const INITIAL_CLOCK_MS = GAME_RULES.initialClockMs;
export const MAX_CLOCK_MS = GAME_RULES.maxClockMs;
export const CLOCK_INCREMENT_MS = GAME_RULES.clockIncrementMs;
