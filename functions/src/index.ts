import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { calculateElo, isValidMove, Move, resolveRound } from "./game";

admin.initializeApp();
const db = admin.firestore();

const REGION = "us-central1";
const ROUND_TIMEOUT_MS = 10_000;
const ELO_WINDOW = 200;
const WINS_TO_FINISH = 2;

interface RoundDoc {
  roundNumber: number;
  player1Choice?: Move;
  player2Choice?: Move;
  winner?: string;
  resolvedAt?: Timestamp;
  deadline?: Timestamp;
}

interface MatchDoc {
  player1: string;
  player2: string;
  player1Name: string;
  player2Name: string;
  status: "active" | "completed" | "abandoned";
  currentRound: number;
  player1Wins: number;
  player2Wins: number;
  rounds: RoundDoc[];
  winnerId?: string;
  player1EloDelta?: number;
  player2EloDelta?: number;
  createdAt: Timestamp;
  lastActivityAt: Timestamp;
}

async function getUserProfile(uid: string) {
  const snap = await db.collection("users").doc(uid).get();
  if (!snap.exists) {
    throw new Error(`User profile missing: ${uid}`);
  }
  return {
    uid,
    displayName: (snap.get("displayName") as string) ?? "Player",
    elo: (snap.get("elo") as number) ?? 1000,
    activeMatchId: snap.get("activeMatchId") as string | undefined,
  };
}

async function createMatch(playerA: string, playerB: string): Promise<string> {
  const [userA, userB] = await Promise.all([
    getUserProfile(playerA),
    getUserProfile(playerB),
  ]);

  const now = Timestamp.now();
  const deadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
  const matchRef = db.collection("matches").doc();

  const match: MatchDoc = {
    player1: playerA,
    player2: playerB,
    player1Name: userA.displayName,
    player2Name: userB.displayName,
    status: "active",
    currentRound: 1,
    player1Wins: 0,
    player2Wins: 0,
    rounds: [{ roundNumber: 1, deadline }],
    createdAt: now,
    lastActivityAt: now,
  };

  const batch = db.batch();
  batch.set(matchRef, match);
  batch.delete(db.collection("queue").doc(playerA));
  batch.delete(db.collection("queue").doc(playerB));
  batch.update(db.collection("users").doc(playerA), { activeMatchId: matchRef.id });
  batch.update(db.collection("users").doc(playerB), { activeMatchId: matchRef.id });
  await batch.commit();
  return matchRef.id;
}

async function tryMatch(uid: string, elo: number): Promise<string | null> {
  const queueSnap = await db.collection("queue").orderBy("joinedAt", "asc").get();
  for (const doc of queueSnap.docs) {
    const otherId = doc.id;
    if (otherId === uid) continue;
    const otherElo = (doc.get("elo") as number) ?? 1000;
    if (Math.abs(otherElo - elo) <= ELO_WINDOW) {
      return createMatch(uid, otherId);
    }
  }
  return null;
}

function getOpenRound(match: MatchDoc): RoundDoc | undefined {
  return [...match.rounds].reverse().find((round) => !round.resolvedAt);
}

async function finalizeMatch(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  winnerId: string,
): Promise<void> {
  const [p1Snap, p2Snap] = await Promise.all([
    db.collection("users").doc(match.player1).get(),
    db.collection("users").doc(match.player2).get(),
  ]);

  const p1Elo = (p1Snap.get("elo") as number) ?? 1000;
  const p2Elo = (p2Snap.get("elo") as number) ?? 1000;
  const p1Score = winnerId === match.player1 ? 1 : 0;
  const elo = calculateElo(p1Elo, p2Elo, p1Score);

  const batch = db.batch();
  batch.update(matchRef, {
    status: "completed",
    winnerId,
    player1Wins: match.player1Wins,
    player2Wins: match.player2Wins,
    rounds: match.rounds,
    player1EloDelta: elo.deltaA,
    player2EloDelta: elo.deltaB,
    lastActivityAt: FieldValue.serverTimestamp(),
  });

  batch.update(db.collection("users").doc(match.player1), {
    elo: elo.newA,
    wins: FieldValue.increment(winnerId === match.player1 ? 1 : 0),
    losses: FieldValue.increment(winnerId === match.player1 ? 0 : 1),
    activeMatchId: FieldValue.delete(),
  });
  batch.update(db.collection("users").doc(match.player2), {
    elo: elo.newB,
    wins: FieldValue.increment(winnerId === match.player2 ? 1 : 0),
    losses: FieldValue.increment(winnerId === match.player2 ? 0 : 1),
    activeMatchId: FieldValue.delete(),
  });
  await batch.commit();
}

