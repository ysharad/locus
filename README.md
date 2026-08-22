# Locus

**Discover, connect, and chat with people around you — with zero internet.**

Locus is a hyperlocal social discovery app for concerts, festivals, parties, campuses, and anywhere people gather. Discovery, matching and chatting run over a Bluetooth mesh between phones and work with the internet switched off — the network is the phones themselves.

Signing in is optional. Without it nothing leaves your device except the profile card you broadcast. Sign in (Google or a phone number) and your chats and connections are saved, you earn a verified badge, and messages can reach people who have left the room — sealed to their key and parked in a mailbox the server cannot read.

**It is not a dating app**: no gender filters, no romantic intent options, adults (18+) only.

Built on the [bitchat-android](https://github.com/permissionlesstech/bitchat-android) mesh engine (GPL-3.0).

## How it works

1. **Make your profile** — name (anonymous by default), glyph, a one-liner, your vibes, and what you're here for. It lives on your device and is broadcast to people in range.
2. **Discover** — your card broadcasts to everyone in radio range (and hops further through the mesh). Their cards land in your deck. Swipe right (⚡ Connect) or left (Pass).
3. **Connect** — interest signals are end-to-end encrypted (Noise protocol) and private. When two people both tap ⚡, both get "It's a connection!" — nobody else ever knows who liked whom.
4. **Chat** — connections chat over bitchat's encrypted private messaging. The **Room** tab is the open venue-wide channel everyone in range shares.

## Feature map

| Tab | What it does |
|---|---|
| Discover | Swipeable card deck of people in range, "wants to connect" tray, radar empty-state |
| Connections | Your mutual connections; one tap into an encrypted 1:1 chat |
| Room | The classic bitchat public mesh chat for the whole venue |
| You | Edit your card; changes broadcast instantly |

## Protocol notes

Locus control traffic rides ordinary bitchat transports with a `BCX1|` prefix and is swallowed before it reaches chat timelines:

- `BCX1|P|<json>` — profile card, public broadcast (fragmented + relayed by the mesh, ~300 bytes)
- `BCX1|L|` — connect signal, Noise-encrypted private message to one peer

Match detection is symmetric and local: each device records likes sent and received; the intersection is a connection. No coordinator needed.

## Build

```bash
JAVA_HOME=<jdk21> ./gradlew :app:assembleDebug
```

APKs land in `app/build/outputs/apk/debug/`. `applicationId` is `com.locus.app`.

## Roadmap

- [ ] Firebase backend phase: optional online presence, profile photos, push
- [ ] Play Store listing assets + release signing
- [ ] Verified-age gate & safety/reporting flows before public launch

## License

GPL-3.0 (inherited from bitchat-android). Distributing builds — including on the Play Store — requires making the corresponding source available.

## Licence and attribution

Locus is a fork of [bitchat-android](https://github.com/permissionlesstech/bitchat-android)
by permissionless.tech, distributed under the **GNU General Public License v3.0**
— see [LICENSE.md](LICENSE.md). The Bluetooth mesh transport, Noise session layer
and packet fragmentation come from that project.

What Locus adds (mostly under `app/src/main/java/com/bitchat/android/connect/`):
profile cards and the discovery deck, mutual-consent connections, the encrypted
Firebase relay, identity anchoring and verification, safety tooling (blocking on
stable keys, reporting), and a complete visual redesign.

`app/google-services.json`, `keystore.properties` and `local.properties` are not
in this repository — supply your own Firebase project and signing config. The
optional login and relay features switch themselves off when absent.

The name **Locus**, its logo and its store listing are trademarks and are not
covered by the GPL. Forks are welcome; please ship them under a different name.
