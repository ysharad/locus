package com.bitchat.android.connect

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide coordinator for the Locus layer: discover nearby people,
 * signal interest, and turn mutual interest into a connection — all over the
 * offline mesh, no server involved.
 *
 * Protocol (see [ConnectSignal]):
 *  - Our profile card is broadcast as a public mesh message on a slow cadence and
 *    whenever new peers appear, so everyone nearby can build a discovery deck.
 *  - A "like" is a Noise-encrypted private message to one peer. Each side keeps
 *    its own liked/received sets; the moment both sets intersect on either device,
 *    that device records the connection. No third party ever sees who liked whom.
 */
object ConnectManager {

    private const val TAG = "ConnectManager"
    private const val BROADCAST_INTERVAL_MS = 60_000L
    private const val MIN_BROADCAST_GAP_MS = 15_000L
    // Presence auto-expiry: a card drops off your deck a few minutes after its owner stops being
    // heard (i.e. leaves the room). Short by design — nobody should linger on radar after they go.
    private const val NEARBY_TTL_MS = 5 * 60_000L

    /** Peers with this prefix are locally seeded demo cards; they never touch the mesh or disk. */
    const val DEMO_PREFIX = "demo-"

    // Locus+ perks, unlocked by watching a rewarded video (never by paying). "See who likes you"
    // holds for a day; a Boost is momentary — it re-airs your card so nearby decks resort you to
    // the front, and the window is only how long we show the "boosted" chip.
    private const val KEY_REVEAL_UNTIL = "plus_reveal_until"
    private const val REVEAL_WINDOW_MS = 24 * 60 * 60_000L
    private const val BOOST_WINDOW_MS = 30 * 60_000L

    // Typing: at most one ping every few seconds while composing; the receiver shows "typing" and
    // clears it if no ping arrives within the TTL (so we never need a reliable "stopped" packet).
    private const val TYPING_THROTTLE_MS = 3_000L
    private const val TYPING_TTL_MS = 6_000L

    private var store: ConnectStore? = null
    private var appContext: Context? = null
    private var mesh: MeshService? = null
    private var loopJob: Job? = null
    // The live BLE/Noise-connected peers — ground truth for "who's actually here right now",
    // independent of whether their profile broadcast was recently heard. Presence leans on this so
    // a connected person never flickers off radar just because a broadcast got missed.
    private var connectedPeersFlow: StateFlow<List<String>>? = null
    private var scope: CoroutineScope? = null
    private val lock = Any()

    @Volatile private var lastBroadcastAt = 0L

    private val _myProfile = MutableStateFlow<ConnectProfile?>(null)
    val myProfile: StateFlow<ConnectProfile?> = _myProfile.asStateFlow()

    /** Profiles heard over the mesh this session, keyed by peerID. */
    private val _nearby = MutableStateFlow<Map<String, ConnectProfile>>(emptyMap())
    val nearby: StateFlow<Map<String, ConnectProfile>> = _nearby.asStateFlow()

    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    private val _passed = MutableStateFlow<Set<String>>(emptySet())
    val passed: StateFlow<Set<String>> = _passed.asStateFlow()

    /** Peers who signalled interest in us and are not yet a connection. */
    private val _likesReceived = MutableStateFlow<Set<String>>(emptySet())
    val likesReceived: StateFlow<Set<String>> = _likesReceived.asStateFlow()

    /** Waves — one-packet hellos. Session-scoped: who I waved at, and who waved at me. */
    private val _wavedAt = MutableStateFlow<Set<String>>(emptySet())
    val wavedAt: StateFlow<Set<String>> = _wavedAt.asStateFlow()
    private val _wavesReceived = MutableStateFlow<Set<String>>(emptySet())
    val wavesReceived: StateFlow<Set<String>> = _wavesReceived.asStateFlow()

    /**
     * Message reactions, in-memory for the session: messageId → (reactor's stable fingerprint → emoji).
     * Both ends key a reactor by the same Noise fingerprint, so a person's reaction de-dupes across
     * devices. Reactions travel over the mesh like a wave — an in-range, in-the-moment signal.
     */
    private val _reactions = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val reactions: StateFlow<Map<String, Map<String, String>>> = _reactions.asStateFlow()

    /** Locus+ : timestamp until which "see who likes you" is unlocked (persisted across restarts). */
    private val _revealUntil = MutableStateFlow(0L)
    val revealUntil: StateFlow<Long> = _revealUntil.asStateFlow()

    /** Locus+ : timestamp until which the "boosted" chip shows after a Boost (in-memory, momentary). */
    private val _boostUntil = MutableStateFlow(0L)
    val boostUntil: StateFlow<Long> = _boostUntil.asStateFlow()