async function resolveRoundIfReady(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
): Promise<void> {
  const round = getOpenRound(match);
  if (!round) return;

  const now = Timestamp.now();
  const p1Choice = round.player1Choice;
  const p2Choice = round.player2Choice;
  const deadlinePassed = round.deadline ? round.deadline.toMillis() <= now.toMillis() : false;

  if (!p1Choice && !p2Choice && !deadlinePassed) return;

  let winner: string;
  if (p1Choice && p2Choice) {
    const result = resolveRound(p1Choice, p2Choice);
    if (result === "tie") {
      winner = "tie";
    } else if (result === "player1") {
      winner = match.player1;
    } else {
      winner = match.player2;
    }
  } else if (p1Choice && !p2Choice) {
    winner = deadlinePassed ? match.player1 : "";
  } else if (!p1Choice && p2Choice) {
    winner = deadlinePassed ? match.player2 : "";
  } else {
    winner = deadlinePassed ? "tie" : "";
  }

  if (!winner) return;

  const rounds = [...match.rounds];
  const roundIndex = rounds.findIndex((r) => r.roundNumber === round.roundNumber && !r.resolvedAt);
  if (roundIndex < 0) return;

  let player1Wins = match.player1Wins;
  let player2Wins = match.player2Wins;
  if (winner === match.player1) player1Wins += 1;
  if (winner === match.player2) player2Wins += 1;

  if (player1Wins >= WINS_TO_FINISH) {
    rounds[roundIndex] = { ...round, winner, resolvedAt: now, player1Choice: p1Choice, player2Choice: p2Choice };
    await finalizeMatch(matchRef, { ...match, rounds, player1Wins, player2Wins }, match.player1);
    return;
  }
  if (player2Wins >= WINS_TO_FINISH) {
    rounds[roundIndex] = { ...round, winner, resolvedAt: now, player1Choice: p1Choice, player2Choice: p2Choice };
    await finalizeMatch(matchRef, { ...match, rounds, player1Wins, player2Wins }, match.player2);
    return;
  }

  if (winner === "tie") {
    const nextDeadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
    rounds[roundIndex] = {
      ...round,
      player1Choice: p1Choice,
      player2Choice: p2Choice,
      winner: "tie",
      resolvedAt: now,
    };
    rounds.push({ roundNumber: match.currentRound, deadline: nextDeadline });
    await matchRef.update({
      rounds,
      player1Wins,
      player2Wins,
      lastActivityAt: FieldValue.serverTimestamp(),
    });
    return;
  }

  rounds[roundIndex] = {
    ...round,
    player1Choice: p1Choice,
    player2Choice: p2Choice,
    winner,
    resolvedAt: now,
  };

  const nextRoundNumber = match.currentRound + 1;
  const nextDeadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
  await matchRef.update({
    rounds: [...rounds, { roundNumber: nextRoundNumber, deadline: nextDeadline }],
    currentRound: nextRoundNumber,
    player1Wins,
    player2Wins,
    lastActivityAt: FieldValue.serverTimestamp(),
  });
}

