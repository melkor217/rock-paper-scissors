import { Timestamp } from "firebase-admin/firestore";

/** Remove users with no app activity for this long. */
export const INACTIVE_USER_MS = 30 * 24 * 60 * 60 * 1000;

/** Cap deletions per scheduler run to stay within function time limits. */
export const INACTIVE_USER_BATCH_SIZE = 50;

export interface UserActivitySnapshot {
  uid: string;
  lastActive?: Timestamp;
  createdAt?: Timestamp;
  activeMatchId?: string;
}

export function getLastActivityAt(user: UserActivitySnapshot): Timestamp | undefined {
  return user.lastActive ?? user.createdAt;
}

export function isInactiveUser(
  user: UserActivitySnapshot,
  cutoff: Timestamp,
): boolean {
  if (user.activeMatchId) return false;
  const lastActivity = getLastActivityAt(user);
  if (!lastActivity) return false;
  return lastActivity.toMillis() < cutoff.toMillis();
}
