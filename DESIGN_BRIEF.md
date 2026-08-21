# Locus — UI redesign brief (prompt for Claude design)

Paste everything below the line into Claude (design mode). It is self-contained.

---

You are the design lead for **Locus**, a native Android app. I want a complete, original visual design system and screen-by-screen specs I can implement directly in Jetpack Compose + Material 3. Give me deliberate, opinionated choices — not a template. Take one real aesthetic risk and justify it.

## What Locus is

Locus is a **hyperlocal discovery app**: it shows you the people physically around you — at a concert, festival, party, campus, or any crowd — and lets you connect and chat. It runs over an **offline Bluetooth mesh** between phones: **no login, no accounts, no phone number, no servers required**. Your identity is a cryptographic key that never leaves your device. It is deliberately **not** framed as a dating app; it's about meeting people in the room you're already in. **18+.**

The three-word promise: **discover · connect · chat.** The felt idea is *proximity as signal* — people near you "emit," and the app is a receiver.

## Who it's for and how it should feel

Adults at social gatherings, phone in hand, often in low light, sometimes with no internet. The redesign brief is one word: **modern and classy.** Today the app leans loud/neon-rave; I want it to feel **premium, trustworthy, and calm-but-alive** — closer to a well-made music or finance app than a nightclub flyer. It must still feel social and a little magnetic. Two non-negotiable emotional notes: **private & safe** (this is the product's spine) and **effortless** (no sign-up, works offline — the design should make that feel like a feature, not an apology).

## The core loop and screens to design

1. **Age gate** — one-time 18+ confirmation. First thing a user sees.
2. **Permissions / onboarding** — explain Bluetooth + location (Android requires location for BLE scanning) and privacy, then grant. Currently reads like a raw system checklist; make it a confident, reassuring first impression.
3. **Make your card** — the profile a user broadcasts: emoji avatar (picked from a set), display name, optional age, one-liner (≤160), "here to…" intent (meet new people / find my crew / see where the night goes / just vibing), and up to 5 vibe tags (music, dancing, chill, foodie, artsy, night owl, etc.). This is a long form — make it feel quick and expressive.
4. **Discover** (primary tab) — a swipeable **card deck** of people in range. Swipe right = connect, left = pass. Needs: the card face (avatar, name+age, "in range now" indicator, "here to…", one-liner, vibe chips), a peeked next card, swipe stamps (CONNECT / PASS), big pass/connect buttons, a "⚡ N for you" tray for people who already liked you, and a strong **empty/scanning state** (no one in range yet — this is common and must feel intentional, not broken).
5. **Match / "connected" moment** — full-screen celebration when two people mutually connect. The one place the design is allowed to be exuberant.
6. **Connections** (tab) — your mutual connections; tap to open an encrypted 1:1 chat. Long-press to block/report.
7. **Room** (tab) — a venue-wide public chat channel for everyone in range.
8. **You** (tab) — edit your card, invite others.
9. Supporting: **block/report safety dialog** (reachable from any stranger's card), **invite sheet** (share link + offline "beam the app" APK transfer), **AI icebreaker sheet** (3 suggested openers).

## Hard constraints (please design within these)

- **Platform:** native Android, Jetpack Compose, Material 3. Output must map to Compose primitives — deliver **color tokens (hex), a type scale in sp, spacing in dp, corner radii, elevation, and motion specs**, plus component anatomy per screen. Assume `MaterialTheme.colorScheme` is the source of truth for standard semantics.
- **Dark-first, and resolve the theme story.** Today the app follows the system light/dark setting, so its neon palette renders on a washed-out white background in light mode — it looks broken. Decide deliberately: either **commit to a single, polished identity** (my lean: a refined dark identity that always looks intentional), or design a **genuinely dual light+dark** system where both are first-class. State which and give both palettes if dual.
- **Legibility in the dark, at arm's length, one-handed.** Big touch targets, high contrast for names/actions, thumb-reachable primary actions.
- **Offline & private is the brand.** No stock "social app" clichés. The privacy/safety story (no account, keys on device, block/report, "your location is never shared") should be expressed through design, not just copy.
- **Monetization tension to resolve:** there is currently a persistent AdMob/GAM banner pinned app-wide (including onboarding). If it stays, design a slot that doesn't cheapen the premium feel or contradict "private/safe"; if you'd argue it out of some surfaces, say so.
- **Performance:** the current design uses many stacked radial-glow/blur effects that choke weaker devices. Favor effects that render cheaply (crisp strokes, gradients, single-layer shadows) over heavy multi-layer glows.

## What I want back, in order

1. **Design thesis** — 3–4 sentences: the concept, the feeling, and the one risk you're taking. Explicitly say how it reads as *classy* rather than *rave*, and how it signals *private/safe*.
2. **Token system:**
   - **Color** — 6–10 named hex tokens (backgrounds, surfaces, primary/secondary accents, an alert/celebration accent, text tiers, outline). If dual-mode, give light + dark.
   - **Type** — a display face, a body face, and a utility/mono face if warranted; pairing rationale, and a scale (size/weight/line-height/letter-spacing) for: eyebrow, title, card name, body, caption.
   - **Spacing / radius / elevation** — the dp system.
   - **Motion** — swipe physics, the match moment, the "scanning" ambient state, page transitions; durations + easing.
3. **The signature element** — the one thing Locus is remembered by, tied to the *proximity-as-signal* idea (today it's faint radar range-rings — keep, evolve, or replace, and say why). Describe it precisely enough to build.
4. **Screen specs** — for each screen above: layout (ASCII wireframe is fine), component anatomy, which tokens apply, and the key state variations (e.g., Discover: active deck vs. scanning-empty vs. deck-cleared; the "for you" tray).
5. **A self-critique pass** — before you finalize, check the plan against generic AI-design defaults (cream+serif, black+acid-green single accent, hairline-broadsheet) and against the current neon-nightlife look; call out anything that reads templated and revise it, saying what you changed.

Design for the specific world of a crowded room at night where phones find each other with no internet — let that be visible in every choice. Prioritize restraint: spend the boldness on the signature and the match moment, keep everything else quiet and confident.
