const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    email: "alice@example.com",
    displayName: "Alice Rahman",
    authProvider: "password",
    emailVerified: false,
    learningLevel: "Beginner",
    dailyGoalMinutes: 20,
    banglaExplanations: true,
    accountStatus: "ACTIVE",
    currentDay: 1,
    streakDays: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSessionPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    deviceName: "Pixel 8 (Android 15)",
    platform: "Android",
    authProvider: "password",
    activeSession: true,
    lastActiveAt: now,
    createdAt: now,
    ...overrides,
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

test("Unauthenticated user: cannot read or write user profiles or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .set(validSessionPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid UserProfile create, partial updates, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(userRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(userRef.get());

  // Sync email verification
  await assertSucceeds(
    userRef.update({
      emailVerified: true,
      authProvider: "password",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Update profile learning preferences
  await assertSucceeds(
    userRef.update({
      displayName: "Alice R.",
      learningLevel: "Intermediate",
      dailyGoalMinutes: 30,
      banglaExplanations: false,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Deactivate and reactivate account
  await assertSucceeds(
    userRef.update({
      accountStatus: "DEACTIVATED",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    userRef.update({
      accountStatus: "ACTIVE",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(userRef.delete());
});

test("Security bounds: reject profile listing, ghost fields, immutable mutation, future timestamps, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // Directory enumeration must fail
  await assertFails(aliceDb.collection("users").get());

  // Reject @DocumentId 'id' ghost field on create
  await assertFails(
    userRef.set(validProfilePayload(ALICE_UID, { id: ALICE_UID }))
  );

  // Reject invalid learningLevel enum
  await assertFails(
    userRef.set(validProfilePayload(ALICE_UID, { learningLevel: "Expert" }))
  );

  // Reject future timestamp
  await assertFails(
    userRef.set(
      validProfilePayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
      })
    )
  );

  await assertSucceeds(userRef.set(validProfilePayload(ALICE_UID)));

  // Reject immutable createdAt modification
  await assertFails(
    userRef.update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject unauthorized streak/currentDay tampering
  await assertFails(
    userRef.update({
      streakDays: 999,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's profile or sessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_alice")
      .set(validSessionPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_alice")
      .delete()
  );
});

test("Authenticated owner: UserSession create, list, update, and revoke succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions");

  // Unfiltered list on empty subcollection must succeed for owner
  await assertSucceeds(sessionsCol.get());

  await assertSucceeds(
    sessionsCol.doc("device_pixel_8").set(validSessionPayload(ALICE_UID))
  );

  await assertSucceeds(
    sessionsCol.doc("device_pixel_8").update({
      activeSession: false,
      lastActiveAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(sessionsCol.get());
  await assertSucceeds(sessionsCol.doc("device_pixel_8").delete());
});

test("VocabularyPreferences: owner create, update >= 5, validation bounds, and cross-user isolation", async () => {
  const now = new Date(Date.now() - 1000);
  const validVocabPrefs = (uid, overrides = {}) => ({
    userId: uid,
    dailyGoal: 30,
    preferredLevel: "A1",
    preferredCategoryIds: ["cat1", "cat2"],
    autoPronounce: true,
    reminderEnabled: false,
    reminderTime: "20:00",
    timezone: "Asia/Dhaka",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const alicePrefRef = aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("vocabulary");

  // Unauthenticated and cross-user access denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("preferences").doc("vocabulary").set(validVocabPrefs(ALICE_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("preferences").doc("vocabulary").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("preferences").doc("vocabulary").set(validVocabPrefs(ALICE_UID)));

  // Reject dailyGoal < 5 or invalid doc ID
  await assertFails(alicePrefRef.set(validVocabPrefs(ALICE_UID, { dailyGoal: 4 })));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("other").set(validVocabPrefs(ALICE_UID)));

  // Owner create & read succeed
  await assertSucceeds(alicePrefRef.set(validVocabPrefs(ALICE_UID)));
  await assertSucceeds(alicePrefRef.get());

  // Owner update dailyGoal to 50 succeeds while preserving createdAt
  await assertSucceeds(
    alicePrefRef.update({
      dailyGoal: 50,
      preferredLevel: "B1",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject tampering with createdAt
  await assertFails(
    alicePrefRef.update({
      dailyGoal: 60,
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(alicePrefRef.delete());
});

