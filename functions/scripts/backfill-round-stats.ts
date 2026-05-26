/**
 * Rebuild roundsWon / roundsLost / roundsDraw from resolved match rounds.
 *
 * Counts rounds with a winner (including "tie"). Skips cancelled stubs (resolved
 * without winner), matching live Cloud Functions behavior.
 *
 * Run from repo root (requires Application Default Credentials):
 *   npm --prefix functions run backfill-round-stats
 *
 * Options:
 *   --dry-run   Log counts without writing
 */
import * as admin from "firebase-admin";
import { FieldPath } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

const MATCHES_COLLECTION = "matches";
const USERS_COLLECTION = "users";
const PAGE_SIZE = 200;

type RoundCounts = {
  roundsWon: number;
  roundsLost: number;
  roundsDraw: number;
};

type RoundDoc = {
  winner?: string;
  resolvedAt?: admin.firestore.Timestamp;
};

function parseArgs(argv: string[]) {
  return { dryRun: argv.includes("--dry-run") };
}

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

function emptyCounts(): RoundCounts {
  return { roundsWon: 0, roundsLost: 0, roundsDraw: 0 };
}

function addRoundOutcome(
  totals: Map<string, RoundCounts>,
  player1: string,
  player2: string,
  winner: string,
): void {
  if (winner === "tie") {
    const p1 = totals.get(player1) ?? emptyCounts();
    p1.roundsDraw += 1;
    totals.set(player1, p1);
    const p2 = totals.get(player2) ?? emptyCounts();
    p2.roundsDraw += 1;
    totals.set(player2, p2);
    return;
  }
  if (winner === player1) {
    const p1 = totals.get(player1) ?? emptyCounts();
    p1.roundsWon += 1;
    totals.set(player1, p1);
    const p2 = totals.get(player2) ?? emptyCounts();
    p2.roundsLost += 1;
    totals.set(player2, p2);
    return;
  }
  if (winner === player2) {
    const p2 = totals.get(player2) ?? emptyCounts();
    p2.roundsWon += 1;
    totals.set(player2, p2);
    const p1 = totals.get(player1) ?? emptyCounts();
    p1.roundsLost += 1;
    totals.set(player1, p1);
  }
}

function isRecapRound(round: RoundDoc): boolean {
  return round.winner != null && round.winner.length > 0;
}

async function main() {
  const { dryRun } = parseArgs(process.argv.slice(2));
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error(
      "No Firebase project id. Set GOOGLE_CLOUD_PROJECT or add projects.default to .firebaserc.",
    );
  }

  admin.initializeApp({ projectId });
  const db = admin.firestore();

  const totals = new Map<string, RoundCounts>();
  let matchesScanned = 0;
  let roundsCounted = 0;
  let roundsSkipped = 0;

  let lastId: string | undefined;
  for (;;) {
    let query = db
      .collection(MATCHES_COLLECTION)
      .orderBy(FieldPath.documentId())
      .limit(PAGE_SIZE);
    if (lastId) query = query.startAfter(lastId);

    const page = await query.get();
    if (page.empty) break;

    for (const doc of page.docs) {
      matchesScanned += 1;
      const data = doc.data();
      const player1 = data.player1 as string | undefined;
      const player2 = data.player2 as string | undefined;
      if (!player1 || !player2) continue;

      const rounds = (data.rounds as RoundDoc[]) ?? [];
      for (const round of rounds) {
        if (!isRecapRound(round)) {
          roundsSkipped += 1;
          continue;
        }
        addRoundOutcome(totals, player1, player2, round.winner!);
        roundsCounted += 1;
      }
    }

    lastId = page.docs[page.docs.length - 1].id;
    if (page.size < PAGE_SIZE) break;
  }

  console.log(
    `Scanned ${matchesScanned} matches; counted ${roundsCounted} resolved rounds ` +
      `(skipped ${roundsSkipped} without winner).`,
  );
  console.log(`Computed round stats for ${totals.size} users.`);

  if (dryRun) {
    for (const [uid, counts] of totals) {
      console.log(uid, counts);
    }
    return;
  }

  let batch = db.batch();
  let ops = 0;
  for (const [uid, counts] of totals) {
    batch.set(db.collection(USERS_COLLECTION).doc(uid), counts, { merge: true });
    ops += 1;
    if (ops >= 400) {
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }
  if (ops > 0) await batch.commit();

  console.log("Done.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
