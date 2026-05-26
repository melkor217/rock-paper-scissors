/**
 * Backfill per-round, per-match, and per-player move timing from match history.
 * Uses 5000 ms for each submitted move that has no recorded timing yet.
 *
 * Run from repo root (requires Application Default Credentials):
 *   npm --prefix functions run backfill-move-timing
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
const ASSUMED_MOVE_MS = 5_000;

type RoundRow = {
  player1Choice?: string;
  player2Choice?: string;
  player1MoveMs?: number;
  player2MoveMs?: number;
  [key: string]: unknown;
};

type MatchTimingUpdate = {
  matchId: string;
  rounds: RoundRow[];
  player1MoveTimeMs: number;
  player2MoveTimeMs: number;
  player1MoveCount: number;
  player2MoveCount: number;
};

type UserMoveTotals = { moveTimeMs: number; moveCount: number };

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

function moveMsForChoice(round: RoundRow, slot: "player1" | "player2"): number | undefined {
  const choice = slot === "player1" ? round.player1Choice : round.player2Choice;
  if (!choice) return undefined;
  const existing = slot === "player1" ? round.player1MoveMs : round.player2MoveMs;
  if (existing != null && typeof existing === "number") return existing;
  return ASSUMED_MOVE_MS;
}

function rebuildMatchRounds(rounds: RoundRow[]): {
  rounds: RoundRow[];
  player1MoveTimeMs: number;
  player2MoveTimeMs: number;
  player1MoveCount: number;
  player2MoveCount: number;
} {
  let player1MoveTimeMs = 0;
  let player2MoveTimeMs = 0;
  let player1MoveCount = 0;
  let player2MoveCount = 0;

  const updated = rounds.map((round) => {
    const next = { ...round };
    const p1 = moveMsForChoice(round, "player1");
    const p2 = moveMsForChoice(round, "player2");
    if (p1 != null) {
      next.player1MoveMs = p1;
      player1MoveTimeMs += p1;
      player1MoveCount += 1;
    }
    if (p2 != null) {
      next.player2MoveMs = p2;
      player2MoveTimeMs += p2;
      player2MoveCount += 1;
    }
    return next;
  });

  return {
    rounds: updated,
    player1MoveTimeMs,
    player2MoveTimeMs,
    player1MoveCount,
    player2MoveCount,
  };
}

function addUserTotals(
  totals: Map<string, UserMoveTotals>,
  uid: string | undefined,
  moveTimeMs: number,
  moveCount: number,
) {
  if (!uid || moveCount === 0) return;
  const prev = totals.get(uid) ?? { moveTimeMs: 0, moveCount: 0 };
  totals.set(uid, {
    moveTimeMs: prev.moveTimeMs + moveTimeMs,
    moveCount: prev.moveCount + moveCount,
  });
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

  const matchUpdates: MatchTimingUpdate[] = [];
  const userTotals = new Map<string, UserMoveTotals>();

  let lastId: string | undefined;
  let matchesScanned = 0;
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
      const rounds = (data.rounds as RoundRow[]) ?? [];
      const rebuilt = rebuildMatchRounds(rounds);

      matchUpdates.push({
        matchId: doc.id,
        ...rebuilt,
      });

      addUserTotals(userTotals, player1, rebuilt.player1MoveTimeMs, rebuilt.player1MoveCount);
      addUserTotals(userTotals, player2, rebuilt.player2MoveTimeMs, rebuilt.player2MoveCount);
    }

    lastId = page.docs[page.docs.length - 1].id;
    if (page.size < PAGE_SIZE) break;
  }

  const movesBackfilled = matchUpdates.reduce(
    (sum, m) => sum + m.player1MoveCount + m.player2MoveCount,
    0,
  );

  console.log(
    `Scanned ${matchesScanned} matches; ${movesBackfilled} timed moves across ${userTotals.size} users.`,
  );

  if (dryRun) {
    for (const m of matchUpdates) {
      console.log(`[dry-run] would update matches/${m.matchId}`, {
        rounds: m.rounds,
        player1MoveTimeMs: m.player1MoveTimeMs,
        player2MoveTimeMs: m.player2MoveTimeMs,
        player1MoveCount: m.player1MoveCount,
        player2MoveCount: m.player2MoveCount,
      });
    }
    for (const [uid, totals] of userTotals) {
      console.log(`[dry-run] would update users/${uid}`, totals);
    }
    return;
  }

  let batch = db.batch();
  let ops = 0;
  for (const update of matchUpdates) {
    batch.set(
      db.collection(MATCHES_COLLECTION).doc(update.matchId),
      {
        rounds: update.rounds,
        player1MoveTimeMs: update.player1MoveTimeMs,
        player2MoveTimeMs: update.player2MoveTimeMs,
        player1MoveCount: update.player1MoveCount,
        player2MoveCount: update.player2MoveCount,
      },
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

  batch = db.batch();
  ops = 0;
  for (const [uid, totals] of userTotals) {
    batch.set(
      db.collection(USERS_COLLECTION).doc(uid),
      totals,
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