    /** Peers currently typing to me (their live mesh peerID). Auto-expires on a short timer. */
    private val _typingFrom = MutableStateFlow<Set<String>>(emptySet())
    val typingFrom: StateFlow<Set<String>> = _typingFrom.asStateFlow()
    private val typingToken = HashMap<String, Int>()
    private val lastTypingSentAt = HashMap<String, Long>() // per-peer, so switching chats isn't throttled together

    // Waves and reactions placed without a live Noise session: queued here and flushed on the same
    // triggers as pending likes (peer-appear + the broadcast tick), so an in-the-moment signal isn't
    // silently dropped just because the handshake wasn't up yet.
    private val pendingWaves = mutableSetOf<String>()
    private val pendingReactions = HashMap<Pair<String, String>, String>() // (peerID, msgId) -> emoji

    private val _matches = MutableStateFlow<Map<String, ConnectMatch>>(emptyMap())
    val matches: StateFlow<Map<String, ConnectMatch>> = _matches.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    // Peers whose repeat-like we've already answered this session (match re-affirmation guard),
    // and those still owed an answer once a Noise session is up.
    private val reaffirmedLikes = mutableSetOf<String>()
    private val pendingReaffirms = mutableSetOf<String>()

    private fun retryPendingReaffirms() {
        val m = mesh ?: return
        pendingReaffirms.toList().forEach { pid ->
            if (pid in reaffirmedLikes) { pendingReaffirms -= pid; return@forEach }
            if (m.hasEstablishedSession(pid)) {
                reaffirmedLikes += pid
                pendingReaffirms -= pid
                sendLike(pid)
            } else {
                try { m.initiateNoiseHandshake(pid) } catch (_: Exception) {}
            }
        }
    }

    private val _ageConfirmed = MutableStateFlow(false)
    val ageConfirmed: StateFlow<Boolean> = _ageConfirmed.asStateFlow()

    private val _identitySeen = MutableStateFlow(false)
    val identitySeen: StateFlow<Boolean> = _identitySeen.asStateFlow()

    /** Opt-in: back up + relay chats through the encrypted backend when online. */
    private val _keepChats = MutableStateFlow(false)
    val keepChats: StateFlow<Boolean> = _keepChats.asStateFlow()

    /** Set from deep in the tree (e.g. the in-chat connectivity banner) to ask the root to open the
     *  sign-in / keep-chats screen; the root consumes it. */
    private val _signInRequested = MutableStateFlow(false)
    val signInRequested: StateFlow<Boolean> = _signInRequested.asStateFlow()
    fun requestSignIn() { _signInRequested.value = true }
    fun consumeSignInRequest() { _signInRequested.value = false }

    /** Presence toggle. When invisible, we stop broadcasting the card and stop stamping last-seen. */
    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _notifyNearby = MutableStateFlow(true)
    val notifyNearby: StateFlow<Boolean> = _notifyNearby.asStateFlow()
    private val _notifyMessages = MutableStateFlow(true)
    val notifyMessages: StateFlow<Boolean> = _notifyMessages.asStateFlow()

    /** Client-side discovery filters. Everyone in range still receives your card; this only
     *  narrows whose cards land in YOUR deck. */
    data class DiscoverFilters(
        val intents: Set<String> = emptySet(),
        val vibes: Set<String> = emptySet(),
        val ageMin: Int = 18,
        val ageMax: Int = 99
    ) {
        val activeCount: Int get() = (if (intents.isEmpty()) 0 else 1) +
            (if (vibes.isEmpty()) 0 else 1) + (if (ageMin > 18 || ageMax < 99) 1 else 0)
    }
    private val _filters = MutableStateFlow(DiscoverFilters())
    val filters: StateFlow<DiscoverFilters> = _filters.asStateFlow()

    fun init(context: Context) {
        if (store != null) return
        appContext = context.applicationContext
        CloudSync.init(context.applicationContext)
        ConnectNotifier.init(context.applicationContext)
        val s = ConnectStore(context.applicationContext)
        store = s
        _myProfile.value = s.loadMyProfile()
        _liked.value = s.likedPeers()
        _passed.value = s.passedPeers()
        _likesReceived.value = s.likesReceived()
        _matches.value = s.loadMatches()
        _blocked.value = s.blockedPeers()
        _ageConfirmed.value = s.isAgeConfirmed()
        _identitySeen.value = s.isIdentitySeen()
        _keepChats.value = s.isKeepChats()
        _visible.value = s.isVisible()
        _notifyNearby.value = s.isNotifyNearby()
        _notifyMessages.value = s.isNotifyMessages()
        _filters.value = DiscoverFilters(s.filterIntents(), s.filterVibes(), s.filterAgeMin(), s.filterAgeMax())
        _revealUntil.value = s.long(KEY_REVEAL_UNTIL, 0L)
    }

