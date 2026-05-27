package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesSyncTest {

    @Test
    fun appConstants_matchSharedJson() {
        val json = readTestResource("/game-rules.json")
        assertEquals(parseInt(json, "roundTimeoutSeconds"), GameRules.ROUND_TIMEOUT_SECONDS)
        assertEquals(parseLong(json, "initialClockMs"), GameRules.INITIAL_CLOCK_MS)
        assertEquals(parseLong(json, "clockIncrementMs"), GameRules.CLOCK_INCREMENT_MS)

        MatchMode.entries.forEach { mode ->
            val block = modeBlock(json, mode.name)
            assertEquals(parseInt(block, "winsToFinish"), mode.winsToFinish)
            assertEquals(parseInt(block, "bestOfRounds"), mode.bestOfRounds)
            val tied = parseOptionalInt(block, "tiedSeriesScore")
            assertEquals(tied, mode.tiedSeriesScore)
        }
    }

    @Test
    fun generatedModeValues_matchMatchModeEnum() {
        GeneratedGameRules.Mode.entries.forEach { generated ->
            val mode = MatchMode.valueOf(generated.name)
            assertEquals(generated.winsToFinish, mode.winsToFinish)
            assertEquals(generated.bestOfRounds, mode.bestOfRounds)
            assertEquals(generated.tiedSeriesScore, mode.tiedSeriesScore)
        }
    }

    private fun readTestResource(path: String): String =
        checkNotNull(GameRulesSyncTest::class.java.getResourceAsStream(path)) {
            "Missing test resource $path — run ./scripts/sync-game-rules.sh"
        }.bufferedReader().readText()

    private fun parseInt(json: String, key: String): Int =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt()
            ?: error("Missing $key in game-rules.json")

    private fun parseLong(json: String, key: String): Long = parseInt(json, key).toLong()

    private fun parseOptionalInt(json: String, key: String): Int? =
        if (!json.contains("\"$key\"")) null else parseInt(json, key)

    private fun modeBlock(json: String, mode: String): String {
        val start = json.indexOf("\"$mode\"")
        assertTrue("Missing mode $mode in game-rules.json", start >= 0)
        val brace = json.indexOf('{', start)
        var depth = 0
        for (i in brace until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(brace, i + 1)
                }
            }
        }
        error("Unclosed mode block for $mode")
    }
}
