/**
 * One-off migration for users/{uid}:
 * - lastSeen (missing, or all users with --all)
 * - throwsRock / throwsPaper / throwsScissors (init to 0 when missing)
 *
 * Run from repo root (requires Application Default Credentials for the project):
 *   npm --prefix functions run backfill-last-seen
 *
 * Options:
 *   --dry-run   Log what would change without writing
 *   --all       Overwrite lastSeen on every user (throw counts still only if missing)
 */
import * as admin from "firebase-admin";
import { FieldPath, Timestamp } from "firebase-admin/firestore";
import * as fs from "fs";
import * as path from "path";

const USERS_COLLECTION = "users";
const PAGE_SIZE = 500;
const BATCH_LIMIT = 500;

function parseArgs(argv: string[]) {
  return {
    dryRun: argv.includes("--dry-run"),
    all: argv.includes("--all"),
  };
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

async function main() {
  const { dryRun, all } = parseArgs(process.argv.slice(2));
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error(
      "No Firebase project id. Set GOOGLE_CLOUD_PROJECT or add projects.default to .firebaserc.",
    );
  }
  admin.initializeApp({ projectId });
  const db = admin.firestore();
  const now = Timestamp.now();

  let scanned = 0;
  let updated = 0;
  let skipped = 0;
  let lastDoc: FirebaseFirestore.QueryDocumentSnapshot | undefined;

  console.log(
    `Backfill users (dryRun=${dryRun}, all=${all}, timestamp=${now.toDate().toISOString()})`,
  );

  while (true) {
    let query = db
      .collection(USERS_COLLECTION)
      .orderBy(FieldPath.documentId())
      .limit(PAGE_SIZE);
    if (lastDoc) {
      query = query.startAfter(lastDoc);
    }

    const page = await query.get();
    if (page.empty) break;

    let batch = db.batch();
    let batchOps = 0;

    for (const doc of page.docs) {
      scanned += 1;
      const patch: Record<string, unknown> = {};
      if (all || doc.get("lastSeen") == null) {
        patch.lastSeen = now;
      }
      if (doc.get("throwsRock") == null) patch.throwsRock = 0;
      if (doc.get("throwsPaper") == null) patch.throwsPaper = 0;
      if (doc.get("throwsScissors") == null) patch.throwsScissors = 0;
      if (doc.get("roundsWon") == null) patch.roundsWon = 0;
      if (doc.get("roundsLost") == null) patch.roundsLost = 0;
      if (doc.get("roundsDraw") == null) patch.roundsDraw = 0;

      if (Object.keys(patch).length === 0) {
        skipped += 1;
        continue;
      }

      if (dryRun) {
        updated += 1;
        console.log(`[dry-run] would update ${doc.id}`, patch);
        continue;
      }

      batch.update(doc.ref, patch);
      batchOps += 1;
      updated += 1;

      if (batchOps >= BATCH_LIMIT) {
        await batch.commit();
        batch = db.batch();
        batchOps = 0;
      }
    }

    if (!dryRun && batchOps > 0) {
      await batch.commit();
    }

    lastDoc = page.docs[page.docs.length - 1];
    if (page.size < PAGE_SIZE) break;
  }

  console.log(`Done. scanned=${scanned} updated=${updated} skipped=${skipped}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
