/**
 * Rebuild throwsRock / throwsPaper / throwsScissors from all match round choices.
 *
 * Run from repo root (requires Application Default Credentials):
 *   npm --prefix functions run backfill-throw-stats
 *
 * Options:
 *   --dry-run   Log counts without writing
 */
import * as admin from "firebase-admin";
import { FieldPath } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";
import type { Move } from "../src/game";

const MATCHES_COLLECTION = "matches";
const USERS_COLLECTION = "users";
const PAGE_SIZE = 200;

type ThrowCounts = { throwsRock: number; throwsPaper: number; throwsScissors: number };

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

function addThrow(counts: ThrowCounts, move: string | undefined) {
  if (move === "ROCK") counts.throwsRock += 1;
  else if (move === "PAPER") counts.throwsPaper += 1;
  else if (move === "SCISSORS") counts.throwsScissors += 1;
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

  const totals = new Map<string, ThrowCounts>();

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
      const data = doc.data();
      const player1 = data.player1 as string | undefined;
      const player2 = data.player2 as string | undefined;
      const rounds = (data.rounds as Array<{
        player1Choice?: Move;
        player2Choice?: Move;
      }>) ?? [];

      for (const round of rounds) {
        if (player1 && round.player1Choice) {
          const counts = totals.get(player1) ?? {
            throwsRock: 0,
            throwsPaper: 0,
            throwsScissors: 0,
          };
          addThrow(counts, round.player1Choice);
          totals.set(player1, counts);
        }
        if (player2 && round.player2Choice) {
          const counts = totals.get(player2) ?? {
            throwsRock: 0,
            throwsPaper: 0,
            throwsScissors: 0,
          };
          addThrow(counts, round.player2Choice);
          totals.set(player2, counts);
        }
      }
    }

    lastId = page.docs[page.docs.length - 1].id;
    if (page.size < PAGE_SIZE) break;
  }

  console.log(`Computed throw stats for ${totals.size} users from match history.`);

  if (dryRun) {
    for (const [uid, counts] of totals) {
      console.log(uid, counts);
    }
    return;
  }

  let batch = db.batch();
  let ops = 0;
  for (const [uid, counts] of totals) {
    batch.set(
      db.collection(USERS_COLLECTION).doc(uid),
      counts,
      { merge: true },
    );
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
