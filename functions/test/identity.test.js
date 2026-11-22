// Runs against local emulators only: firebase emulators:exec --project demo-locus ...
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const admin = require("firebase-admin");

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("Refusing to run outside the Firebase emulators.");
}
admin.initializeApp({ projectId: "demo-locus" });
const db = admin.firestore();
const { createChallenge, proveProfile, deleteAccount } = require("../identity");

const uid = () => `u${crypto.randomBytes(6).toString("hex")}`;
function identity() {
  const k = crypto.generateKeyPairSync("x25519");
  const pub = Buffer.from(k.publicKey.export({ format: "jwk" }).x, "base64url");
  return { ...k, pub, fp: crypto.createHash("sha256").update(pub).digest("hex") };
}
function answer(id, who, challenge) {
  const publicKey = crypto.createPublicKey({
    key: { kty: "OKP", crv: "X25519", x: Buffer.from(challenge, "base64").toString("base64url") },
    format: "jwk",
  });
  const shared = crypto.diffieHellman({ privateKey: id.privateKey, publicKey });
  return {
    publicKey: id.pub.toString("base64"),
    proof: crypto.createHmac("sha256", shared)
      .update(`locus-profile-claim-v1|${who}|${id.fp}|${challenge}`).digest("base64"),
  };
}
async function claim(id, who) {
  const { challenge } = await createChallenge(db, who);
  return proveProfile(db, who, answer(id, who, challenge));
}
// A client write, checked by the real firestore.rules.
async function clientWrite(who, path, fields) {
  const enc = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const token = `${enc({ alg: "none", typ: "JWT" })}.${enc({
    sub: who, user_id: who, iat: now, exp: now + 3600, aud: "demo-locus",
    iss: "https://securetoken.google.com/demo-locus", firebase: { sign_in_provider: "anonymous" },
  })}.`;
  const mask = Object.keys(fields).map((k) => `updateMask.fieldPaths=${k}`).join("&");
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-locus/databases/(default)/documents/${path}?${mask}`,
    { method: "PATCH", headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ fields: Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, { stringValue: v }])) }) });
  return res.status;
}

test("proof creates an owned profile", async () => {
  const id = identity(), who = uid();
  assert.deepEqual(await claim(id, who), { fingerprint: id.fp });
  assert.equal((await db.doc(`profiles/${id.fp}`).get()).get("uid"), who);
});

test("real owner takes a squatted profile back, without the squatter's key, badge or push token", async () => {
  const id = identity(), squatter = uid(), owner = uid();
  assert.equal(await clientWrite(squatter, `profiles/${id.fp}`, { uid: squatter, name: "x", chatPubKey: "bad" }), 200);
  await db.doc(`profiles/${id.fp}`).set({ verified: true }, { merge: true });
  await db.doc(`tokens/${id.fp}`).set({ uid: squatter, token: "bad" });
  await claim(id, owner);
  assert.deepEqual((await db.doc(`profiles/${id.fp}`).get()).data(), { uid: owner });
  assert.equal((await db.doc(`tokens/${id.fp}`).get()).exists, false);
});

test("after a claim the squatter is locked out and the owner can write", async () => {
  const id = identity(), squatter = uid(), owner = uid();
  await claim(id, owner);
  assert.equal(await clientWrite(squatter, `profiles/${id.fp}`, { uid: squatter, name: "x" }), 403);
  assert.equal(await clientWrite(owner, `profiles/${id.fp}`, { uid: owner, name: "Owner" }), 200);
});

test("re-proving keeps the owner's card", async () => {
  const id = identity(), who = uid();
  await db.doc(`profiles/${id.fp}`).set({ uid: who, name: "A", verified: true });
  await claim(id, who);
  assert.deepEqual((await db.doc(`profiles/${id.fp}`).get()).data(), { uid: who, name: "A", verified: true });
});

test("knowing only the public key is not enough", async () => {
  const victim = identity(), attacker = identity(), who = uid();
  const { challenge } = await createChallenge(db, who);
  const forged = { ...answer(attacker, who, challenge), publicKey: victim.pub.toString("base64") };
  await assert.rejects(proveProfile(db, who, forged), { code: "permission-denied" });
  assert.equal((await db.doc(`profiles/${victim.fp}`).get()).exists, false);
});

test("a proof made for one account cannot be used by another", async () => {
  const id = identity(), a = uid(), b = uid();
  const { challenge } = await createChallenge(db, b);
  await assert.rejects(proveProfile(db, b, answer(id, a, challenge)), { code: "permission-denied" });
});

test("a proof cannot be replayed", async () => {
  const id = identity(), who = uid();
  const proof = answer(id, who, (await createChallenge(db, who)).challenge);
  await proveProfile(db, who, proof);
  await assert.rejects(proveProfile(db, who, proof), { code: "failed-precondition" });
});

test("an expired challenge is refused", async () => {
  const id = identity(), who = uid();
  const proof = answer(id, who, (await createChallenge(db, who)).challenge);
  await assert.rejects(proveProfile(db, who, proof, Date.now() + 121000), { code: "failed-precondition" });
});

test("malformed input is refused", async () => {
  for (const data of [null, {}, { publicKey: "AA==", proof: "AA==" }, { publicKey: 5, proof: [] }]) {
    await assert.rejects(proveProfile(db, uid(), data), { code: "invalid-argument" });
  }
});

test("challenges are invisible to clients", async () => {
  const who = uid();
  await createChallenge(db, who);
  assert.equal(await clientWrite(who, `challenges/${who}`, { pub: "x" }), 403);
});

test("delete removes every profile, token and queued message of the caller, and the sign-in", async () => {
  const { uid: who } = await admin.auth().createUser({});
  for (const fp of [identity().fp, identity().fp]) {
    await db.doc(`profiles/${fp}`).set({ uid: who, name: "A" });
    await db.doc(`tokens/${fp}`).set({ uid: who, token: "t" });
    await db.doc(`mailbox/${fp}/messages/m1`).set({ ct: "c" });
  }
  const fps = (await db.collection("profiles").where("uid", "==", who).get()).docs.map((d) => d.id);
  await createChallenge(db, who);
  await deleteAccount(db, admin.auth(), who);
  for (const fp of fps) {
    for (const p of [`profiles/${fp}`, `tokens/${fp}`, `mailbox/${fp}/messages/m1`]) {
      assert.equal((await db.doc(p).get()).exists, false, p);
    }
  }
  assert.equal((await db.doc(`challenges/${who}`).get()).exists, false);
  await assert.rejects(admin.auth().getUser(who), { code: "auth/user-not-found" });
});

test("delete leaves other people's data alone", async () => {
  const { uid: who } = await admin.auth().createUser({});
  const fp = identity().fp;
  await db.doc(`profiles/${fp}`).set({ uid: "someone-else" });
  await db.doc(`mailbox/${fp}/messages/m1`).set({ ct: "c" });
  await deleteAccount(db, admin.auth(), who);
  assert.equal((await db.doc(`profiles/${fp}`).get()).exists, true);
  assert.equal((await db.doc(`mailbox/${fp}/messages/m1`).get()).exists, true);
});

test("delete can be retried after the sign-in is already gone", async () => {
  await deleteAccount(db, admin.auth(), uid());
});
