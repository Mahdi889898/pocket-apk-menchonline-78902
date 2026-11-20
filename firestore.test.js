const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, now) {
  return {
    userId: uid,
    displayName: "سارا",
    avatarIndex: 2,
    wins: 0,
    gamesPlayed: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validRoomPayload(roomCode, hostUid, visibility, now) {
  return {
    roomCode,
    hostUid,
    title: "میز منچ دوستانه",
    visibility,
    status: "waiting",
    memberUids: [hostUid],
    seatUids: [hostUid, "", "", ""],
    seatNames: ["سارا", "", "", ""],
    seatTypes: ["human", "empty", "empty", "empty"],
    seatMuted: [false, false, false, false],
    seatSpeaking: [false, false, false, false],
    pieces: Array(16).fill(-1),
    currentTurn: 0,
    diceValue: 0,
    turnPhase: "roll",
    consecutiveSixes: 0,
    winnerSeat: -1,
    lastActionText: "اتاق ساخته شد",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated access to users and rooms is denied", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, now))
  );
  await assertFails(
    unauth.collection("rooms").where("visibility", "==", "public").where("status", "==", "waiting").get()
  );
  await assertFails(
    unauth.collection("rooms").doc("ROOM01").set(validRoomPayload("ROOM01", ALICE_UID, "public", now))
  );
});

test("User profile isolation: owner CRUD succeeds, cross-user and tampered stats fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, now))
  );

  // Cannot create profile with pre-inflated wins or wrong userId
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validProfilePayload(BOB_UID, now),
      wins: 99,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).get()
  );

  // Valid partial update by owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "سارا قهرمان",
      wins: 1,
      gamesPlayed: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field or createdAt overwrite
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Room lifecycle: create, public lobby query, join, start, gameplay, voice mute, finish lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // 1. Alice creates a public room and a private room
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PUB123").set(validRoomPayload("PUB123", ALICE_UID, "public", created))
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PRV999").set(validRoomPayload("PRV999", ALICE_UID, "private", created))
  );

  // Unfiltered list must fail; filtered public waiting list succeeds
  await assertFails(bobDb.collection("rooms").get());
  await assertSucceeds(
    bobDb.collection("rooms").where("visibility", "==", "public").where("status", "==", "waiting").get()
  );
  await assertSucceeds(
    aliceDb.collection("rooms").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // 2. Bob joins PUB123
  await assertSucceeds(
    bobDb.collection("rooms").doc("PUB123").update({
      memberUids: [ALICE_UID, BOB_UID],
      seatUids: [ALICE_UID, BOB_UID, "", ""],
      seatNames: ["سارا", "علی", "", ""],
      seatTypes: ["human", "human", "empty", "empty"],
      seatMuted: [false, false, false, false],
      lastActionText: "علی وارد اتاق شد",
      updatedAt: new Date(Date.now() - 5000),
    })
  );

  // Non-host Bob cannot start the game
  await assertFails(
    bobDb.collection("rooms").doc("PUB123").update({
      status: "playing",
      seatNames: ["سارا", "علی", "ربات ۳", "ربات ۴"],
      seatTypes: ["human", "human", "bot", "bot"],
      currentTurn: 0,
      diceValue: 0,
      turnPhase: "roll",
      lastActionText: "بازی شروع شد",
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // 3. Host Alice starts the game filling seats 2 & 3 with bots
  await assertSucceeds(
    aliceDb.collection("rooms").doc("PUB123").update({
      status: "playing",
      seatNames: ["سارا", "علی", "ربات ۳", "ربات ۴"],
      seatTypes: ["human", "human", "bot", "bot"],
      currentTurn: 0,
      diceValue: 0,
      turnPhase: "roll",
      lastActionText: "بازی شروع شد!",
      updatedAt: new Date(Date.now() - 3500),
    })
  );

  // 4. Outsider Charlie cannot mutate or read active room
  await assertFails(charlieDb.collection("rooms").doc("PUB123").get());
  await assertFails(
    charlieDb.collection("rooms").doc("PUB123").update({
      diceValue: 6,
      turnPhase: "move",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // 5. Member Bob updates voice mute / speaking state
  await assertSucceeds(
    bobDb.collection("rooms").doc("PUB123").update({
      seatMuted: [false, true, false, false],
      seatSpeaking: [false, false, false, false],
      updatedAt: new Date(Date.now() - 2500),
    })
  );

  // 6. Member Alice rolls dice and finishes game
  const nextPieces = Array(16).fill(-1);
  nextPieces[0] = 56;
  nextPieces[1] = 56;
  nextPieces[2] = 56;
  nextPieces[3] = 56;

  await assertSucceeds(
    aliceDb.collection("rooms").doc("PUB123").update({
      pieces: nextPieces,
      currentTurn: 0,
      diceValue: 6,
      turnPhase: "move",
      consecutiveSixes: 1,
      winnerSeat: 0,
      status: "finished",
      lastActionText: "سارا برنده بازی شد!",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 7. Terminal state lock: finished room cannot be updated
  await assertFails(
    aliceDb.collection("rooms").doc("PUB123").update({
      diceValue: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Voice signals & quick messages enforce room membership and sender identity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 4000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc("VOICE1").set({
      ...validRoomPayload("VOICE1", ALICE_UID, "public", now),
      memberUids: [ALICE_UID, BOB_UID],
      seatUids: [ALICE_UID, BOB_UID, "", ""],
      seatNames: ["سارا", "علی", "", ""],
      seatTypes: ["human", "human", "empty", "empty"],
      status: "playing",
    });
  });

  // Alice sends offer to Bob
  await assertSucceeds(
    aliceDb.collection("rooms").doc("VOICE1").collection("signals").doc("sig_1").set({
      roomId: "VOICE1",
      senderUid: ALICE_UID,
      targetUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      type: "offer",
      sdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      sdpMid: "",
      sdpMLineIndex: 0,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Bob queries signals addressed to him; unfiltered query fails
  await assertSucceeds(
    bobDb.collection("rooms").doc("VOICE1").collection("signals").where("targetUid", "==", BOB_UID).get()
  );
  await assertFails(
    bobDb.collection("rooms").doc("VOICE1").collection("signals").get()
  );

  // Charlie (non-member) cannot send signal or message
  await assertFails(
    charlieDb.collection("rooms").doc("VOICE1").collection("signals").doc("sig_bad").set({
      roomId: "VOICE1",
      senderUid: CHARLIE_UID,
      targetUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      type: "offer",
      sdp: "fake",
      sdpMid: "",
      sdpMLineIndex: 0,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Bob sends quick table reaction message
  await assertSucceeds(
    bobDb.collection("rooms").doc("VOICE1").collection("messages").doc("msg_1").set({
      roomId: "VOICE1",
      senderUid: BOB_UID,
      senderName: "علی",
      memberUids: [ALICE_UID, BOB_UID],
      text: "دمت گرم! عجب تاسی!",
      createdAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("rooms").doc("VOICE1").collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );
});
