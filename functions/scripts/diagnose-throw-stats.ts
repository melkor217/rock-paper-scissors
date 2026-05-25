/**
 * Compare user throw fields in Firestore vs counts recomputed from match rounds.
 * Run: npm --prefix functions run build && node functions/lib/scripts/diagnose-throw-stats.js
 */
import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";
type Move = "ROCK" | "PAPER" | "SCISSORS";

type ThrowCounts = { throwsRock: number; throwsPaper: number; throwsScissors: number };

function resolveProjectId(): string | undefined {
  const fromEnv = process.env.GOOGLE_CLOUD_PROJECT ?? process.env.GCLOUD_PROJECT;
  if (fromEnv) return fromEnv;
  const firebaserc = path.resolve(__dirname, "../../../.firebaserc");
  try {
    const parsed = JSON.parse(fs.readFileSync(firebaserc, "utf8")) as {
      projects?: { default?: string };
    };
    return parsed.projects?.default;
  } catch {
    return undefined;
  }
}

function addThrow(counts: ThrowCounts, move: string | undefined) {
  if (move === "ROCK") counts.throwsRock += 1;
  else if (move === "PAPER") counts.throwsPaper += 1;
  else if (move === "SCISSORS") counts.throwsScissors += 1;
}

async function main() {
  const projectId = resolveProjectId();
  if (!projectId) throw new Error("No Firebase project id");
  admin.initializeApp({ projectId });
  const db = admin.firestore();

  const totals = new Map<string, ThrowCounts>();
  const matchesSnap = await db.collection("matches").get();

  let recentCompleted = 0;
  const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;

  for (const doc of matchesSnap.docs) {
    const data = doc.data();
    const player1 = data.player1 as string | undefined;
    const player2 = data.player2 as string | undefined;
    const status = data.status as string;
    const lastActivityAt = (data.lastActivityAt as admin.firestore.Timestamp)?.toMillis() ?? 0;
    if (status === "completed" && lastActivityAt >= oneDayAgo) recentCompleted += 1;

    const rounds = (data.rounds as Array<{ player1Choice?: Move; player2Choice?: Move }>) ?? [];
    for (const round of rounds) {
      if (player1 && round.player1Choice) {
        const counts = totals.get(player1) ?? { throwsRock: 0, throwsPaper: 0, throwsScissors: 0 };
        addThrow(counts, round.player1Choice);
        totals.set(player1, counts);
      }
      if (player2 && round.player2Choice) {
        const counts = totals.get(player2) ?? { throwsRock: 0, throwsPaper: 0, throwsScissors: 0 };
        addThrow(counts, round.player2Choice);
        totals.set(player2, counts);
      }
    }
  }

  console.log(`Matches: ${matchesSnap.size}, completed in last 24h: ${recentCompleted}`);
  console.log("Users where stored throws != recomputed from matches:\n");

  let mismatches = 0;
  for (const [uid, expected] of totals) {
    const userSnap = await db.collection("users").doc(uid).get();
    if (!userSnap.exists) continue;
    const stored = {
      throwsRock: (userSnap.get("throwsRock") as number) ?? 0,
      throwsPaper: (userSnap.get("throwsPaper") as number) ?? 0,
      throwsScissors: (userSnap.get("throwsScissors") as number) ?? 0,
    };
    const match =
      stored.throwsRock === expected.throwsRock &&
      stored.throwsPaper === expected.throwsPaper &&
      stored.throwsScissors === expected.throwsScissors;
    if (!match) {
      mismatches += 1;
      const name = userSnap.get("displayName") ?? uid.slice(0, 8);
      console.log(name);
      console.log("  stored:   ", stored);
      console.log("  expected: ", expected);
      console.log("  wins:     ", userSnap.get("wins"));
      console.log();
    }
  }
  console.log(`Total mismatches: ${mismatches} / ${totals.size}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