/** Client writes queue/{uid}; this trigger runs matchmaking (no Callable / Cloud Run IAM). */
export const onQueueEntry = onDocumentCreated(
  { document: "queue/{userId}", region: REGION },
  async (event) => {
    const uid = event.params.userId;
    const data = event.data?.data();
    if (!data) return;

    const profile = await getUserProfile(uid);
    if (profile.activeMatchId) {
      const active = await db.collection("matches").doc(profile.activeMatchId).get();
      if (active.exists && active.get("status") === "active") {
        await db.collection("queue").doc(uid).delete();
        return;
      }
    }

    const elo = (data.elo as number) ?? profile.elo;
    await tryMatch(uid, elo);
  },
);

async function applyPlayerChoice(
  matchId: string,
  uid: string,
  choice: string,
  roundNumber: number,
) {
  if (!isValidMove(choice)) return;

  const matchRef = db.collection("matches").doc(matchId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(matchRef);
    if (!snap.exists) return;

    const match = snap.data() as MatchDoc;
    if (match.status !== "active") return;
    if (uid !== match.player1 && uid !== match.player2) return;

    const round = getOpenRound(match);
    if (!round || round.roundNumber !== roundNumber) return;

    const rounds = [...match.rounds];
    const idx = rounds.findIndex((r) => r.roundNumber === round.roundNumber && !r.resolvedAt);
    if (idx < 0) return;

    const current = { ...rounds[idx] };
    if (uid === match.player1) {
      if (current.player1Choice) return;
      current.player1Choice = choice;
    } else {
      if (current.player2Choice) return;
      current.player2Choice = choice;
    }

    rounds[idx] = current;
    tx.update(matchRef, {
      rounds,
      lastActivityAt: FieldValue.serverTimestamp(),
    });
  });

  const updated = await matchRef.get();
  if (updated.exists) {
    await resolveRoundIfReady(matchRef, updated.data() as MatchDoc);
    await clearChoicesForRound(matchId, roundNumber);
  }
}

async function clearChoicesForRound(matchId: string, roundNumber: number) {
  const choices = await db
    .collection("matches")
    .doc(matchId)
    .collection("rounds")
    .doc(String(roundNumber))
    .collection("choices")
    .get();
  const batch = db.batch();
  choices.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
}

/** Client writes matches/{id}/rounds/{round}/choices/{uid}; server applies move to the match doc. */
export const onPlayerChoice = onDocumentCreated(
  { document: "matches/{matchId}/rounds/{roundNumber}/choices/{userId}", region: REGION },
  async (event) => {
    const matchId = event.params.matchId;
    const uid = event.params.userId;
    const roundNumber = parseInt(event.params.roundNumber, 10);
    const snap = event.data;
    if (!snap || Number.isNaN(roundNumber)) return;

    const choice = snap.get("choice") as string;
    await applyPlayerChoice(matchId, uid, choice, roundNumber);
  },
);

export const resolveTimedOutRounds = onSchedule(
  { schedule: "every 1 minutes", region: REGION },
  async () => {
  const activeMatches = await db.collection("matches")
    .where("status", "==", "active")
    .get();

  for (const doc of activeMatches.docs) {
    await resolveRoundIfReady(doc.ref, doc.data() as MatchDoc);
  }
  },
);

export const cleanupStale = onSchedule(
  { schedule: "every 5 minutes", region: REGION },
  async () => {
  const cutoff = Timestamp.fromMillis(Date.now() - 60_000);

  const staleQueue = await db.collection("queue")
    .where("joinedAt", "<", cutoff)
    .get();
  const queueBatch = db.batch();
  staleQueue.docs.forEach((doc) => queueBatch.delete(doc.ref));
  await queueBatch.commit();

  const staleMatches = await db.collection("matches")
    .where("status", "==", "active")
    .where("lastActivityAt", "<", cutoff)
    .get();

  for (const doc of staleMatches.docs) {
    const match = doc.data() as MatchDoc;
    const batch = db.batch();
    batch.update(doc.ref, { status: "abandoned" });
    batch.update(db.collection("users").doc(match.player1), { activeMatchId: FieldValue.delete() });
    batch.update(db.collection("users").doc(match.player2), { activeMatchId: FieldValue.delete() });
    await batch.commit();
  }
  },
);
