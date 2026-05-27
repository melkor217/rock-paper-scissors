import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  calculateElo,
  isValidMove,
  MatchMode,
  Move,
  parseMatchMode,
  parseMatchModes,
  pickSharedMatchMode,
  resolveRound,
  seriesOutcomeAfterRound,
  countRoundWins,
} from "./game";
import {
  applyClockIncrement,
  clockExpiry,
  initialClockFields,
  tickClocks,
} from "./clockControl";
import {
  computeMoveMs,
  existingMoveMs,
  MoveTimingSlot,
  ROUND_TIMEOUT_MS,
  withMoveMs,
} from "./moveTiming";

admin.initializeApp();
const db = admin.firestore();
db.settings({ ignoreUndefinedProperties: true });

const REGION = "us-central1";
const ELO_WINDOW = 300;

interface RoundDoc {
  roundNumber: number;
  player1Choice?: Move;
  player2Choice?: Move;
  winner?: string;
  resolvedAt?: Timestamp;
  startedAt?: Timestamp;
  deadline?: Timestamp;
  /** Ms from round start until each player locked in a move. */
  player1MoveMs?: number;
  player2MoveMs?: number;
  /** Set when throwsRock/Paper/Scissors have been incremented for this round. */
  throwStatsRecorded?: boolean;
  /** Set when roundsWon/Lost/Draw have been incremented for this round. */
  roundStatsRecorded?: boolean;
  endReason?: RoundEndReason;
}

type RoundEndReason = "normal" | "round_timeout" | "clock_timeout" | "cancelled";

const PLAYED_ROUND_END_REASON: RoundEndReason = "normal";
const ROUND_TIMEOUT_END_REASON: RoundEndReason = "round_timeout";
const CLOCK_TIMEOUT_END_REASON: RoundEndReason = "clock_timeout";
const CANCELLED_ROUND_END_REASON: RoundEndReason = "cancelled";

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
  /** Sum of per-round move times (ms) for each player in this match. */
  player1MoveTimeMs?: number;
  player2MoveTimeMs?: number;
  player1MoveCount?: number;
  player2MoveCount?: number;
  /** Remaining match thinking time (ms); runs until move is submitted each round. */
  player1ClockMs?: number;
  player2ClockMs?: number;
  clocksUpdatedAt?: Timestamp;
  winnerId?: string;
  /** Absolute match outcome for UI and queries. */
  resolution?: MatchResolution;
  /** Why the match ended: normal series, round deadline forfeit, or match clock forfeit. */
  endReason?: "normal" | "round_timeout" | "clock_timeout";
  player1EloDelta?: number;
  player2EloDelta?: number;
  player1Elo?: number;
  player2Elo?: number;
  createdAt: Timestamp;
  lastActivityAt: Timestamp;
}

interface RecordedMoveTiming {
  uid: string;
  slot: MoveTimingSlot;
  ms: number;
}

const THROW_FIELDS: Record<Move, "throwsRock" | "throwsPaper" | "throwsScissors"> = {
  ROCK: "throwsRock",
  PAPER: "throwsPaper",
  SCISSORS: "throwsScissors",
};

function moveTimingSlot(match: MatchDoc, uid: string): MoveTimingSlot | null {
  if (uid === match.player1) return "player1";
  if (uid === match.player2) return "player2";
  return null;
}

/** Stamp per-round move ms and return match/user increment deltas (once per player per round). */
function recordMoveTiming(
  round: RoundDoc,
  match: MatchDoc,
  uid: string,
  now: Timestamp,
): { round: RoundDoc; timing: RecordedMoveTiming } | null {
  const slot = moveTimingSlot(match, uid);
  if (!slot || existingMoveMs(round, slot) != null) return null;
  const ms = computeMoveMs(round, now, now);
  return {
    round: withMoveMs(round, slot, ms) as RoundDoc,
    timing: { uid, slot, ms },
  };
}

function matchTimingIncrements(timings: RecordedMoveTiming[]): Record<string, FirebaseFirestore.FieldValue> {
  const out: Record<string, FirebaseFirestore.FieldValue> = {};
  for (const t of timings) {
    if (t.slot === "player1") {
      out.player1MoveTimeMs = FieldValue.increment(t.ms);
      out.player1MoveCount = FieldValue.increment(1);
    } else {
      out.player2MoveTimeMs = FieldValue.increment(t.ms);
      out.player2MoveCount = FieldValue.increment(1);
    }
  }
  return out;
}