    fun setFilters(f: DiscoverFilters) {
        store?.saveFilters(f.intents, f.vibes, f.ageMin, f.ageMax)
        _filters.value = f
    }

    /** Whether a nearby profile survives the current filters (demo cards always pass). */
    fun passesFilters(p: ConnectProfile): Boolean {
        if (p.peerID.startsWith(DEMO_PREFIX)) return true
        val f = _filters.value
        if (f.intents.isNotEmpty() && p.hereTo !in f.intents) return false
        if (f.vibes.isNotEmpty() && p.vibes.none { it in f.vibes }) return false
        val age = p.age
        if (age != null && (age < f.ageMin || age > f.ageMax)) return false
        return true
    }

    fun setVisible(on: Boolean) {
        store?.setVisible(on)
        _visible.value = on
        if (on) broadcastProfile(force = true) // reappear immediately
    }

    fun setNotifyNearby(on: Boolean) { store?.setNotifyNearby(on); _notifyNearby.value = on }
    fun setNotifyMessages(on: Boolean) { store?.setNotifyMessages(on); _notifyMessages.value = on }

    /** Remove a block (by the stable fingerprint or peerID stored). */
    fun unblock(key: String) = synchronized(lock) {
        // One person is stored under BOTH keys (fingerprint + peerID, where peerID == fp.take(16)).
        // Unblocking must clear every key with the same 16-hex stem or they stay half-blocked.
        val stem = key.take(16)
        val companions = _blocked.value.filter { it.take(16) == stem } + key
        companions.forEach { store?.removeBlocked(it) }
        _blocked.value = _blocked.value - companions.toSet()
    }

    /** Erase all Locus data on this device and, best-effort, remove the cloud profile. */
    fun wipeLocalData() = synchronized(lock) {
        ChatRelay.stopListening()
        val fp = mesh?.let { try { it.getIdentityFingerprint() } catch (e: Exception) { null } }
        CloudSync.deleteProfile(fp)
        store?.wipe()
        _myProfile.value = null
        _liked.value = emptySet()
        _passed.value = emptySet()
        _likesReceived.value = emptySet()
        _matches.value = emptyMap()
        _blocked.value = emptySet()
        _nearby.value = emptyMap()
        _keepChats.value = false
        _visible.value = true
        _ageConfirmed.value = false
        _identitySeen.value = false
        // Also clear the in-memory session state a wipe previously left behind.
        _reactions.value = emptyMap()
        _typingFrom.value = emptySet()
        _wavedAt.value = emptySet()
        _wavesReceived.value = emptySet()
        _revealUntil.value = 0L
        _boostUntil.value = 0L
        pendingWaves.clear()
        pendingReactions.clear()
        typingToken.clear()
        lastTypingSentAt.clear()
    }

    fun confirmAge() {
        store?.setAgeConfirmed()
        _ageConfirmed.value = true
    }

    fun markIdentitySeen() {
        store?.setIdentitySeen()
        _identitySeen.value = true
    }

    fun setKeepChats(on: Boolean) {
        store?.setKeepChats(on)
        _keepChats.value = on
        if (on) { markIdentitySeen(); startRelay() } else ChatRelay.stopListening()
    }

    /** Begin listening on our encrypted mailbox (opt-in online chat). Safe to call repeatedly. */
    private fun startRelay() {
        val ctx = appContext ?: return
        val m = mesh ?: return
        val sc = scope ?: return
        val fp = try { m.getIdentityFingerprint() } catch (e: Exception) { return }
        store?.setMyFingerprint(fp)
        // Ensure the owner profile doc (uid + name) exists before publishing keys/token and before
        // anyone relays: the mailbox rules bind a sender to the profile they own, and key/token
        // writes are otherwise denied as creates on a missing doc.
        _myProfile.value?.let { CloudSync.syncProfile(it) }
        ChatRelay.startListening(ctx, fp, _myProfile.value?.name ?: "You", sc)
        ChatRelay.publishFcmToken(fp)
    }

