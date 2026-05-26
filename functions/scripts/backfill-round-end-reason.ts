/**
 * Set rounds[].endReason on match documents from winner/choices and match endReason.
 *
 * Run: npm --prefix functions run backfill-round-end-reason
 * Options: --dry-run
 */
import * as admin from "firebase-admin";
import { FieldPath } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

type RoundEndReason = "normal" | "round_timeout" | "clock_timeout" | "cancelled";

type RoundDoc = {
  roundNumber: number;
  player1Choice?: string;
  player2Choice?: string;
  winner?: string;
  resolvedAt?: admin.firestore.Timestamp;
  endReason?: RoundEndReason;
};

const MATCHES_COLLECTION = "matches";
const PAGE_SIZE = 100;

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

function inferRoundEndReason(
  round: RoundDoc,
  matchEndReason: string | undefined,
  isLastResolved: boolean,
): RoundEndReason | undefined {
  const storedReason = round.endReason as string | undefined;
  if (storedReason) {
    return storedReason === "tie" ? "normal" : (storedReason as RoundEndReason);
  }
  if (!round.resolvedAt) return undefined;

  if (!round.winner) return "cancelled";

  const p1 = round.player1Choice;
  const p2 = round.player2Choice;
  if (round.winner === "tie" || (p1 && p2)) return "normal";

  const oneMissing = (p1 == null) !== (p2 == null);
  if (oneMissing && isLastResolved) {
    if (matchEndReason === "clock_timeout") return "clock_timeout";
    if (matchEndReason === "round_timeout") return "round_timeout";
    return "round_timeout";
  }

  return undefined;
}

async function main() {
  const { dryRun } = parseArgs(process.argv.slice(2));
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error("No Firebase project id.");
  }

  admin.initializeApp({ projectId });
  const db = admin.firestore();

  let matchesUpdated = 0;
  let roundsPatched = 0;
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
      const rounds = (data.rounds as RoundDoc[]) ?? [];
      if (rounds.length === 0) continue;

      const matchEndReason = data.endReason as string | undefined;
      const lastResolvedIdx = rounds.reduce<number>((best, r, i) => {
        if (!r.resolvedAt) return best;
        const bestAt = best >= 0 ? rounds[best].resolvedAt?.toMillis() ?? 0 : 0;
        const at = r.resolvedAt.toMillis();
        return at >= bestAt ? i : best;
      }, -1);

      let changed = false;
      const patched = rounds.map((round, i) => {
        const reason = inferRoundEndReason(round, matchEndReason, i === lastResolvedIdx);
        if (!reason || round.endReason === reason) return round;
        changed = true;
        roundsPatched += 1;
        return { ...round, endReason: reason };
      });

      if (!changed) continue;
      matchesUpdated += 1;

      if (dryRun) {
        console.log(`[dry-run] ${doc.id}: would patch ${patched.filter((r, i) => r.endReason !== rounds[i].endReason).length} rounds`);
        continue;
      }

      await doc.ref.update({ rounds: patched });
    }

    lastId = page.docs[page.docs.length - 1].id;
    if (page.size < PAGE_SIZE) break;
  }

  console.log(
    dryRun
      ? `[dry-run] Would update ${matchesUpdated} matches (${roundsPatched} rounds).`
      : `Updated ${matchesUpdated} matches (${roundsPatched} rounds).`,
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
