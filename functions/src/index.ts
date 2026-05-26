import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { calculateElo, isValidMove, MatchMode, Move, parseMatchMode, parseMatchModes, pickSharedMatchMode, resolveRound, winsToFinish } from "./game";

admin.initializeApp();
const db = admin.firestore();
db.settings({ ignoreUndefinedProperties: true });

const REGION = "us-central1";
/** Late player forfeits the whole match after this window. */
const ROUND_TIMEOUT_MS = 60_000;
const ELO_WINDOW = 200;

interface RoundDoc {
  roundNumber: number;
  player1Choice?: Move;
  player2Choice?: Move;
  winner?: string;
  resolvedAt?: Timestamp;
  deadline?: Timestamp;
  /** Set when throwsRock/Paper/Scissors have been incremented for this round. */
  throwStatsRecorded?: boolean;
}

interface MatchDoc {
  player1: string;
  player2: string;
  player1Name: string;
  player2Name: string;
  matchMode: MatchMode;
  status: "active" | "completed" | "abandoned";
  currentRound: number;
  player1Wins: number;
  player2Wins: number;
  rounds: RoundDoc[];
  winnerId?: string;
  player1EloDelta?: number;
  player2EloDelta?: number;
  player1Elo?: number;
  player2Elo?: number;
  createdAt: Timestamp;
  lastActivityAt: Timestamp;
}

const THROW_FIELDS: Record<Move, "throwsRock" | "throwsPaper" | "throwsScissors"> = {
  ROCK: "throwsRock",
  PAPER: "throwsPaper",
  SCISSORS: "throwsScissors",
};

async function recordMoveThrown(uid: string, move: Move): Promise<void> {
  await db.collection("users").doc(uid).update({
    [THROW_FIELDS[move]]: FieldValue.increment(1),
    lastSeen: FieldValue.serverTimestamp(),
  });
}