function applyRecordedTimingsToTransaction(
  tx: FirebaseFirestore.Transaction,
  timings: RecordedMoveTiming[],
): void {
  for (const t of timings) {
    tx.update(db.collection("users").doc(t.uid), {
      moveTimeMs: FieldValue.increment(t.ms),
      moveCount: FieldValue.increment(1),
      lastSeen: FieldValue.serverTimestamp(),
    });
  }
}

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

async function recordRoundOutcomeStats(
  player1: string,
  player2: string,
  winner: string,
): Promise<void> {
  const bump = (uid: string, patch: Record<string, FirebaseFirestore.FieldValue>) =>
    db.collection("users").doc(uid).update({
      ...patch,
      lastSeen: FieldValue.serverTimestamp(),
    });

  if (winner === "tie") {
    await Promise.all([
      bump(player1, { roundsDraw: FieldValue.increment(1) }),
      bump(player2, { roundsDraw: FieldValue.increment(1) }),
    ]);
    return;
  }

  const loser = winner === player1 ? player2 : player1;
  await Promise.all([
    bump(winner, { roundsWon: FieldValue.increment(1) }),
    bump(loser, { roundsLost: FieldValue.increment(1) }),
  ]);
}

/** Throw counts + per-player round W/L/D; marks stats recorded on returned round. */
async function recordRoundResolutionStats(
  match: MatchDoc,
  round: RoundDoc,
  winner: string,
): Promise<RoundDoc> {
  if (!round.throwStatsRecorded) {
    await recordRoundMoveThrows(match, round);
  }
  if (!round.roundStatsRecorded) {
    await recordRoundOutcomeStats(match.player1, match.player2, winner);
  }
  return {
    ...round,
    throwStatsRecorded: true,
    roundStatsRecorded: true,
  };
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
    player1MoveTimeMs: 0,
    player2MoveTimeMs: 0,
    player1MoveCount: 0,
    player2MoveCount: 0,
    ...initialClockFields(now),
    rounds: [{ roundNumber: 1, deadline, startedAt: now }],
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
  if (round.startedAt) clean.startedAt = round.startedAt;
  if (round.deadline) clean.deadline = round.deadline;
  if (round.player1MoveMs != null) clean.player1MoveMs = round.player1MoveMs;
  if (round.player2MoveMs != null) clean.player2MoveMs = round.player2MoveMs;
  if (round.throwStatsRecorded) clean.throwStatsRecorded = true;
  if (round.roundStatsRecorded) clean.roundStatsRecorded = true;
  if (round.endReason) clean.endReason = round.endReason;
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
    endReason: CANCELLED_ROUND_END_REASON,
  });
  const batch = db.batch();
  batch.update(matchRef, {
    status: "abandoned",
    resolution: "abandoned",
    rounds: sanitizeRounds(rounds),
    lastActivityAt: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(match.player1), { activeMatchId: FieldValue.delete() });
  batch.update(db.collection("users").doc(match.player2), { activeMatchId: FieldValue.delete() });
  await batch.commit();
}

type MatchEndReason = "normal" | "round_timeout" | "clock_timeout";
type MatchResolution = "player1_win" | "player2_win" | "draw" | "abandoned";

function matchResolutionForWinner(match: MatchDoc, winnerId: string): MatchResolution {
  return winnerId === match.player1 ? "player1_win" : "player2_win";
}

