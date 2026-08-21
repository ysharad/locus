# Locus Privacy Policy

_Last updated: August 15, 2026_

Locus helps you discover, connect, and chat with people around you — primarily over a local Bluetooth mesh network, with an optional online layer. This policy explains exactly what data exists, where it goes, and who can see it.

## Your identity

Locus has no accounts, logins, emails, or phone numbers. Your identity is a cryptographic key pair generated on your device on first launch. Your public identity is a fingerprint of that key. Uninstalling the app destroys the key; there is no way for us (or anyone) to recover it.

## Your card

Your card — the name, age (optional), emoji, one-liner, vibes, and intent you choose — is written by you and is **public by design**:

- **Over Bluetooth**, your card is broadcast unencrypted to Locus users in radio range (and relayed a few hops further by their devices). Anyone nearby running compatible software can see it. Only put on your card what you'd say out loud in the room.
- **Online**, if your device has internet access, your card and a last-seen timestamp are also uploaded to our backend (Google Firebase, see below). This happens automatically; the app is fully functional without it.

## Connect signals and chats

- "Connect" (like) signals are sent **end-to-end encrypted** (Noise protocol) directly to the person you choose. We never see them; no server ever sees them. Who liked whom exists only on the two devices involved.
- Matches are computed locally on your device and stored only there.
- Private chats are end-to-end encrypted device-to-device over the mesh. We cannot read them.
- Public "Room" messages are visible to everyone in mesh range, like speaking in a shared space.

## What our backend stores (Google Firebase)

When internet is available, we store:

- Your public card (name, optional age, emoji, one-liner, vibes, intent)
- A last-seen timestamp
- An anonymous authentication ID that proves ownership of your card
- Reports you file about other users (see Safety)

We store **no** location, contacts, phone number, email, message content, likes, or matches. Firebase (Google LLC) processes this data on our behalf; see Google's privacy documentation for their infrastructure practices.

## Location and Bluetooth permissions

Android requires location permission for Bluetooth scanning. Locus uses it only to operate the local radio. **Your location is never recorded, stored, or transmitted** — not to us, not to other users. Proximity is implicit (radio range), never coordinates.

## Safety

You can block and report any person from their card or your connections list. Blocking is immediate and local. Reports (the reported card and fingerprint) are sent to us for review when internet is available.

## Data deletion

- Local data (your key, card, matches, chats): delete by uninstalling the app or using in-app data wipe.
- Backend data: to have your backend card and any related data deleted, contact us at the address below with your fingerprint. We will delete it within 30 days of a verified request.

## Children

Locus is for adults. You must be 18 or older to use it.

## Changes

We will update this policy when the app's data practices change and note the date above.

## Contact

<!-- TODO: replace with your real support address before the store listing goes live -->
Contact: hello@locus.app

---

Locus is built on the open-source bitchat mesh engine (GPL-3.0). Source code for this app is available as required by its license.
