import { isGuestDisplayName } from "./guestCleanup";
import { FieldPath, FieldValue, Firestore, QueryDocumentSnapshot } from "firebase-admin/firestore";

export const LEADERBOARD_BACKFILL_MAINTENANCE_DOC = "maintenance/leaderboardVisibilityBackfill";

function asInt(value: unknown): number {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
}

export function isGuestProfile(profile: Record<string, unknown> | undefined): boolean {
  if (profile?.isGuest === true) return true;
  if (profile?.isGuest === false) return false;
  return isGuestDisplayName(profile?.displayName);
}

/** Non-guest accounts with at least one finished match appear on the leaderboard. */
export function computeLeaderboardVisible(
  profile: Record<string, unknown> | undefined,
  wins: number,
  losses: number,
  draws: number,
): boolean {
  if (isGuestProfile(profile)) return false;
  return wins + losses + draws > 0;
}

export function leaderboardVisibleAfterMatch(
  profile: Record<string, unknown> | undefined,
  winsDelta: number,
  lossesDelta: number,
  drawsDelta: number,
): boolean {
  const wins = asInt(profile?.wins) + winsDelta;
  const losses = asInt(profile?.losses) + lossesDelta;
  const draws = asInt(profile?.draws) + drawsDelta;
  return computeLeaderboardVisible(profile, wins, losses, draws);
}

export interface LeaderboardVisibilityBackfillSummary {
  scanned: number;
  updated: number;
  visible: number;
  dryRun: boolean;
}

export function profileLeaderboardFields(
  profile: Record<string, unknown> | undefined,
): { isGuest: boolean; leaderboardVisible: boolean } {
  const wins = asInt(profile?.wins);
  const losses = asInt(profile?.losses);
  const draws = asInt(profile?.draws);
  return {
    isGuest: isGuestProfile(profile),
    leaderboardVisible: computeLeaderboardVisible(profile, wins, losses, draws),
  };
}

export async function runLeaderboardVisibilityBackfill(
  db: Firestore,
  dryRun: boolean,
): Promise<LeaderboardVisibilityBackfillSummary> {
  let scanned = 0;
  let updated = 0;
  let visible = 0;
  let batch = db.batch();
  let batchOps = 0;

  const commitBatch = async () => {
    if (dryRun || batchOps === 0) return;
    await batch.commit();
    batch = db.batch();
    batchOps = 0;
  };

  let lastDoc: QueryDocumentSnapshot | undefined;
  while (true) {
    let query = db.collection("users")
      .orderBy(FieldPath.documentId())
      .limit(500);
    if (lastDoc) query = query.startAfter(lastDoc);

    const snap = await query.get();
    if (snap.empty) break;

    for (const doc of snap.docs) {
      scanned += 1;
      const data = doc.data() as Record<string, unknown>;
      const target = profileLeaderboardFields(data);
      if (target.leaderboardVisible) visible += 1;

      const patch: Record<string, boolean> = {};
      if (data.isGuest !== target.isGuest) patch.isGuest = target.isGuest;
      if (data.leaderboardVisible !== target.leaderboardVisible) {
        patch.leaderboardVisible = target.leaderboardVisible;
      }
      if (Object.keys(patch).length === 0) continue;

      updated += 1;
      if (!dryRun) {
        batch.update(doc.ref, patch);
        batchOps += 1;
        if (batchOps >= 400) await commitBatch();
      }
    }

    lastDoc = snap.docs[snap.docs.length - 1];
    if (snap.size < 500) break;
  }

  await commitBatch();
  return { scanned, updated, visible, dryRun };
}

export async function leaderboardBackfillAlreadyCompleted(db: Firestore): Promise<boolean> {
  const snap = await db.doc(LEADERBOARD_BACKFILL_MAINTENANCE_DOC).get();
  return snap.exists && snap.get("completedAt") != null;
}

export async function markLeaderboardBackfillComplete(
  db: Firestore,
  summary: LeaderboardVisibilityBackfillSummary,
): Promise<void> {
  await db.doc(LEADERBOARD_BACKFILL_MAINTENANCE_DOC).set({
    completedAt: FieldValue.serverTimestamp(),
    scanned: summary.scanned,
    updated: summary.updated,
    visible: summary.visible,
  });
}
