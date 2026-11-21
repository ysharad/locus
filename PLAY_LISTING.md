# Locus — Play Store listing

Paste into Play Console → Main store listing. Character limits noted.
Last revised for vc67 (0.32.1) — sign-in, relay and rewarded ads all landed since the first draft.

## App name (≤ 30 chars)
Locus

## Short description (≤ 80 chars)
Discover and meet people around you — even with zero internet.

## Full description (≤ 4000 chars)
Locus shows you the people around you, right now.

At a gig, a festival, a conference, a campus, a bar — open Locus and see who else is here. Swipe, connect, start talking. No feed, no followers, no endless scroll. Just the room you're standing in.

WORKS WITH NO INTERNET
Locus finds people over Bluetooth, phone to phone. Dead signal at the venue, data run out, airplane mode crowd — it still works. When you do have a connection, your messages also reach people who have moved out of range.

ANONYMOUS BY DEFAULT
Start with a name you invent and an emoji. Nobody sees your number or your email. Sign in with Google or your phone number only if you want a Verified badge — it stays optional, and that badge is all it is used for.

PRIVATE BY DESIGN
• Chats are end-to-end encrypted.
• Nobody sees who liked whom. Only mutual connections are ever revealed.
• Your location is never recorded or shared — proximity is simply radio range.
• Block or report anyone, instantly.

MADE FOR MEETING PEOPLE
• A card, not a profile: a line about you and what you're here for.
• Mutual connections only, so no cold messages.
• Wave, connect, chat.

Locus is for adults (18+).

## App category
Social

## Store listing declarations
- Contains user-generated content: **Yes** (profile cards + chat; block + report present)
- Contains ads: **Yes** — optional rewarded video only (used to reveal who liked you); no banners, nothing forced
- In-app purchases: **No**
- Target audience: 18+

## Notes for the Data Safety form (must match the privacy policy)
Collected:
- Name (self-chosen display name on the card) — optional
- Approximate age — optional
- User-provided profile text (bio, vibes, intent)
- App activity: a "last seen" timestamp
- Authentication identifier (anonymous by default; Google account or phone number ONLY if the user signs in for a Verified badge)
- Messages: relayed **ciphertext only** while undelivered, deleted on delivery — unreadable to us

NOT collected: precise or coarse location, contacts, readable message content, likes, matches.
All profile data is user-entered. Encrypted in transit. Deletion available in-app and via
https://goapps.store/locus-delete.html

Privacy policy: https://goapps.store/locus-privacy.html

## Content rating questionnaire — key answers
- Social features / users interact / share content: Yes
- User-generated content shared with others, unmoderated: Yes (block + report available)
- Mature, sexual or violent content: No
- Target age: 18+

## Corrections vs. the original draft (do not reintroduce)
The first draft claimed "No account, no phone number, no email", "NO SERVER", "NO SIGN-UP" and
"Ads: No". All four are now false: optional Google/phone sign-in exists as the Verified anchor,
a Firebase relay carries sealed ciphertext for out-of-range delivery, and rewarded ads gate the
who-liked-you reveal.
