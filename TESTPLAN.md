# Locus — Launch Test Plan & Ledger (v0.21.1 / vc26)

**Date:** 2026-08-21 · **Devices:** Nokia 6.1 (Android 10, `PL2G…1432`) + Oppo CPH2411 (Android 15, `XWYX…S4RC`)
**Method:** adb-driven (screencap + input + TestHook mesh rig + logcat dumps), both devices in parallel.
Legend: ✅ pass · ❌ fail (→ issue #) · ⚠️ pass-with-note · ⏭ n/a-deferred

## Efficiency doctrine (how this hits 500%)
1. **Two devices driven concurrently** from one host — every mesh test exercises send + receive simultaneously; no idle device.
2. **State toggles by adb, not fingers** — `svc wifi|data`, BT via cmd/service, airplane via `cmd connectivity`; each network state applied in seconds, screenshot-verified.
3. **TestHook mesh rig** (debug builds) — deterministic peer/session/DM assertions (`state`, `peers`, `dm_send/recv`) instead of waiting on UI; catches engine bugs UI hides.
4. **Logcat dumps per phase** (`logcat -d --pid`) — every sweep doubles as a crash/ANR/exception audit even when UI "looks fine".
5. **One ledger** (this file) — each test has an ID; failures link to issues; fixes link back to re-verification. Nothing gets lost.

---

## Phase 0 — Setup
- [ ] S1 Build current tree (assembleDebug arm64) — both phones get IDENTICAL apk
- [ ] S2 Clean install Oppo (uninstall first → onboarding fresh)
- [ ] S3 Clean install Nokia
- [ ] S4 Baseline: perms granted state, battery whitelist, screen-stay-on, logcat clean at idle

## Phase 1 — Onboarding & first-run (both devices, fresh)
- [ ] O1 Splash animation (rings draw-on, no white flash, no ANR)
- [ ] O2 Permission prime → system dialogs (A10: location; A15: nearby-devices + notifications) — grant path
- [ ] O3 Permission DENY path → explanatory screen, re-request works, no crash
- [ ] O4 Background-location + battery-optimization prompts
- [ ] O5 Age gate — copy, decline blocks, confirm proceeds, state persists across kill
- [ ] O6 Identity "Keep your chats?" — Skip path; Keep path (anon opt-in, no email required)
- [ ] O7 Make your card — name required, age slider bounds, bio limits, glyph rail, HERE-TO intents, vibes, save
- [ ] O8 Land on Discover; scanning empty state (searching rings animate)

## Phase 2 — Every screen/menu/submenu (single-device, parallel on both)
- [ ] U1 Discover CARDS: deck render, pass(✕)/connect stamps, next-card peek, deck-cleared state
- [ ] U2 Discover RADAR: toggle, rings, no-peers empty state, peer dots, connected=copper vs stranger=jade-pulse
- [ ] U3 "N IN RANGE" counter accuracy
- [ ] U4 ⚡ admirers tray pill → RevealGate (rewarded ad) → tray contents
- [ ] U5 Filters (≡): intent chips, vibes, age slider, live "Show N", undo-passes, persistence
- [ ] U6 Chats tab: empty state, likes badge count, rows in-range/away, filter pills, hold-to-block
- [ ] U7 Room tab: empty state, composer, send public msg, "N IN RANGE" header, glyph+name+time rows
- [ ] U8 TONIGHT overlay: recap, here-now list, WAVE/CHAT buttons, question → edit card, room link
- [ ] U9 You tab: card preview matches editor, visibility toggle, fingerprint 4-group format
- [ ] U10 Edit card: every field editable, changes propagate to peers' decks
- [ ] U11 QR screen: my code renders; scan → camera permission → (2-dev) instant match; self-scan guarded
- [ ] U12 Locus+ hub: Reveal + Boost cards, status lines, watch-ad buttons (test units fill?)
- [ ] U13 Settings hub + EVERY sub-screen opens/closes: Privacy / Notifications / Blocked / Safety / Discovery / Appearance / Data / About
- [ ] U14 Privacy sub: who-can-see segmented (Everyone/Connections-only), read-receipts/typing/last-seen toggles persist, key shown, export-JSON share intent, delete
- [ ] U15 Notifications sub: master toggle, per-type toggles, quiet-hours
- [ ] U16 Appearance: Auto/Dark/Light — apply + persist; EVERY screen legible in BOTH themes
- [ ] U17 Data: Delete-my-data → wipes → age gate; Firestore profile doc gone
- [ ] U18 About: policy link opens, mail intent, version string correct (0.21.1)
- [ ] U19 Invite sheet: share-link intent, beam-APK path
- [ ] U20 Icebreaker sheet ✨/✦: online generates 3 openers; offline → graceful fallback
- [ ] U21 Back-button from every screen/overlay (no dead ends, no app exit surprise)
- [ ] U22 Rotation on key screens (no crash/state loss) — Oppo
- [ ] U23 Rapid tab switching ×20 — no jank/crash
- [ ] U24 Long/unicode/emoji input in name, bio, chat, room

## Phase 3 — Two-device mesh core (the product)
- [ ] M1 Mutual discovery <30s foreground↔foreground; card content correct both ways
- [ ] M2 Like (A→B): silent on B (no tray reveal w/o ad? per spec: badge/⚡ count), no match yet
- [ ] M3 Like back (B→A): MUTUAL MATCH both sides — celebration full-screen, ENCRYPTED footer
- [ ] M4 Pass: silent (B never knows), removed from deck, undo-passes restores
- [ ] M5 1:1 chat A→B and B→A: delivery, ordering, self-bubble copper
- [ ] M6 Typing indicator (throttle 3s, TTL 6s expiry)
- [ ] M7 Reactions via long-press: ❤️🔥😂👍⚡, toggle-clear, grouped counts both sides
- [ ] M8 Reply-quote: strip above composer, quote block renders on both sides
- [ ] M9 Copy message action
- [ ] M10 Report & block from chat: chat closes, B's signals dropped at A, A invisible to B?
- [ ] M11 Wave (Tonight): WAVED state, received wave shows
- [ ] M12 Room broadcast: A msg appears on B, glyph+name right, both directions
- [ ] M13 Notification: B backgrounded → A msg → shade notification; REPLY FROM SHADE (regression v0.20!) — mesh survives
- [ ] M14 Notification tap → opens correct chat (not home tab)
- [ ] M15 Match while on other tab → celebration still fires
- [ ] M16 Visibility OFF on B → A stops seeing B (presence expiry ≤5min), B still sees A
- [ ] M17 Connections-only broadcast: stranger A can't see B's card, matched A can
- [ ] M18 Block from deck ⚑ + report reasons sheet → Firestore report written (online)
- [ ] M19 Unblock (Settings→Blocked) → rediscovery works
- [ ] M20 Boost: B's deck re-sorts A to front
- [ ] M21 Reveal tray: A sees B's like after ad-grant, 24h window stamped
- [ ] M22 QR connect-in-person: A shows, B scans → both matched instantly, chat works
- [ ] M23 TestHook deep-checks: session establish, dm_send/recv round-trip, file_send?, state dump anomalies

## Phase 4 — Connectivity matrix (per cell: discovery + chat + graceful degradation)
| State | Device A | Device B | Tests |
|---|---|---|---|
- [ ] N1 WiFi+Data ON both — everything (baseline; relay+mesh dedupe on msg.id)
- [ ] N2 **ALL RADIOS OFF except BT** both — FULL core loop must work: discovery, match, chat, room, wave, reactions (the product promise)
- [ ] N3 WiFi-only (no SIM/data) — mesh + relay + icebreakers OK
- [ ] N4 Data-only (WiFi off) — same
- [ ] N5 Airplane + BT re-enabled — mesh works
- [ ] N6 BT OFF — app explains (no crash), "waiting" states; BT ON → auto-recovery without restart
- [ ] N7 A offline+BT / B online — mesh chat works; B's relay sends queue harmlessly (no dupes when A returns)
- [ ] N8 Out of BLE range, both online, both keep-chats → RELAY delivers (foreground)
- [ ] N9 Same, recipient app KILLED → FCM push wakes → notification (Phase B)
- [ ] N10 Icebreakers offline → fallback copy, no spinner-forever
- [ ] N11 Rewarded ad offline → graceful "unavailable", tray NOT permanently locked
- [ ] N12 Screen locked/doze 10-min soak — peers survive on radar (50% duty cycle), no service death
- [ ] N13 Walk-away (BT range loss) → peer ages out ≤5min; return → re-appears quickly

## Phase 5 — Lifecycle & abuse
- [ ] L1 Force-stop → relaunch: state intact (profile, matches, chats, settings)
- [ ] L2 REINSTALL: blocklist survives? (fp-keyed — expected YES), matches/chats gone (expected), Firestore profile re-adopted
- [ ] L3 Process death mid-chat (am kill) → no data loss of delivered msgs
- [ ] L4 Battery-saver mode ON → power profile degrades but discovery continues (foreground)
- [ ] L5 Permission revoked mid-run (BT/nearby) → graceful re-prompt, no crash-loop
- [ ] L6 Notification permission denied (A15) → app fully usable, no silent failures elsewhere
- [ ] L7 Storage of 1000+ msgs / 20+ peers — retention caps respected, UI perf OK
- [ ] L8 Time skew: device clock ±1d — presence TTLs / quiet hours don't break core
- [ ] L9 A10 vs A15 parity notes (legacy BLE stack quirks)

## Phase 6 — Fix → rebuild → re-verify (ledger below)

## Issues ledger
| # | Sev | Area | Symptom | Status |
|---|---|---|---|---|
| 1 | HIGH | Room composer | A10: composer pushed off-screen while IME open (double inset: legacy window resize + imePadding) → typing blind | FIXED (SDK<30 skips imePadding) — verify on device |
| 2 | HIGH | Navigation | System BACK from ANY overlay (Settings/Filters/QR/Tonight/Plus/Identity/editor/1:1 chat/celebration) exits the app — no BackHandler anywhere | FIXED (ConnectRoot BackHandler peels layers; chat closes via hidePrivateChatSheet → auto-returns to Chats) — verify |
| 3 | LOW | Discover copy | Header "1 IN RANGE" while radar body says "No one in range yet" (peer connected, no card yet) — contradictory | OPEN (copy tweak candidate) |
| 4 | NOTE | Room rows | Row glyph is name-hash derived (◐) ≠ sender's card glyph (☾) — public channel carries no glyph | OPEN (cosmetic) |
| 5 | NOTE | A10 cold start | ~15 s to first frame on Nokia 6.1 (grey starting-window, no ink splash preview) | OPEN (observe; low-end HW) |
| 6 | MED | Battery | Nostr/Tor spin-retry every ~2 min while offline (pre-existing, inherited) | OPEN (deferred) |
| 7 | MED | Settings nav | Back from a Settings sub-screen closed ALL of Settings | FIXED (BackHandler in SettingsScreen) ✅ verified |
| 8 | LOW | Chat cosmetics | 1:1 incoming bubble: off-palette magenta/green "@name" tag + violet bubble/composer tint (inherited bitchat) | OPEN (polish) |
| 9 | — | Notifications | tap-through "bug" was a heads-up race in MY tap, not real | INVALID (retest passed, logcat-verified) |
| 10 | LOW | Chats list | No unread badge / last-message preview on rows (profile one-liner instead) | OPEN (design choice, note) |
| 11 | — | Room | "Empty room on Oppo" = ephemeral by design after reinstall | NOT A BUG |
| 12 | MED | Privacy | Invisibility didn't hide you from phones holding a live BLE link (stranger case) | FIXED (prune keeps connected-only-if-matched) ✅ |
| 13 | HIGH | Safety | **Block leaked**: blocked sender's DMs still rang notifications (+ relay path unguarded) | FIXED (admission gate + relay×2) ✅ verified silent |
| 14 | MED | Blocked list | One person = 2 rows (fp+peerID); single-row unblock left half-blocked | FIXED (dedupe list + stem unblock) |
| 15 | MED | Post-unblock | Person could never re-enter deck/radar (stale liked/passed persisted) | FIXED (block clears swipe slate) ✅ verified rediscovery |
| 16 | MED | Re-match | Peer who kept the old match swallowed re-likes → re-match impossible | FIXED (re-affirm like, queued + retried, ping-pong-guarded) ✅ verified celebration |
| 17 | LOW | BT-enable screen | Stock Android-green palette (inherited bitchat onboarding screen) | OPEN (cosmetic) |
| 18 | MED | Battery | Oppo idle drain ~11%/h overnight (screen off, offline; BLE duty + Nostr/Tor retry loop suspects) | OPEN — needs batterystats forensics on battery power |
| 19 | LOW | Delivery | ONE message ACKed ✓✓ but never stored (05:35, radio-cut window); unreproducible ×20 after; upstream ACK precedes storage | MONITOR |
| 20 | MED | Sign-in banner | "Sign in" opened Identity UNDER the modal chat sheet — looked dead | FIXED (close sheet first) — in build, retest pending |
| 21 | LOW | Rewarded ads | Google TEST end-card lacks working close (✕ unresponsive); reward flow itself completes | MONITOR w/ real units |
| 22 | MED | BT-off UX | Full-screen "Bluetooth Recommended" gate on every open while BT off; relay chats hidden behind small Skip | OPEN (product call) |
| 23 | LOW | BT-off staleness | Radar/"1 IN RANGE" showed stale peer ~1–2 min after BT off | OPEN (minor; TTL cleans up) |
| 24 | MED-HIGH | Relay notify | Relayed msg while app background-alive = NO notification (listener drains silently; FCM finds empty mailbox) | FIXED (ChatRelay.notifier → shared NotificationManager, full gating) — retest pending |
| 25 | LOW | Relay mailbox | Mesh-first dupes never delete their mailbox doc (accumulates, re-scanned every drain) | OPEN |
| 26 | MED | Radar sheet | Tapping a CONNECTED person's medallion offered "⚡ Connect" (nonsense for a match) | FIXED — sheet shows "CONNECTED · IN RANGE" + "✶ Chat" → opens the 1:1 directly (verified on-device) |
| 27 | HIGH | Room privacy | Favorite/unfavorite system notices rendered on the PUBLIC Room board (+ Nostr-pitching copy in 1:1) | FIXED — notices removed entirely (mesh + Nostr paths, state-only now); Room filters out all system-sender messages (verified: controls received, zero UI) |
| 28 | MED | Chats tab | No unread indicator on the bottom-bar Chats tab (old badge showed likes count — wrong concern) | FIXED — badge = # conversations with unread, clears on read (verified) |
| 29 | MED | Notifications | Small icon was bitchat's generic chat bubble; mesh-service notification used full-color mipmap → grey circle | FIXED — ic_notification = Locus contour mark (alpha-only, 8-stroke); MeshForegroundService + LocusMessagingService repointed (verified on A10) |
| 31 | HIGH | 1:1 composer | Send button re-sendable during the durable-write round-trip → N impatient taps = N duplicate messages (field cleared only in async callback; felt "shaky" on release build) | FIXED — optimistic clear on first tap + restore-on-reject (PrivateChatSheet onSend); built, NOT yet installed (Nokia off adb, Oppo on Play build) |
| 32 | INFO | Release build | First real-world run of the R8 release build (Play internal v0.22.0 on Oppo, fresh install): onboarding, discovery, match, mesh chat, ✓✓ all work — G4 partially validated | MONITOR |
| 33 | MED | Read receipts | "Shaky" — on chat-open, receipts sent ONLY if Noise session up at that instant; otherwise silently dropped forever → read state = radio luck | FIXED — pendingReadReceipts queue in PrivateChatManager, flushed on session-established (ChatViewModel hook) |
| 34 | MED | Typing indicator | Flickered mid-typing (3s throttle vs 6s TTL left no jitter budget) and lingered after the message arrived | FIXED — throttle 2.5s/TTL 10s + clearTypingFrom() on real incoming message (IncomingMessageAdmission) |
| 30 | MED | App liveness | Oppo app found force-stopped since morning N9 test → invisible to mesh while Nokia said "0 peers" + stale "Ravi is here" notif. Aggressive OEMs (ColorOS) can do the same to real users | OPEN — consider auto-launch/battery-exemption guidance on aggressive OEMs |

## Morning session verifications (09:20–09:55)
Ravi keep-chats opt-in (banner→Identity→Keep) ✓ · rewarded ad loads+plays+completes (AdActivity, test unit) ✓ · N4 data-only ✓ · N5 airplane+BT ✓ · Chats away-section ("out of range", dim medallion) ✓ · **N8 RELAY E2E ✓ — msg with ZERO BT path delivered via Firestore sealed-box relay ("via the cloud then" 09:47)** · context banner states (BT off / signed-in variants) ✓ · Nokia onto Oppo's hotspot via UI automation ✓ · N9 attempt 1 invalidated (force-stop = stopped-state, FCM never delivers by platform rule — retest as swipe-kill pending)

## Verified so far (Phase 1–2, Nokia; Oppo partial)
O1 ⚠️(A10 slow start) · O2 ✅ both grant flows (A15: nearby+loc+notif+bg-loc+battery; A10: loc+all-time+battery) · O5 ✅ decline exits + gate persists · O6 ✅ both paths (Keep=Nokia, Skip=Oppo) · O7 ✅ full editor (limits, counter, chips, vibes 3/5, glyph swap) · O8 ✅ radar scanning state
U1–U3 partial ✅ (deck empty-state "That's everyone", radar+cards toggle, 1-in-range counter live) · U5 ✅ filters screen + live count + reset · U6 ✅ chats empty state · U7 ✅ room send/render + "NOTHING IS ARCHIVED" · U9 ✅ You card preview + key 4-group · U13 ✅ hub + Privacy/Notifications subs · U16 ✅ theme Dark/Light/Dark live switch all screens · U18 ✅ version 0.21.1 shown · U21 ❌→fix (issue 2)
Mesh pre-check: Nokia↔Oppo BLE link up ("1 IN RANGE" both Room+Discover) with Oppo app background — power fix holding.

## Phase 3/4 results (2026-08-21 03:45–05:50)
M1✅ M2✅ M3✅ (celebration both sides) M4✅+undo✅ M5✅(✓✓) M6✅ M7✅ M8✅ M9⏭(sheet verified, copy not exercised) M11⚠️(WAVE→WAVED persists; receiver badge only shows for stranger-state — untestable in matched pair) M12✅ both dirs M13✅ (shade reply + mesh survives) M14✅ (logcat-proof) M15✅ M16✅(new matched-only rule) M18✅ M19✅ M20/M21 pending-online M22⏭(needs physical camera aim) M23✅ (TestHook: peers/session/dm/msg/sniff used throughout)
N2 ✅✅ **BOTH PHONES ZERO-INTERNET: private chat ✓✓ delivered + room broadcast + radar connection — pure BLE** N6 ✅ (BT off → in-app explainer, no crash; enable → consent → mesh auto-recovered <60 s, no restart) N12 soak started 05:47. N1/N3/N4/N7–N11 pending (need Oppo adb back / Nokia wifi creds).
L1 ✅ implicitly ×5 (state survived every reinstall/restart: profile, matches, chats, settings, theme)
G1 mapped: manifest:93 app-id + AdConfig.kt:40-41 units (+ json remote-config keys ads_enabled/mode/rewarded_unit) — all still Google TEST ids.

## Evening session (19:15–19:45) — discovery re-validation + 5 fixes
Oppo updated to the all-fixes build (was still pre-#24 from the morning; `install -r`, data kept). Timed cold-start discovery test: mutual verified announces **within ~2 s of both radios up** (Nokia's ~14 s cold start is the only wait); "1 IN RANGE" + copper connection medallion both sides; zero BLE disconnects over 2½ min watch; foreground announce cadence 12 s. The earlier "0 peers but Ravi is here" report = Oppo app was force-stopped (leftover from morning N9 test), not a discovery fault → issue #30.
Issues #26–#29 found by user, fixed and verified on-device this session (build installed on both phones, uncommitted). Product decision reaffirmed: out-of-mesh path = OUR Firebase relay, NOT Nostr — favorite-notice copy pitching Nostr removed with #27; full Nostr/Tor teardown remains a deferred post-launch item (battery + size win).

## Nostr/Tor severed + favorites removed (evening, user decision)
Product call: the Firebase relay is THE out-of-mesh path; Nostr was an unreachable "backup" (needed mutual favorites), Tor bootstrap was a battery suspect (#18), both cut. **Severed, not deleted** (one-commit revert): BitchatApplication no longer starts ArtiTorManager / RelayDirectory / LocationNotes / NostrIdentity / NostrBackgroundRuntime; TorPreferenceManager pinned OFF (legacy saved "ON" ignored); MessageRouter = mesh-or-queue only (RouteResult.NOSTR gone); favorites star removed from chat header, sendFavoriteNotification deleted; libarti_android.so excluded from packaging (−5.4MB/ABI, file kept on disk). RELAY EFFICIENCY: #25 FIXED (dupe mailbox docs now deleted via AppStateStore.hasSeenMessage), recipient chatPubKey cached 10min (was 1 Firestore read/message), own-key republish once per process (was 1 write/message). VERIFIED on both phones: zero ArtiTor/NostrRelay log markers, mesh chat both directions, star gone, notification icon + unread badge still good. NOT retested tonight: relay E2E with BT off (Nokia offline; unchanged code path + dupe-delete is new but guarded).

## Launch-gate checklist (pre-existing, verify before store push)
- [ ] G1 Swap TEST AdMob app-id + units → real (MANDATORY before production)
- [ ] G2 versionCode bump for release AAB
- [ ] G3 firestore.rules deployed = repo version
- [ ] G4 Release-signed AAB smoke test (R8) — debug≠release behavior (minify!)
- [ ] G5 Play listing assets + Data Safety form consistency
