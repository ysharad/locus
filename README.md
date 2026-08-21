# Locus

**Discover, connect, and chat with people around you — with zero internet.**

Locus is a hyperlocal social discovery app for concerts, festivals, parties, campuses, and anywhere people gather. It runs entirely over a Bluetooth mesh network between phones: no accounts, no phone numbers, no servers, no internet.

Built on the [bitchat-android](https://github.com/permissionlesstech/bitchat-android) mesh engine (GPL-3.0).

## How it works

1. **Make your card** — name, emoji avatar, a one-liner, your vibes, and what you're here for. No login; your card lives only on your device.
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
