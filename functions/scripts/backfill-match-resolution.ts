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

// Shared with functions/src/game.ts (compiled to lib/game.js).
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { inferMatchResolution } = require("../game") as {
  inferMatchResolution: (data: Record<string, unknown>) => string | null;
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
      const data = doc.data();
      if (data.resolution) {
        skipped += 1;
        continue;
      }

      const resolution = inferMatchResolution(data);
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
