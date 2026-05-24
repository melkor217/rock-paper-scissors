#!/usr/bin/env node
/**
 * One-time backfill of rock/paper/scissors counts from completed matches.
 *
 * Usage (from repo root, with Firebase CLI logged in):
 *   node scripts/backfill-move-stats.mjs
 *
 * Requires Application Default Credentials (e.g. `firebase login` or GOOGLE_APPLICATION_CREDENTIALS).
 */
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const MOVE_FIELDS = {
  ROCK: "rockCount",
  PAPER: "paperCount",
  SCISSORS: "scissorsCount",
};

initializeApp();
const db = getFirestore();

function incrementUpdate(choice) {
  const field = MOVE_FIELDS[choice];
  if (!field) return null;
  return { [field]: FieldValue.increment(1) };
}

async function backfillMatch(doc) {
  const match = doc.data();
  if (match.moveStatsRecorded) {
    return false;
  }
  if (match.status !== "completed") {
    return false;
  }

  const batch = db.batch();
  for (const round of match.rounds ?? []) {
    if (round.player1Choice && MOVE_FIELDS[round.player1Choice]) {
      batch.update(
        db.collection("users").doc(match.player1),
        incrementUpdate(round.player1Choice),
      );
    }
    if (round.player2Choice && MOVE_FIELDS[round.player2Choice]) {
      batch.update(
        db.collection("users").doc(match.player2),
        incrementUpdate(round.player2Choice),
      );
    }
  }
  batch.update(doc.ref, { moveStatsRecorded: true });
  await batch.commit();
  return true;
}

const snapshot = await db.collection("matches").where("status", "==", "completed").get();
let updated = 0;
for (const doc of snapshot.docs) {
  if (await backfillMatch(doc)) {
    updated += 1;
    console.log(`Backfilled ${doc.id}`);
  }
}
console.log(`Done. Backfilled ${updated} of ${snapshot.size} completed matches.`);
