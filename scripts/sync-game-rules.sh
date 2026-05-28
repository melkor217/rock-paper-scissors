#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$ROOT/shared/game-rules.json"
OUT="$ROOT/app/src/main/java/com/rpsonline/app/domain/GeneratedGameRules.kt"
TEST_RES="$ROOT/app/src/test/resources/game-rules.json"

node - "$JSON" "$OUT" "$TEST_RES" <<'NODE'
const fs = require("fs");
const [jsonPath, outPath, testResPath] = process.argv.slice(2);
const rules = JSON.parse(fs.readFileSync(jsonPath, "utf8"));
const modes = rules.matchModes;
const modeLines = Object.entries(modes)
  .map(([name, mode]) => {
    const tied = mode.tiedSeriesScore != null ? `, tiedSeriesScore = ${mode.tiedSeriesScore}` : "";
    return `        ${name}(winsToFinish = ${mode.winsToFinish}, bestOfRounds = ${mode.bestOfRounds}${tied}),`;
  })
  .join("\n");

const kotlin = `package com.rpsonline.app.domain

// Generated from shared/game-rules.json — run ./scripts/sync-game-rules.sh after edits.
object GeneratedGameRules {
    const val ROUND_TIMEOUT_SECONDS: Int = ${rules.roundTimeoutSeconds}
    const val INITIAL_CLOCK_MS: Long = ${rules.initialClockMs}L
    const val MAX_CLOCK_MS: Long = ${rules.maxClockMs}L
    const val CLOCK_INCREMENT_MS: Long = ${rules.clockIncrementMs}L
    const val CLOCK_RING_FULL_SECONDS: Int = 60

    enum class Mode(
        val winsToFinish: Int,
        val bestOfRounds: Int,
        val tiedSeriesScore: Int? = null,
    ) {
${modeLines}
    }
}
`;

fs.writeFileSync(outPath, kotlin);
fs.mkdirSync(require("path").dirname(testResPath), { recursive: true });
fs.copyFileSync(jsonPath, testResPath);
NODE

echo "Wrote $OUT and $TEST_RES"