    /**
     * Also push an outgoing private message through the encrypted relay when the user has opted in.
     * Delivery dedupes on message id, so double-delivery (mesh + relay) is harmless; this is what
     * lets a conversation continue when the peer is out of Bluetooth range.
     */
    fun relayOutgoing(conversationId: String, content: String, msgId: String, senderName: String?) {
        if (!_keepChats.value) return
        val ctx = appContext ?: return
        val m = mesh ?: return
        val myFp = try { m.getIdentityFingerprint() } catch (e: Exception) { return }
        val recipientFp = if (conversationId.startsWith("contact_")) conversationId.removePrefix("contact_")
        else (m.getPeerFingerprint(conversationId) ?: return)
        if (recipientFp.isBlank() || recipientFp == myFp) return
        val name = senderName ?: _myProfile.value?.name ?: "Someone"
        val msg = BitchatMessage(
            id = msgId,
            sender = name,
            content = content,
            timestamp = java.util.Date(),
            isPrivate = true,
            senderPeerID = myFp.take(16)
        )
        ChatRelay.send(ctx, myFp, name, recipientFp, msg)
    }

    /**
     * Wire up the transport. Called from the UI layer once the mesh is running;
     * safe to call repeatedly (e.g. on every recomposition of the root screen).
     */
    fun attach(
        meshService: MeshService,
        scope: CoroutineScope,
        connectedPeers: StateFlow<List<String>>
    ) {
        mesh = meshService
        this.scope = scope
        connectedPeersFlow = connectedPeers
        try { CloudSync.bind(meshService.getIdentityFingerprint()) } catch (_: Exception) { }
        _myProfile.value?.let { CloudSync.syncProfile(it) }
        if (_keepChats.value) startRelay()
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            launch {
                var known = emptySet<String>()
                connectedPeers.collect { peers ->
                    val fresh = peers.toSet() - known
                    known = known + peers
                    if (fresh.isNotEmpty()) {
                        // Someone new just came into range — put our card on the air immediately
                        // (bypass the min-gap) so they see us right away instead of on the next tick.
                        lastBroadcastAt = 0L
                        broadcastProfile(force = true)
                        retryPendingLikes()
                        retryPendingSignals()
                        retryPendingReaffirms()
                    }
                }
            }
            while (true) {
                delay(BROADCAST_INTERVAL_MS)
                pruneNearby() // always expire stale presence, even once the room has emptied out
                if (connectedPeers.value.isNotEmpty()) {
                    broadcastProfile(force = true)
                    retryPendingLikes()
                    retryPendingSignals()
                    retryPendingReaffirms()
                    if (_visible.value) CloudSync.heartbeat()
                }
            }
        }
    }

    // MARK: - Incoming (called from transport worker threads)

    /**
     * Returns true when [message] is Locus control traffic and has been
     * consumed — the caller must then keep it out of chat state entirely.
     */
    fun handleIncoming(message: BitchatMessage): Boolean {
        val content = message.content
        if (!ConnectSignal.isConnectContent(content)) return false
        val peerID = message.senderPeerID?.takeIf { it.isNotBlank() } ?: return true
        val self = mesh?.myPeerID
        if (peerID == self) return true
        // Block check keys on the STABLE fingerprint (survives peerID rotation), with the raw
        // peerID as a fallback. (It cannot survive the other party reinstalling — that mints a new
        // key by design; identity here is device-bound, not an account.)
        val fp = mesh?.getPeerFingerprint(peerID)
        if (peerID in _blocked.value || (fp != null && fp in _blocked.value)) return true
        try {
            val now = System.currentTimeMillis()
            ConnectSignal.decodeProfile(content, peerID, now)?.let { profile ->
                onProfileReceived(profile)
                return true
            }
            if (ConnectSignal.isLike(content) && message.isPrivate) {
                onLikeReceived(peerID)
            }
            if (ConnectSignal.isWave(content) && message.isPrivate) {
                synchronized(lock) { _wavesReceived.value = _wavesReceived.value + peerID }
            }
            if (ConnectSignal.isReaction(content) && message.isPrivate) {
                ConnectSignal.parseReaction(content)?.let { (msgId, emoji) ->
                    // Key the reactor by their stable fingerprint so it survives peerID rotation.
                    putReaction(msgId, fp ?: peerID, emoji)
                }
            }
            if (ConnectSignal.isTyping(content) && message.isPrivate) {
                onTypingReceived(peerID)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle connect signal: ${e.message}")
        }
        return true
    }

    private fun onProfileReceived(profile: ConnectProfile) = synchronized(lock) {
        val firstSighting = !_nearby.value.containsKey(profile.peerID)
        _nearby.value = _nearby.value + (profile.peerID to profile)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (firstSighting && _notifyNearby.value && (store?.notificationsAllowedNow(hour) != false)) {
            ConnectNotifier.personDetected(
                profile,
                isConnection = _matches.value.containsKey(profile.peerID)
            )
        }
        // Keep connection snapshots fresh as people edit their cards
        _matches.value[profile.peerID]?.let { existing ->
            val updated = existing.copy(profile = profile)
            _matches.value = _matches.value + (profile.peerID to updated)
            // Strip demo keys like every other save site, so an in-memory demo match never persists.
            store?.saveMatches(_matches.value.filterKeys { !it.startsWith(DEMO_PREFIX) })
        }
    }

    private fun onLikeReceived(peerID: String) = synchronized(lock) {
        val s = store ?: return
        if (_matches.value.containsKey(peerID)) {
            // A like from someone we already hold a match with means THEIR side lost the match
            // (block→unblock, reinstall). Re-affirm so both sides converge on matched — queued,
            // because the Noise session may not be up at this instant, and the normal like-retry
            // never fires for matched peers. Once-per-session guard breaks any ping-pong.
            if (peerID !in reaffirmedLikes) pendingReaffirms += peerID
            retryPendingReaffirms()
            return
        }
        if (!peerID.startsWith(DEMO_PREFIX)) s.addLikeReceived(peerID)
        _likesReceived.value = _likesReceived.value + peerID
        if (peerID in _liked.value) {
            registerMatch(peerID)
        }
    }

    // MARK: - Actions from UI

    fun saveProfile(profile: ConnectProfile) {
        val s = store ?: return
        val stamped = profile.copy(updatedAt = System.currentTimeMillis())
        s.saveMyProfile(stamped)
        _myProfile.value = stamped
        broadcastProfile(force = true)
        CloudSync.syncProfile(stamped)
    }

    fun like(peerID: String) = synchronized(lock) {
        val s = store ?: return
        if (!peerID.startsWith(DEMO_PREFIX)) s.addLiked(peerID)
        _liked.value = _liked.value + peerID
        if (peerID.startsWith(DEMO_PREFIX)) {
            // Demo peers reciprocate after a beat so the full match flow can be tried solo
            scope?.launch {
                delay(1_800)
                onLikeReceived(peerID)
            }
            return
        }
        sendLike(peerID)
        if (peerID in _likesReceived.value) {
            registerMatch(peerID)
        }
    }

    /** My stable identity fingerprint (64-hex), for the QR handshake. */
    fun myFingerprint(): String? =
        (mesh?.let { try { it.getIdentityFingerprint() } catch (e: Exception) { null } }) ?: store?.myFingerprint()

    /**
     * Connect in person from a scanned card — an immediate mutual connection, no swiping. Keyed on
     * the stable fingerprint so the chat lands in the same thread as any later mesh conversation.
     */
    fun connectViaQr(profile: ConnectProfile) = synchronized(lock) {
        val s = store ?: return
        // Ignore a scan of your own code (both keyed by the 16-hex peerID now).
        if (profile.peerID == mesh?.myPeerID || profile.peerID.isBlank()) return
        if (_matches.value.containsKey(profile.peerID)) return
        val match = ConnectMatch(profile.peerID, profile, System.currentTimeMillis())
        _matches.value = _matches.value + (profile.peerID to match)
        s.saveMatches(_matches.value.filterKeys { !it.startsWith(DEMO_PREFIX) })
        // Best-effort: tell them over the mesh so it's mutual if they're in range.
        try {
            if (mesh?.hasEstablishedSession(profile.peerID) == true) {
                mesh?.sendPrivateMessage(ConnectSignal.encodeLike(), profile.peerID, profile.name)
            }
        } catch (_: Exception) { }
    }

    /** Send a wave — one packet, no message, no obligation. */
    fun sendWave(peerID: String) {
        synchronized(lock) {
            if (peerID in _wavedAt.value) return
            _wavedAt.value = _wavedAt.value + peerID
        }
        val m = mesh ?: return
        try {
            if (m.hasEstablishedSession(peerID)) {
                m.sendPrivateMessage(ConnectSignal.encodeWave(), peerID, nicknameFor(peerID) ?: peerID)
                synchronized(lock) { pendingWaves.remove(peerID) }
            } else {
                // No session yet: queue the wave and kick a handshake; retryPendingSignals delivers it.
                synchronized(lock) { pendingWaves.add(peerID) }
                m.initiateNoiseHandshake(peerID)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wave failed for $peerID: ${e.message}")
        }
    }

    /**
     * React to a message in a 1:1 chat. Tapping the same emoji again clears it. The reaction is
     * recorded locally at once (optimistic) and sent to [peerID] over the mesh; if there's no live
     * session it kicks a handshake and the tap can be repeated once connected.
     */
    fun sendReaction(peerID: String, messageId: String, emoji: String) {
        val me = myFingerprint() ?: "me"
        val current = _reactions.value[messageId]?.get(me)
        val next = if (current == emoji) "" else emoji // toggle off when re-tapping the same one
        putReaction(messageId, me, next)
        val m = mesh ?: return
        try {
            if (m.hasEstablishedSession(peerID)) {
                m.sendPrivateMessage(ConnectSignal.encodeReaction(messageId, next), peerID, nicknameFor(peerID) ?: peerID)
                synchronized(lock) { pendingReactions.remove(peerID to messageId) }
            } else {
                // No session yet: queue the latest emoji for this message and hand off to the retry.
                synchronized(lock) { pendingReactions[peerID to messageId] = next }
                m.initiateNoiseHandshake(peerID)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reaction failed for $peerID: ${e.message}")
        }
    }

    private fun putReaction(messageId: String, reactor: String, emoji: String) = synchronized(lock) {
        val forMsg = (_reactions.value[messageId] ?: emptyMap()).toMutableMap()
        if (emoji.isBlank()) forMsg.remove(reactor) else forMsg[reactor] = emoji
        _reactions.value = if (forMsg.isEmpty()) _reactions.value - messageId
        else _reactions.value + (messageId to forMsg)
    }

    // MARK: - Locus+ (rewarded-ad perks)

    /** True while "see who likes you" is unlocked. */
    fun revealActive(): Boolean = System.currentTimeMillis() < _revealUntil.value

    /** Grant the reveal window after a rewarded video completes. Persisted so it survives restarts. */
    fun grantRevealLikes() {
        val until = System.currentTimeMillis() + REVEAL_WINDOW_MS
        _revealUntil.value = until
        store?.setLong(KEY_REVEAL_UNTIL, until)
    }

    /** True while the "boosted" chip should show. */
    fun boostActive(): Boolean = System.currentTimeMillis() < _boostUntil.value

    /**
     * Boost: re-stamp the card with a fresh time and put it back on the air. Nearby decks sort by
     * the card's updatedAt, so this genuinely resorts you to the front for everyone in range. Needs
     * visibility to have any effect — an invisible card is never broadcast. Returns false if it
     * couldn't air (invisible or connections-only), so the caller can nudge the user.
     */
    fun boostNow(): Boolean {
        val p = _myProfile.value ?: return false
        if (!_visible.value) return false
        if (store?.bool(ConnectStore.KEY_CONNECTIONS_ONLY, false) == true) return false
        val stamped = p.copy(updatedAt = System.currentTimeMillis())
        _myProfile.value = stamped
        store?.saveMyProfile(stamped)
        lastBroadcastAt = 0L // bypass the min-gap so the boost airs immediately
        broadcastProfile(force = true)
        _boostUntil.value = System.currentTimeMillis() + BOOST_WINDOW_MS
        return true
    }

    // MARK: - Typing indicator

    /**
     * Tell [peerID] I'm typing. Throttled so at most one ping goes out every few seconds, gated on
     * the user's typing-indicator setting, and only sent when there's a live session (no handshake
     * kicked just to announce typing).
     */
    fun notifyTyping(peerID: String) {
        if (store?.bool(ConnectStore.KEY_TYPING, true) == false) return
        val now = System.currentTimeMillis()
        if (now - (lastTypingSentAt[peerID] ?: 0L) < TYPING_THROTTLE_MS) return
        val m = mesh ?: return
        if (!m.hasEstablishedSession(peerID)) return
        lastTypingSentAt[peerID] = now
        try {
            m.sendPrivateMessage(ConnectSignal.encodeTyping(), peerID, nicknameFor(peerID) ?: peerID)
        } catch (e: Exception) {
            Log.w(TAG, "Typing ping failed for $peerID: ${e.message}")
        }
    }

    private fun onTypingReceived(peerID: String) {
        val token = synchronized(lock) {
            _typingFrom.value = _typingFrom.value + peerID
            val next = (typingToken[peerID] ?: 0) + 1
            typingToken[peerID] = next
            next
        }
        scope?.launch {
            delay(TYPING_TTL_MS)
            // Only clear if no fresher ping arrived in the meantime.
            synchronized(lock) {
                if (typingToken[peerID] == token) _typingFrom.value = _typingFrom.value - peerID
            }
        }
    }

    // MARK: - Settings gates consulted by the inherited mesh/notification paths, so the Locus
    // privacy + notification toggles actually govern read receipts, last-seen, and every
    // notification — not just the Locus-specific code paths.

    private fun currentHour() = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    /** Read receipts default on; off means the inherited mesh path must not send them. */
    fun readReceiptsEnabled(): Boolean = store?.bool(ConnectStore.KEY_READ_RECEIPTS, true) ?: true

    /** Last-seen default on; off means we stop refreshing the cloud timestamp. */
    fun lastSeenEnabled(): Boolean = store?.bool(ConnectStore.KEY_LAST_SEEN, true) ?: true

    /** Private-message notifications: master switch + quiet hours + the "New message" toggle. */
    fun messageNotificationsAllowed(): Boolean {
        val s = store ?: return true
        return s.isNotifyMessages() && s.notificationsAllowedNow(currentHour())
    }

    /** Mention notifications: master switch + quiet hours + the "Mentioned in the Room" toggle. */
    fun mentionNotificationsAllowed(): Boolean {
        val s = store ?: return true
        return s.bool(ConnectStore.KEY_NOTIFY_MENTIONED, true) && s.notificationsAllowedNow(currentHour())
    }

    /** General public-channel activity: master switch + quiet hours. */
    fun roomActivityAllowed(): Boolean = store?.notificationsAllowedNow(currentHour()) ?: true

    fun pass(peerID: String) = synchronized(lock) {
        val s = store ?: return
        if (!peerID.startsWith(DEMO_PREFIX)) s.addPassed(peerID)
        _passed.value = _passed.value + peerID
        if (peerID in _likesReceived.value) {
            s.removeLikeReceived(peerID)
            _likesReceived.value = _likesReceived.value - peerID
        }
    }

    /**
     * Block a person everywhere: removed from the deck, likes, and connections,
     * all future mesh traffic from them is dropped, and (optionally) a report is
     * filed to the backend for review.
     */
    /**
     * True when the sender is on the blocklist — keyed on the stable fingerprint when the mesh can
     * resolve one, with the raw peerID as fallback. Used by message admission so a blocked person
     * is silent EVERYWHERE (timeline, room, unread, notifications), not just their Connect signals.
     */
    fun isBlockedSender(peerID: String?): Boolean {
        if (peerID.isNullOrBlank()) return false
        if (peerID in _blocked.value) return true
        val fp = try { mesh?.getPeerFingerprint(peerID) } catch (_: Exception) { null }
        return fp != null && fp in _blocked.value
    }

    fun blockAndReport(peerID: String, report: Boolean) = synchronized(lock) {
        val s = store ?: return
        val profile = _nearby.value[peerID] ?: _matches.value[peerID]?.profile
        // Persist the stable fingerprint (survives peerID rotation) AND the raw peerID as a fallback.
        val fp = mesh?.getPeerFingerprint(peerID)
        val keys = setOfNotNull(peerID, fp)
        if (!peerID.startsWith(DEMO_PREFIX)) {
            keys.forEach { s.addBlocked(it) }
            if (report) CloudSync.report(peerID, profile)
        }
        _blocked.value = _blocked.value + keys
        _nearby.value = _nearby.value - peerID
        if (peerID in _likesReceived.value) {
            s.removeLikeReceived(peerID)
            _likesReceived.value = _likesReceived.value - peerID
        }
        if (_matches.value.containsKey(peerID)) {
            _matches.value = _matches.value - peerID
            s.saveMatches(_matches.value.filterKeys { !it.startsWith(DEMO_PREFIX) })
        }
        // Blocking wipes your swipe history for this person. Without this, an unblock leaves them
        // in `liked`/`passed` forever — hidden from the deck and radar with no way back in.
        if (peerID in _liked.value) {
            s.removeLiked(peerID)
            _liked.value = _liked.value - peerID
        }
        if (peerID in _passed.value) {
            s.removePassed(peerID)
            _passed.value = _passed.value - peerID
        }
    }

    /** Give passed profiles another chance (rewind-all, offline style). */
    fun resetPasses() = synchronized(lock) {
        val s = store ?: return
        _passed.value.forEach { s.removePassed(it) }
        _passed.value = emptySet()
    }

    // MARK: - Internals

    private fun registerMatch(peerID: String) {
        val s = store ?: return
        val profile = _nearby.value[peerID]
            ?: ConnectProfile(peerID = peerID, name = nicknameFor(peerID) ?: "Someone nearby")
        val match = ConnectMatch(peerID, profile, System.currentTimeMillis())
        _matches.value = _matches.value + (peerID to match)
        // Demo connections stay in-memory only
        s.saveMatches(_matches.value.filterKeys { !it.startsWith(DEMO_PREFIX) })
        s.removeLikeReceived(peerID)
        _likesReceived.value = _likesReceived.value - peerID
    }

    /** Seed a few local demo cards so the deck/match flow can be tried without other devices. */
    fun seedDemoProfiles() = synchronized(lock) {
        val now = System.currentTimeMillis()
        val demos = listOf(
            ConnectProfile("${DEMO_PREFIX}nova", "Nova", 25, "🝊", "resident of the front row", listOf("music", "dancing", "night owl"), "see where the night goes", now),
            ConnectProfile("${DEMO_PREFIX}kai", "Kai", 27, "◈", "will trade setlist predictions for snacks", listOf("music", "foodie", "deep talks"), "find my crew", now),
            ConnectProfile("${DEMO_PREFIX}juno", "Juno", 23, "❍", "first time here, adopt me", listOf("artsy", "chill", "traveler"), "meet new people", now),
            ConnectProfile("${DEMO_PREFIX}rex", "Rex", 29, "☾", "shortest guy at the venue, easy to find", listOf("gamer", "sporty", "festival head"), "just vibing", now)
        )
        _nearby.value = _nearby.value + demos.associateBy { it.peerID }
        // Demo seeds go through the notifier too, so backgrounding the app right
        // after seeding demonstrates the grouped "people nearby" notification.
        demos.forEach { ConnectNotifier.personDetected(it, isConnection = false) }
    }

    private fun broadcastProfile(force: Boolean) {
        if (!_visible.value) return // invisible: don't put the card on the air
        // "Connections only" also stops broadcasting — new people never receive your card.
        if (store?.bool(ConnectStore.KEY_CONNECTIONS_ONLY, false) == true) return
        val m = mesh ?: return
        val profile = _myProfile.value ?: return
        val now = System.currentTimeMillis()
        val gap = if (force) MIN_BROADCAST_GAP_MS else MIN_BROADCAST_GAP_MS * 2
        if (now - lastBroadcastAt < gap) return
        lastBroadcastAt = now
        try {
            m.sendMessage(ConnectSignal.encodeProfile(profile))
        } catch (e: Exception) {
            Log.w(TAG, "Profile broadcast failed: ${e.message}")
        }
    }

    private fun sendLike(peerID: String) {
        val m = mesh ?: return
        val s = store ?: return
        try {
            if (m.hasEstablishedSession(peerID)) {
                m.sendPrivateMessage(ConnectSignal.encodeLike(), peerID, nicknameFor(peerID) ?: peerID)
                s.addLikeDelivered(peerID)
            } else {
                // Queue behind a Noise handshake; retried on peer events and the broadcast tick
                m.initiateNoiseHandshake(peerID)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Like send failed for $peerID: ${e.message}")
        }
    }

    private fun retryPendingLikes() {
        val s = store ?: return
        val pending = _liked.value - s.deliveredLikes() - _matches.value.keys
        pending.forEach { sendLike(it) }
    }

    /** Flush waves/reactions that were queued while their peer had no live session. */
    private fun retryPendingSignals() {
        val m = mesh ?: return
        val waves = synchronized(lock) { pendingWaves.toList() }
        waves.forEach { peerID ->
            if (m.hasEstablishedSession(peerID)) {
                try {
                    m.sendPrivateMessage(ConnectSignal.encodeWave(), peerID, nicknameFor(peerID) ?: peerID)
                    synchronized(lock) { pendingWaves.remove(peerID) }
                } catch (e: Exception) {
                    Log.w(TAG, "Pending wave retry failed for $peerID: ${e.message}")
                }
            }
        }
        val reactions = synchronized(lock) { pendingReactions.toMap() }
        reactions.forEach { (key, emoji) ->
            val (peerID, msgId) = key
            if (m.hasEstablishedSession(peerID)) {
                try {
                    m.sendPrivateMessage(ConnectSignal.encodeReaction(msgId, emoji), peerID, nicknameFor(peerID) ?: peerID)
                    synchronized(lock) { pendingReactions.remove(key) }
                } catch (e: Exception) {
                    Log.w(TAG, "Pending reaction retry failed for $peerID: ${e.message}")
                }
            }
        }
    }

    private fun pruneNearby() {
        val cutoff = System.currentTimeMillis() - NEARBY_TTL_MS
        // A live BLE/Noise connection is definitive presence — but only for people you've MATCHED
        // with. A stranger who goes invisible must age off your radar even while the radios still
        // hold a link, or the visibility toggle silently lies to them. Matched connections stay
        // ("go quiet without leaving"); everyone else expires on the heard-timer.
        val connected = connectedPeersFlow?.value?.toSet() ?: emptySet()
        val kept = _nearby.value.filterValues {
            it.updatedAt >= cutoff || (it.peerID in connected && _matches.value.containsKey(it.peerID))
        }
        if (kept.size != _nearby.value.size) _nearby.value = kept
    }

    private fun nicknameFor(peerID: String): String? =
        _nearby.value[peerID]?.name ?: mesh?.getPeerNicknames()?.get(peerID)
}