async function finalizeMatch(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  winnerId: string,
  options?: { forfeit?: boolean; endReason?: MatchEndReason },
): Promise<void> {
  const [p1Snap, p2Snap] = await Promise.all([
    db.collection("users").doc(match.player1).get(),
    db.collection("users").doc(match.player2).get(),
  ]);

  const p1Elo = (p1Snap.get("elo") as number) ?? 1000;
  const p2Elo = (p2Snap.get("elo") as number) ?? 1000;
  const p1Score = winnerId === match.player1 ? 1 : 0;
  const elo = calculateElo(p1Elo, p2Elo, p1Score);

  let player1Wins = match.player1Wins;
  let player2Wins = match.player2Wins;
  if (options?.forfeit) {
    player1Wins = countRoundWins(match.rounds, match.player1);
    player2Wins = countRoundWins(match.rounds, match.player2);
  }

  const endReason: MatchEndReason = options?.endReason ?? "normal";

  const batch = db.batch();
  batch.update(matchRef, {
    status: "completed",
    winnerId,
    endReason,
    resolution: matchResolutionForWinner(match, winnerId),
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

async function finalizeMatchDraw(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  player1Wins: number,
  player2Wins: number,
  rounds: RoundDoc[],
): Promise<void> {
  const [p1Snap, p2Snap] = await Promise.all([
    db.collection("users").doc(match.player1).get(),
    db.collection("users").doc(match.player2).get(),
  ]);

  const p1Elo = (p1Snap.get("elo") as number) ?? 1000;
  const p2Elo = (p2Snap.get("elo") as number) ?? 1000;

  const batch = db.batch();
  batch.update(matchRef, {
    status: "completed",
    winnerId: FieldValue.delete(),
    endReason: PLAYED_ROUND_END_REASON,
    resolution: "draw",
    player1Wins,
    player2Wins,
    rounds: sanitizeRounds(rounds),
    player1Elo: p1Elo,
    player2Elo: p2Elo,
    player1EloDelta: 0,
    player2EloDelta: 0,
    lastActivityAt: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(match.player1), {
    draws: FieldValue.increment(1),
    activeMatchId: FieldValue.delete(),
    lastSeen: FieldValue.serverTimestamp(),
  });
  batch.update(db.collection("users").doc(match.player2), {
    draws: FieldValue.increment(1),
    activeMatchId: FieldValue.delete(),
    lastSeen: FieldValue.serverTimestamp(),
  });
  await batch.commit();
}

async function applySeriesOutcome(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  rounds: RoundDoc[],
  roundIndex: number,
  round: RoundDoc,
  player1Wins: number,
  player2Wins: number,
  now: Timestamp,
  p1Choice: Move,
  p2Choice: Move,
  winner: string,
): Promise<boolean> {
  const mode = parseMatchMode(match.matchMode);
  const outcome = seriesOutcomeAfterRound(mode, player1Wins, player2Wins, round.roundNumber);

  rounds[roundIndex] = sanitizeRound({
    ...round,
    winner,
    resolvedAt: now,
    player1Choice: p1Choice,
    player2Choice: p2Choice,
    throwStatsRecorded: true,
    roundStatsRecorded: true,
    endReason: PLAYED_ROUND_END_REASON,
  });

  if (outcome.kind === "winner") {
    const winnerId = outcome.player === "player1" ? match.player1 : match.player2;
    await finalizeMatch(
      matchRef,
      { ...match, rounds: sanitizeRounds(rounds), player1Wins, player2Wins },
      winnerId,
    );
    return true;
  }
  if (outcome.kind === "draw") {
    await finalizeMatchDraw(matchRef, match, player1Wins, player2Wins, sanitizeRounds(rounds));
    return true;
  }
  return false;
}

async function persistClocks(
  matchRef: FirebaseFirestore.DocumentReference,
  clocks: ReturnType<typeof tickClocks>,
): Promise<void> {
  await matchRef.update({
    player1ClockMs: clocks.player1ClockMs,
    player2ClockMs: clocks.player2ClockMs,
    clocksUpdatedAt: clocks.clocksUpdatedAt,
    lastActivityAt: FieldValue.serverTimestamp(),
  });
}

async function resolveRoundIfReady(
  matchRef: FirebaseFirestore.DocumentReference,
  match: MatchDoc,
  options?: { forceDeadline?: boolean },
): Promise<void> {
  const round = getOpenRound(match);
  if (!round) return;

  const now = Timestamp.now();
  const ticked = tickClocks(match, round, now);
  match = { ...match, ...ticked };

  const p1Choice = round.player1Choice;
  const p2Choice = round.player2Choice;
  const deadlinePassed =
    options?.forceDeadline === true ||
    (round.deadline ? round.deadline.toMillis() <= now.toMillis() : false);

  const rounds = [...match.rounds];
  const roundIndex = rounds.findIndex((r) => r.roundNumber === round.roundNumber && !r.resolvedAt);
  if (roundIndex < 0) return;

  const expiry = clockExpiry(ticked.player1ClockMs, ticked.player2ClockMs, round);
  if (expiry === "player1") {
    const winner = match.player2;
    const pendingRound: RoundDoc = {
      ...round,
      ...(p2Choice ? { player2Choice: p2Choice } : {}),
      winner,
      resolvedAt: now,
      endReason: CLOCK_TIMEOUT_END_REASON,
    };
    const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
    rounds[roundIndex] = sanitizeRound(resolvedRound);
    await finalizeMatch(
      matchRef,
      { ...match, rounds: sanitizeRounds(rounds) },
      match.player2,
      { forfeit: true, endReason: "clock_timeout" },
    );
    return;
  }
  if (expiry === "player2") {
    const winner = match.player1;
    const pendingRound: RoundDoc = {
      ...round,
      ...(p1Choice ? { player1Choice: p1Choice } : {}),
      winner,
      resolvedAt: now,
      endReason: CLOCK_TIMEOUT_END_REASON,
    };
    const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
    rounds[roundIndex] = sanitizeRound(resolvedRound);
    await finalizeMatch(
      matchRef,
      { ...match, rounds: sanitizeRounds(rounds) },
      match.player1,
      { forfeit: true, endReason: "clock_timeout" },
    );
    return;
  }
  if (expiry === "both") {
    if (p1Choice && !p2Choice) {
      const winner = match.player1;
      const pendingRound: RoundDoc = {
        ...round,
        player1Choice: p1Choice,
        winner,
        resolvedAt: now,
        endReason: CLOCK_TIMEOUT_END_REASON,
      };
      const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
      rounds[roundIndex] = sanitizeRound(resolvedRound);
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player1,
        { forfeit: true, endReason: "clock_timeout" },
      );
      return;
    }
    if (!p1Choice && p2Choice) {
      const winner = match.player2;
      const pendingRound: RoundDoc = {
        ...round,
        player2Choice: p2Choice,
        winner,
        resolvedAt: now,
        endReason: CLOCK_TIMEOUT_END_REASON,
      };
      const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
      rounds[roundIndex] = sanitizeRound(resolvedRound);
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player2,
        { forfeit: true, endReason: "clock_timeout" },
      );
      return;
    }
    await abandonMatch(matchRef, match, rounds, roundIndex, now);
    return;
  }

  // Both players submitted — resolve normally.
  if (p1Choice && p2Choice) {
    // fall through to winner resolution below
  } else if (deadlinePassed) {
    // Late player forfeits the entire series (not just the round).
    if (p1Choice && !p2Choice) {
      const winner = match.player1;
      const pendingRound: RoundDoc = {
        ...round,
        player1Choice: p1Choice,
        winner,
        resolvedAt: now,
        endReason: ROUND_TIMEOUT_END_REASON,
      };
      const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
      rounds[roundIndex] = sanitizeRound(resolvedRound);
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player1,
        { forfeit: true, endReason: "round_timeout" },
      );
      return;
    }
    if (!p1Choice && p2Choice) {
      const winner = match.player2;
      const pendingRound: RoundDoc = {
        ...round,
        player2Choice: p2Choice,
        winner,
        resolvedAt: now,
        endReason: ROUND_TIMEOUT_END_REASON,
      };
      const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);
      rounds[roundIndex] = sanitizeRound(resolvedRound);
      await finalizeMatch(
        matchRef,
        { ...match, rounds: sanitizeRounds(rounds) },
        match.player2,
        { forfeit: true, endReason: "round_timeout" },
      );
      return;
    }
    // Neither player submitted in time — cancel the match (no Elo change).
    await abandonMatch(matchRef, match, rounds, roundIndex, now);
    return;
  } else {
    await persistClocks(matchRef, ticked);
    return;
  }

  let winner: string;
  const result = resolveRound(p1Choice!, p2Choice!);
  if (result === "tie") {
    winner = "tie";
  } else if (result === "player1") {
    winner = match.player1;
  } else {
    winner = match.player2;
  }

  const pendingRound: RoundDoc = {
    ...round,
    player1Choice: p1Choice,
    player2Choice: p2Choice,
    winner,
    resolvedAt: now,
    endReason: PLAYED_ROUND_END_REASON,
  };
  const resolvedRound = await recordRoundResolutionStats(match, pendingRound, winner);

  let player1Wins = match.player1Wins;
  let player2Wins = match.player2Wins;
  if (winner === match.player1) player1Wins += 1;
  if (winner === match.player2) player2Wins += 1;

  if (
    await applySeriesOutcome(
      matchRef,
      match,
      rounds,
      roundIndex,
      round,
      player1Wins,
      player2Wins,
      now,
      p1Choice!,
      p2Choice!,
      winner,
    )
  ) {
    return;
  }

  const nextRoundNumber = match.currentRound + 1;
  const nextDeadline = Timestamp.fromMillis(now.toMillis() + ROUND_TIMEOUT_MS);
  const incremented = applyClockIncrement(ticked.player1ClockMs, ticked.player2ClockMs);
  rounds[roundIndex] = sanitizeRound(resolvedRound);
  rounds.push({ roundNumber: nextRoundNumber, deadline: nextDeadline, startedAt: now });
  await matchRef.update({
    rounds: sanitizeRounds(rounds),
    currentRound: nextRoundNumber,
    player1Wins,
    player2Wins,
    player1ClockMs: incremented.player1ClockMs,
    player2ClockMs: incremented.player2ClockMs,
    clocksUpdatedAt: now,
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

    const now = Timestamp.now();
    let current = { ...rounds[idx] };
    const timings: RecordedMoveTiming[] = [];
    const ticked = tickClocks(match, current, now);

    if (uid === match.player1) {
      if (current.player1Choice) return;
      const stamped = recordMoveTiming(current, match, uid, now);
      if (stamped) {
        current = stamped.round;
        timings.push(stamped.timing);
      }
      current.player1Choice = choice as Move;
    } else {
      if (current.player2Choice) return;
      const stamped = recordMoveTiming(current, match, uid, now);
      if (stamped) {
        current = stamped.round;
        timings.push(stamped.timing);
      }
      current.player2Choice = choice as Move;
    }

    rounds[idx] = sanitizeRound(current);
    tx.update(matchRef, {
      rounds: sanitizeRounds(rounds),
      player1ClockMs: ticked.player1ClockMs,
      player2ClockMs: ticked.player2ClockMs,
      clocksUpdatedAt: ticked.clocksUpdatedAt,
      lastActivityAt: FieldValue.serverTimestamp(),
      ...matchTimingIncrements(timings),
    });
    applyRecordedTimingsToTransaction(tx, timings);
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
    const timings: RecordedMoveTiming[] = [];
    const now = Timestamp.now();
    let current = { ...rounds[idx] };
    const ticked = tickClocks(match, current, now);
    for (const doc of choicesSnap.docs) {
      const uid = doc.id;
      const choice = doc.get("choice") as string;
      if (!isValidMove(choice)) continue;
      if (uid === match.player1 && !current.player1Choice) {
        const stamped = recordMoveTiming(current, match, uid, now);
        if (stamped) {
          current = stamped.round;
          timings.push(stamped.timing);
        }
        current.player1Choice = choice;
        recorded.push({ uid, move: choice });
      } else if (uid === match.player2 && !current.player2Choice) {
        const stamped = recordMoveTiming(current, match, uid, now);
        if (stamped) {
          current = stamped.round;
          timings.push(stamped.timing);
        }
        current.player2Choice = choice;
        recorded.push({ uid, move: choice });
      }
    }

    if (recorded.length === 0) return { match, recorded };

    rounds[idx] = sanitizeRound(current);
    tx.update(matchRef, {
      rounds: sanitizeRounds(rounds),
      player1ClockMs: ticked.player1ClockMs,
      player2ClockMs: ticked.player2ClockMs,
      clocksUpdatedAt: ticked.clocksUpdatedAt,
      lastActivityAt: FieldValue.serverTimestamp(),
      ...matchTimingIncrements(timings),
    });
    applyRecordedTimingsToTransaction(tx, timings);
    return {
      match: { ...match, ...ticked, rounds: sanitizeRounds(rounds) },
      recorded,
    };
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

/** Lightweight RTT probe for in-app connection meter (requires auth). */
export const ping = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  return { serverTimeMs: Date.now() };
});

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
