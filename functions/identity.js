// Binds profiles/{fingerprint} to whoever holds that mesh identity's private key.
// The fingerprint is SHA-256 of an X25519 public key, which cannot sign, so the proof is an HMAC
// under the Diffie-Hellman secret shared with a one-time server key.
const crypto = require("node:crypto");
const { HttpsError } = require("firebase-functions/v2/https");

const CONTEXT = "locus-profile-claim-v1";
const TTL_MS = 2 * 60 * 1000;

function decode32(value) {
  if (typeof value !== "string" || value.length !== 44) return null;
  const bytes = Buffer.from(value, "base64");
  return bytes.length === 32 ? bytes : null;
}

async function createChallenge(db, uid) {
  const key = crypto.generateKeyPairSync("x25519");
  const challenge = Buffer.from(key.publicKey.export({ format: "jwk" }).x, "base64url").toString("base64");
  await db.collection("challenges").doc(uid).set({
    challenge,
    privateKey: key.privateKey.export({ format: "der", type: "pkcs8" }).toString("base64"),
    expiresAt: Date.now() + TTL_MS,
  });
  return { challenge };
}

async function proveProfile(db, uid, data, now = Date.now()) {
  const publicKey = decode32(data && data.publicKey);
  const proof = decode32(data && data.proof);
  if (!publicKey || !proof) throw new HttpsError("invalid-argument", "Bad proof.");
  const fingerprint = crypto.createHash("sha256").update(publicKey).digest("hex");

  await db.runTransaction(async (tx) => {
    const challengeRef = db.collection("challenges").doc(uid);
    const profileRef = db.collection("profiles").doc(fingerprint);
    const [pending, profile] = await Promise.all([tx.get(challengeRef), tx.get(profileRef)]);
    if (!pending.exists || pending.get("expiresAt") < now) {
      throw new HttpsError("failed-precondition", "Ask for a new challenge.");
    }

    let shared;
    try {
      shared = crypto.diffieHellman({
        privateKey: crypto.createPrivateKey({
          key: Buffer.from(pending.get("privateKey"), "base64"), format: "der", type: "pkcs8",
        }),
        publicKey: crypto.createPublicKey({
          key: { kty: "OKP", crv: "X25519", x: publicKey.toString("base64url") }, format: "jwk",
        }),
      });
    } catch (_) {
      throw new HttpsError("invalid-argument", "Bad key."); // low-order point
    }
    const expected = crypto.createHmac("sha256", shared)
      .update(`${CONTEXT}|${uid}|${fingerprint}|${pending.get("challenge")}`).digest();
    if (!crypto.timingSafeEqual(expected, proof)) {
      throw new HttpsError("permission-denied", "That identity isn't yours.");
    }

    tx.delete(challengeRef);
    if (profile.exists && profile.get("uid") === uid) return;
    // New, or held by another account: nothing the previous holder wrote can be trusted.
    tx.set(profileRef, { uid });
    tx.delete(db.collection("tokens").doc(fingerprint));
  });
  return { fingerprint };
}

async function deleteAccount(db, auth, uid) {
  const owned = await db.collection("profiles").where("uid", "==", uid).get();
  for (const profile of owned.docs) {
    await db.recursiveDelete(db.collection("mailbox").doc(profile.id));
    await db.collection("tokens").doc(profile.id).delete();
    await profile.ref.delete(); // last, so a failed run can be retried
  }
  await db.collection("challenges").doc(uid).delete();
  try {
    await auth.deleteUser(uid);
  } catch (e) {
    if (e.code !== "auth/user-not-found") throw e;
  }
  return { deleted: true };
}

module.exports = { createChallenge, proveProfile, deleteAccount };
