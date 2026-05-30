import * as admin from "firebase-admin";
import { FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";

export const GUEST_CLEANUP_MAINTENANCE_DOC = "maintenance/oneTimeGuestCleanup";

const DEFAULT_MIN_AGE_MS = 60 * 60 * 1000;

function asInt(value: unknown): number {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
}

/** Firebase anonymous accounts have no linked identity providers. */
export function isAnonymousAuthUser(user: admin.auth.UserRecord): boolean {
  return (
    user.providerData.length === 0 &&
    user.email == null &&
    user.phoneNumber == null
  );
}

export function isGuestDisplayName(displayName: unknown): boolean {
  return typeof displayName === "string" && displayName.startsWith("Guest ");
}

/** True when the user has never finished a match or round (and is not mid-match). */
export function profileHasZeroMatches(data: Record<string, unknown> | undefined): boolean {
  if (!data) return true;
  if (data.activeMatchId) return false;

  const wins = asInt(data.wins);
  const losses = asInt(data.losses);
  const draws = asInt(data.draws);
  if (wins + losses + draws > 0) return false;

  const rounds =
    asInt(data.roundsWon) + asInt(data.roundsLost) + asInt(data.roundsDraw);
  return rounds === 0;
}

export function profileLastActiveMs(
  profile: Record<string, unknown> | undefined,
  authUser: admin.auth.UserRecord,
): number {
  const lastSeen = profile?.lastSeen;
  if (lastSeen instanceof Timestamp) return lastSeen.toMillis();
  if (lastSeen && typeof lastSeen === "object" && "toMillis" in lastSeen) {
    return (lastSeen as { toMillis: () => number }).toMillis();
  }

  const createdAt = profile?.createdAt;
  if (createdAt instanceof Timestamp) return createdAt.toMillis();
  if (createdAt && typeof createdAt === "object" && "toMillis" in createdAt) {
    return (createdAt as { toMillis: () => number }).toMillis();
  }

  return Date.parse(authUser.metadata.creationTime);
}

export function shouldDeleteZeroMatchGuest(params: {
  authUser: admin.auth.UserRecord;
  profile: Record<string, unknown> | undefined;
  profileExists: boolean;
  nowMs: number;
  minAgeMs?: number;
}): { delete: boolean; reason: string } {
  if (!isAnonymousAuthUser(params.authUser)) {
    return { delete: false, reason: "not_anonymous" };
  }
  if (!profileHasZeroMatches(params.profile)) {
    return { delete: false, reason: "has_matches" };
  }
  if (params.profileExists && params.profile && !isGuestDisplayName(params.profile.displayName)) {
    return { delete: false, reason: "not_guest_profile" };
  }

  const minAgeMs = params.minAgeMs ?? DEFAULT_MIN_AGE_MS;
  if (minAgeMs > 0) {
    const lastActiveMs = profileLastActiveMs(params.profile, params.authUser);
    if (lastActiveMs > params.nowMs - minAgeMs) {
      return { delete: false, reason: "too_recent" };
    }
  }

  return { delete: true, reason: "eligible" };
}

export interface GuestCleanupSummary {
  scannedAuthUsers: number;
  anonymousAuthUsers: number;
  eligible: number;
  deleted: number;
  skipped: Record<string, number>;
  dryRun: boolean;
}

async function deleteGuestArtifacts(db: Firestore, uid: string): Promise<void> {
  const batch = db.batch();
  batch.delete(db.collection("queue").doc(uid));
  batch.delete(db.collection("presence").doc(uid));
  batch.delete(db.collection("users").doc(uid));
  await batch.commit();
  await admin.auth().deleteUser(uid);
}

export async function runZeroMatchGuestCleanup(params: {
  db: Firestore;
  dryRun: boolean;
  minAgeMs?: number;
  nowMs?: number;
  pageSize?: number;
}): Promise<GuestCleanupSummary> {
  const nowMs = params.nowMs ?? Date.now();
  const pageSize = params.pageSize ?? 1000;
  const skipped: Record<string, number> = {};
  const bump = (reason: string) => {
    skipped[reason] = (skipped[reason] ?? 0) + 1;
  };

  let scannedAuthUsers = 0;
  let anonymousAuthUsers = 0;
  let eligible = 0;
  let deleted = 0;
  let pageToken: string | undefined;

  do {
    const page = await admin.auth().listUsers(pageSize, pageToken);
    scannedAuthUsers += page.users.length;

    for (const authUser of page.users) {
      if (!isAnonymousAuthUser(authUser)) continue;
      anonymousAuthUsers += 1;

      const profileSnap = await params.db.collection("users").doc(authUser.uid).get();
      const profileExists = profileSnap.exists;
      const profile = profileExists
        ? (profileSnap.data() as Record<string, unknown>)
        : undefined;

      const decision = shouldDeleteZeroMatchGuest({
        authUser,
        profile,
        profileExists,
        nowMs,
        minAgeMs: params.minAgeMs,
      });

      if (!decision.delete) {
        bump(decision.reason);
        continue;
      }

      eligible += 1;
      if (params.dryRun) continue;

      await deleteGuestArtifacts(params.db, authUser.uid);
      deleted += 1;
    }

    pageToken = page.pageToken;
  } while (pageToken);

  return {
    scannedAuthUsers,
    anonymousAuthUsers,
    eligible,
    deleted,
    skipped,
    dryRun: params.dryRun,
  };
}

export async function markGuestCleanupComplete(
  db: Firestore,
  summary: GuestCleanupSummary,
): Promise<void> {
  await db.doc(GUEST_CLEANUP_MAINTENANCE_DOC).set({
    completedAt: FieldValue.serverTimestamp(),
    deletedAuthUsers: summary.deleted,
    eligible: summary.eligible,
    anonymousAuthUsers: summary.anonymousAuthUsers,
    scannedAuthUsers: summary.scannedAuthUsers,
    skipped: summary.skipped,
  });
}

export async function guestCleanupAlreadyCompleted(db: Firestore): Promise<boolean> {
  const snap = await db.doc(GUEST_CLEANUP_MAINTENANCE_DOC).get();
  return snap.exists && snap.get("completedAt") != null;
}
