/**
 * Set matches.resolution on finished match documents that are missing it.
 *
 * Run: npm --prefix functions run backfill-match-resolution
 * Options: --dry-run
 */
import * as admin from "firebase-admin";
import { FieldPath } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

type MatchResolution = "player1_win" | "player2_win" | "draw" | "abandoned";

type MatchDoc = {
  player1?: string;
  player2?: string;
  status?: string;
  winnerId?: string;
  player1Wins?: number;
  player2Wins?: number;
  resolution?: MatchResolution;
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

function inferResolution(data: MatchDoc): MatchResolution | null {
  const status = data.status;
  if (status === "abandoned") return "abandoned";
  if (status !== "completed") return null;

  const player1 = data.player1;
  const player2 = data.player2;
  if (!player1 || !player2) return null;

  const winnerId = data.winnerId;
  const player1Wins = Number(data.player1Wins ?? 0);
  const player2Wins = Number(data.player2Wins ?? 0);

  if (winnerId === player1) return "player1_win";
  if (winnerId === player2) return "player2_win";
  if (winnerId != null) return null;

  if (player1Wins === player2Wins) return "draw";
  if (player1Wins > player2Wins) return "player1_win";
  return "player2_win";
}

async function main() {
  const { dryRun } = parseArgs(process.argv.slice(2));
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error("No Firebase project id.");
  }

  admin.initializeApp({ projectId });
  const db = admin.firestore();

  let scanned = 0;
  let updated = 0;
  let skipped = 0;
  let unresolved = 0;
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
      scanned += 1;
      const data = doc.data() as MatchDoc;
      if (data.resolution) {
        skipped += 1;
        continue;
      }

      const resolution = inferResolution(data);
      if (!resolution) {
        unresolved += 1;
        console.warn(`[skip] ${doc.id}: status=${data.status} winnerId=${data.winnerId ?? "null"}`);
        continue;
      }

      updated += 1;
      if (dryRun) {
        console.log(`[dry-run] ${doc.id}: would set resolution=${resolution}`);
        continue;
      }

      await doc.ref.update({ resolution });
    }

    lastId = page.docs[page.docs.length - 1].id;
    if (page.size < PAGE_SIZE) break;
  }

  console.log(
    dryRun
      ? `[dry-run] Scanned ${scanned}, would update ${updated}, already set ${skipped}, unresolved ${unresolved}.`
      : `Scanned ${scanned}, updated ${updated}, already set ${skipped}, unresolved ${unresolved}.`,
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