/** Record R/P/S counts once per resolved round (guarded by throwStatsRecorded). */
async function recordRoundMoveThrows(match: MatchDoc, round: RoundDoc): Promise<void> {
  if (round.throwStatsRecorded) return;
  const tasks: Promise<void>[] = [];
  if (round.player1Choice) {
    tasks.push(recordMoveThrown(match.player1, round.player1Choice));
  }
  if (round.player2Choice) {
    tasks.push(recordMoveThrown(match.player2, round.player2Choice));
  }
  if (tasks.length > 0) {
    await Promise.all(tasks);
  }
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

async function createMatch(playerA: string, playerB: string, matchMode: MatchMode): Promise<string> {
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
    matchMode,
    player1Elo: userA.elo,
    player2Elo: userB.elo,
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
  batch.update(db.collection("users").doc(playerA), {
    activeMatchId: matchRef.id,
    lastSeen: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(playerB), {
    activeMatchId: matchRef.id,
    lastSeen: FieldValue.serverTimestamp(),
  });
  await batch.commit();
  return matchRef.id;
}

async function tryMatch(uid: string, elo: number, matchModes: MatchMode[]): Promise<string | null> {
  const queueSnap = await db.collection("queue").orderBy("joinedAt", "asc").get();
  for (const doc of queueSnap.docs) {
    const otherId = doc.id;
    if (otherId === uid) continue;
    const otherModes = parseMatchModes(doc.get("matchModes"), doc.get("matchMode"));
    const sharedMode = pickSharedMatchMode(matchModes, otherModes);
    if (!sharedMode) continue;
    const otherElo = (doc.get("elo") as number) ?? 1000;
    if (Math.abs(otherElo - elo) <= ELO_WINDOW) {
      return createMatch(uid, otherId, sharedMode);
    }
  }
  return null;
}

function getOpenRound(match: MatchDoc): RoundDoc | undefined {
  return [...match.rounds].reverse().find((round) => !round.resolvedAt);
}

/** Firestore rejects undefined field values; strip them from round payloads. */
function sanitizeRound(round: RoundDoc): RoundDoc {
  const clean: RoundDoc = { roundNumber: round.roundNumber };
  if (round.player1Choice) clean.player1Choice = round.player1Choice;
  if (round.player2Choice) clean.player2Choice = round.player2Choice;
  if (round.winner) clean.winner = round.winner;
  if (round.resolvedAt) clean.resolvedAt = round.resolvedAt;
  if (round.deadline) clean.deadline = round.deadline;
  if (round.throwStatsRecorded) clean.throwStatsRecorded = true;
  return clean;
}

function sanitizeRounds(rounds: RoundDoc[]): RoundDoc[] {
  return rounds.map(sanitizeRound);
}

async function abandonMatch(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  rounds: RoundDoc[],
  roundIndex: number,
  now: Timestamp,
): Promise<void> {
  rounds[roundIndex] = sanitizeRound({
    ...rounds[roundIndex],
    resolvedAt: now,
  });
  const batch = db.batch();
  batch.update(matchRef, {
    status: "abandoned",
    rounds: sanitizeRounds(rounds),
    lastActivityAt: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(match.player1), { activeMatchId: FieldValue.delete() });
  batch.update(db.collection("users").doc(match.player2), { activeMatchId: FieldValue.delete() });
  await batch.commit();
}

async function finalizeMatch(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  winnerId: string,
  options?: { forfeit?: boolean },
): Promise<void> {
  const [p1Snap, p2Snap] = await Promise.all([
    db.collection("users").doc(match.player1).get(),
    db.collection("users").doc(match.player2).get(),
  ]);

  const p1Elo = (p1Snap.get("elo") as number) ?? 1000;
  const p2Elo = (p2Snap.get("elo") as number) ?? 1000;
  const p1Score = winnerId === match.player1 ? 1 : 0;
  const elo = calculateElo(p1Elo, p2Elo, p1Score);

  const winsNeeded = winsToFinish(parseMatchMode(match.matchMode));
  let player1Wins = match.player1Wins;
  let player2Wins = match.player2Wins;
  if (options?.forfeit) {
    if (winnerId === match.player1) {
      player1Wins = Math.max(player1Wins, winsNeeded);
    } else {
      player2Wins = Math.max(player2Wins, winsNeeded);
    }
  }

  const batch = db.batch();
  batch.update(matchRef, {
    status: "completed",
    winnerId,
    player1Wins,
    player2Wins,
    rounds: sanitizeRounds(match.rounds),
    player1Elo: p1Elo,
    player2Elo: p2Elo,
    player1EloDelta: elo.deltaA,
    player2EloDelta: elo.deltaB,
    lastActivityAt: FieldValue.serverTimestamp(),
  });

  batch.update(db.collection("users").doc(match.player1), {
    elo: elo.newA,
    wins: FieldValue.increment(winnerId === match.player1 ? 1 : 0),
    losses: FieldValue.increment(winnerId === match.player1 ? 0 : 1),
    activeMatchId: FieldValue.delete(),
    lastSeen: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(match.player2), {
    elo: elo.newB,
    wins: FieldValue.increment(winnerId === match.player2 ? 1 : 0),
    losses: FieldValue.increment(winnerId === match.player2 ? 0 : 1),
    activeMatchId: FieldValue.delete(),
    lastSeen: FieldValue.serverTimestamp(),
  });
  await batch.commit();
}

async function resolveRoundIfReady(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  options?: { forceDeadline?: boolean },
): Promise<void> {
  const round = getOpenRound(match);
  if (!round) return;

  const now = Timestamp.now();
  const p1Choice = round.player1Choice;
  const p2Choice = round.player2Choice;
  const deadlinePassed =
    options?.forceDeadline === true ||
    (round.deadline ? round.deadline.toMillis() <= now.toMillis() : false);

  const rounds = [...match.rounds];
  const roundIndex = rounds.findIndex((r) => r.roundNumber === round.roundNumber && !r.resolvedAt);
  if (roundIndex < 0) return;

  // Both players submitted — resolve normally.
  if (p1Choice && p2Choice) {
    // fall through to winner resolution below
  } else if (deadlinePassed) {
    // Late player forfeits the entire series (not just the round).
    if (p1Choice && !p2Choice) {
      await recordRoundMoveThrows(match, { ...round, player1Choice: p1Choice });
      rounds[roundIndex] = sanitizeRound({
        ...round,
        player1Choice: p1Choice,
        throwStatsRecorded: true,
        winner: match.player1,
        resolvedAt: now,
      });
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player1,
        { forfeit: true },
      );
      return;
    }
    if (!p1Choice && p2Choice) {
      await recordRoundMoveThrows(match, { ...round, player2Choice: p2Choice });
      rounds[roundIndex] = sanitizeRound({
        ...round,
        player2Choice: p2Choice,
        throwStatsRecorded: true,
        winner: match.player2,
        resolvedAt: now,
      });
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player2,
        { forfeit: true },
      );
      return;
    }
    // Neither player submitted in time — cancel the match (no Elo change).
    await abandonMatch(matchRef, match, rounds, roundIndex, now);
    return;
  } else {
    return;
  }

  await recordRoundMoveThrows(match, { ...round, player1Choice: p1Choice, player2Choice: p2Choice });

  let winner: string;
  const result = resolveRound(p1Choice!, p2Choice!);
  if (result === "tie") {
    winner = "tie";
  } else if (result === "player1") {
    winner = match.player1;
  } else {
    winner = match.player2;
  }

  let player1Wins = match.player1Wins;
  let player2Wins = match.player2Wins;
  if (winner === match.player1) player1Wins += 1;
  if (winner === match.player2) player2Wins += 1;

  const winsNeeded = winsToFinish(parseMatchMode(match.matchMode));

  if (player1Wins >= winsNeeded) {
    rounds[roundIndex] = sanitizeRound({
      ...round,
      winner,
      resolvedAt: now,
      player1Choice: p1Choice,
      player2Choice: p2Choice,
      throwStatsRecorded: true,
    });
    await finalizeMatch(
      matchRef,
      { ...match, rounds: sanitizeRounds(rounds), player1Wins, player2Wins },
      match.player1,
    );
    return;
  }
  if (player2Wins >= winsNeeded) {
    rounds[roundIndex] = sanitizeRound({
      ...round,
      winner,
      resolvedAt: now,
      player1Choice: p1Choice,
      player2Choice: p2Choice,
      throwStatsRecorded: true,
    });
    await finalizeMatch(
      matchRef,
      { ...match, rounds: sanitizeRounds(rounds), player1Wins, player2Wins },
      match.player2,
    );
    return;
  }

  if (winner === "tie") {
    const nextRoundNumber = match.currentRound + 1;
    const nextDeadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
    rounds[roundIndex] = sanitizeRound({
      ...round,
      player1Choice: p1Choice,
      player2Choice: p2Choice,
      throwStatsRecorded: true,
      winner: "tie",
      resolvedAt: now,
    });
    rounds.push({ roundNumber: nextRoundNumber, deadline: nextDeadline });
    await matchRef.update({
      rounds: sanitizeRounds(rounds),
      currentRound: nextRoundNumber,
      player1Wins,
      player2Wins,
      lastActivityAt: FieldValue.serverTimestamp(),
    });
    return;
  }

  rounds[roundIndex] = sanitizeRound({
    ...round,
    player1Choice: p1Choice,
    player2Choice: p2Choice,
    throwStatsRecorded: true,
    winner,
    resolvedAt: now,
  });

  const nextRoundNumber = match.currentRound + 1;
  const nextDeadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
  await matchRef.update({
    rounds: sanitizeRounds([...rounds, { roundNumber: nextRoundNumber, deadline: nextDeadline }]),
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

    await db.collection("users").doc(uid).update({
      lastSeen: FieldValue.serverTimestamp(),
    });

    const profile = await getUserProfile(uid);
    if (profile.activeMatchId) {
      const active = await db.collection("matches").doc(profile.activeMatchId).get();
      if (active.exists && active.get("status") === "active") {
        await db.collection("queue").doc(uid).delete();
        return;
      }
    }

    const elo = (data.elo as number) ?? profile.elo;
    const matchModes = parseMatchModes(data.matchModes, data.matchMode);
    await tryMatch(uid, elo, matchModes);
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

    const rounds = [...match.rounds];
    const idx = rounds.findIndex((r) => r.roundNumber === roundNumber && !r.resolvedAt);
    if (idx < 0) return;

    const current = { ...rounds[idx] };
    if (uid === match.player1) {
      if (current.player1Choice) return;
      current.player1Choice = choice as Move;
    } else {
      if (current.player2Choice) return;
      current.player2Choice = choice as Move;
    }

    rounds[idx] = sanitizeRound(current);
    tx.update(matchRef, {
      rounds: sanitizeRounds(rounds),
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

/** Copy pending subcollection choices onto the match doc before timeout/forfeit resolution. */
async function mergeChoicesFromSubcollection(
  matchId: string,
  roundNumber: number,
): Promise<MatchDoc | null> {
  const matchRef = db.collection("matches").doc(matchId);
  const choicesSnap = await db
    .collection("matches")
    .doc(matchId)
    .collection("rounds")
    .doc(String(roundNumber))
    .collection("choices")
    .get();

  if (choicesSnap.empty) {
    const snap = await matchRef.get();
    return snap.exists ? (snap.data() as MatchDoc) : null;
  }

  const outcome = await db.runTransaction(async (tx) => {
    const snap = await tx.get(matchRef);
    if (!snap.exists) return null;

    const match = snap.data() as MatchDoc;
    if (match.status !== "active") {
      return { match, recorded: [] as Array<{ uid: string; move: Move }> };
    }

    const rounds = [...match.rounds];
    const idx = rounds.findIndex((r) => r.roundNumber === roundNumber && !r.resolvedAt);
    if (idx < 0) return { match, recorded: [] as Array<{ uid: string; move: Move }> };

    const recorded: Array<{ uid: string; move: Move }> = [];
    const current = { ...rounds[idx] };
    for (const doc of choicesSnap.docs) {
      const uid = doc.id;
      const choice = doc.get("choice") as string;
      if (!isValidMove(choice)) continue;
      if (uid === match.player1 && !current.player1Choice) {
        current.player1Choice = choice;
        recorded.push({ uid, move: choice });
      } else if (uid === match.player2 && !current.player2Choice) {
        current.player2Choice = choice;
        recorded.push({ uid, move: choice });
      }
    }

    if (recorded.length === 0) return { match, recorded };

    rounds[idx] = sanitizeRound(current);
    tx.update(matchRef, {
      rounds: sanitizeRounds(rounds),
      lastActivityAt: FieldValue.serverTimestamp(),
    });
    return { match: { ...match, rounds: sanitizeRounds(rounds) }, recorded };
  });

  return outcome?.match ?? null;
}

/** Client writes when the round timer hits zero; resolves immediately (scheduler is backup). */
export const onRoundTimeout = onDocumentCreated(
  { document: "matches/{matchId}/rounds/{roundNumber}/timeoutRequests/{requestId}", region: REGION },
  async (event) => {
    const matchId = event.params.matchId;
    const roundNumber = parseInt(event.params.roundNumber, 10);
    if (Number.isNaN(roundNumber)) return;

    const matchRef = db.collection("matches").doc(matchId);
    const merged = await mergeChoicesFromSubcollection(matchId, roundNumber);
    if (!merged) return;

    await resolveRoundIfReady(matchRef, merged, { forceDeadline: true });

    const after = await matchRef.get();
    if (!after.exists) return;
    const match = after.data() as MatchDoc;
    const open = getOpenRound(match);
    if (open && open.roundNumber === roundNumber) {
      await clearChoicesForRound(matchId, roundNumber);
    }
  },
);

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
    const match = doc.data() as MatchDoc;
    const open = getOpenRound(match);
    if (!open) continue;
    const merged = await mergeChoicesFromSubcollection(doc.id, open.roundNumber);
    if (merged) {
      await resolveRoundIfReady(doc.ref, merged);
    }
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
