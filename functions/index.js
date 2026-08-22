const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const Anthropic = require("@anthropic-ai/sdk");

if (!admin.apps.length) admin.initializeApp();

// The Anthropic key never ships in the app — it lives only here, as a Cloud
// Function secret, injected at runtime. Set it with:
//   firebase functions:secrets:set ANTHROPIC_API_KEY
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

/** Trim a profile down to the few fields that make a good opener, and cap lengths. */
function card(p) {
  if (!p || typeof p !== "object") return null;
  const s = (v, n) => (typeof v === "string" ? v.slice(0, n) : "");
  const vibes = Array.isArray(p.vibes)
    ? p.vibes.slice(0, 5).map((v) => String(v).slice(0, 24))
    : [];
  const name = s(p.name, 32).trim();
  if (!name) return null;
  return { name, bio: s(p.bio, 160), hereTo: s(p.hereTo, 48), vibes };
}

/**
 * generateIcebreakers — given the two connected profiles, return three short,
 * specific opening lines the user could send. Auth-gated (Firebase anonymous
 * auth is fine; we only need a signed-in caller). Returns { openers: string[] }.
 */
exports.generateIcebreakers = onCall(
  { secrets: [ANTHROPIC_API_KEY], region: "us-central1", cors: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const me = card(request.data && request.data.me);
    const them = card(request.data && request.data.them);
    if (!them) {
      throw new HttpsError("invalid-argument", "A valid match profile is required.");
    }

    const client = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() });

    const describe = (c) =>
      [
        `name: ${c.name}`,
        c.hereTo ? `here to: ${c.hereTo}` : null,
        c.vibes.length ? `vibes: ${c.vibes.join(", ")}` : null,
        c.bio ? `bio: "${c.bio}"` : null,
      ]
        .filter(Boolean)
        .join("\n");

    const prompt =
      `Two people just matched on a hyperlocal app used at concerts, parties, and ` +
      `local hangouts. Write 3 opening messages ${me ? me.name : "the first person"} ` +
      `could send ${them.name}. Each must be one sentence, casual, specific to what ` +
      `their cards share, and easy to reply to. No greetings like "hey", no pickup ` +
      `lines, no emoji unless it genuinely fits. Return ONLY a JSON array of 3 strings.\n\n` +
      (me ? `THEM (${me.name}, the sender):\n${describe(me)}\n\n` : "") +
      `RECIPIENT (${them.name}):\n${describe(them)}`;

    let text = "";
    try {
      // Haiku 4.5 — icebreakers are short and high-volume, so the cheapest
      // fast model is the right fit ($1/$5 per M tok vs Opus's $5/$25).
      const message = await client.messages.create({
        model: "claude-haiku-4-5",
        max_tokens: 400,
        messages: [{ role: "user", content: prompt }],
      });
      text = (message.content || [])
        .filter((b) => b.type === "text")
        .map((b) => b.text)
        .join("");
    } catch (err) {
      console.error("Anthropic call failed:", err && err.message);
      throw new HttpsError("internal", "Could not generate icebreakers right now.");
    }

    const openers = parseOpeners(text);
    if (!openers.length) {
      throw new HttpsError("internal", "No icebreakers were produced.");
    }
    return { openers };
  }
);

/** Pull a clean list of up to 3 opener strings out of the model's reply. */
function parseOpeners(text) {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start !== -1 && end > start) {
    try {
      const arr = JSON.parse(text.slice(start, end + 1));
      if (Array.isArray(arr)) {
        return arr
          .map((s) => String(s).trim())
          .filter(Boolean)
          .slice(0, 3);
      }
    } catch (_) {
      // fall through to line parsing
    }
  }
  return text
    .split("\n")
    .map((l) => l.replace(/^\s*(?:\d+[.)]|[-*"])\s*/, "").replace(/"\s*,?\s*$/, "").trim())
    .filter((l) => l.length > 0)
    .slice(0, 3);
}

/**
 * relayNotify — when an encrypted message lands in someone's mailbox, wake their device with a
 * content-less data push so it can drain + decrypt locally. The function never sees plaintext
 * (only ciphertext lives in the doc, and we don't even read it here).
 */
exports.relayNotify = onDocumentCreated(
  { document: "mailbox/{fp}/messages/{id}", region: "us-central1" },
  async (event) => {
    const fp = event.params.fp;
    const data = event.data && event.data.data();
    if (!data) return;
    try {
      // Token lives in an owner-only `tokens/{fp}` doc, not the readable profile.
      const tokenDoc = await admin.firestore().collection("tokens").doc(fp).get();
      const token = tokenDoc.get("token");
      if (!token) return;
      // Truly content-less: just a wake. The client drains its encrypted mailbox itself; the
      // sender's name/fingerprint never transit the push service.
      await admin.messaging().send({
        token,
        data: { type: "chat" },
        android: { priority: "high" },
      });
    } catch (e) {
      console.error("relayNotify failed:", e && e.message);
    }
  }
);

// The ONE writer of profiles/{fp}.verified. Verification level 1 = the caller's auth session
// genuinely carries a Google identity (checked in the token, not client-asserted) AND the
// caller owns the fingerprint's profile doc. Firestore rules reject any client write that
// touches `verified`, so this callable is the only path to the badge.
exports.claimVerified = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const fp = String((request.data && request.data.fingerprint) || "");
  if (!/^[0-9a-f]{64}$/i.test(fp)) throw new HttpsError("invalid-argument", "Bad fingerprint.");
  const identities =
    (request.auth.token && request.auth.token.firebase && request.auth.token.firebase.identities) || {};
  const hasGoogle = Array.isArray(identities["google.com"]) && identities["google.com"].length > 0;
  const hasPhone =
    (Array.isArray(identities["phone"]) && identities["phone"].length > 0) ||
    Boolean(request.auth.token && request.auth.token.phone_number);
  if (!hasGoogle && !hasPhone) {
    throw new HttpsError("failed-precondition", "This session has no Google account or phone number linked.");
  }
  const ref = admin.firestore().collection("profiles").doc(fp.toLowerCase());
  const snap = await ref.get();
  if (!snap.exists || snap.get("uid") !== request.auth.uid) {
    throw new HttpsError("permission-denied", "That profile isn't yours.");
  }
  await ref.set(
    { verified: true, verifiedAt: admin.firestore.FieldValue.serverTimestamp() },
    { merge: true }
  );
  return { verified: true };
});
