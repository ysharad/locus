package com.bitchat.android.connect.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitchat.android.R
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.ui.ChatScreen
import com.bitchat.android.ui.ChatViewModel

private enum class ConnectTab(val label: String, val iconRes: Int) {
    DISCOVER("Discover", R.drawable.ic_spec_range),
    CONNECTIONS("Chats", R.drawable.ic_spec_star),
    CHAT("Room", R.drawable.ic_spec_chat_bubbles),
    YOU("You", R.drawable.ic_spec_person)
}

/**
 * Locus root: first-run card creation, then the four-tab shell.
 * The classic mesh chat lives on as the "Room" tab — the venue-wide open channel.
 */
@Composable
fun ConnectRoot(viewModel: ChatViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ConnectManager.init(context)
        ConnectManager.attach(
            meshService = viewModel.meshServiceFacade,
            scope = viewModel.viewModelScope,
            connectedPeers = viewModel.connectedPeers
        )
    }

    val myProfile by ConnectManager.myProfile.collectAsState()
    val ageConfirmed by ConnectManager.ageConfirmed.collectAsState()
    val identitySeen by ConnectManager.identitySeen.collectAsState()

    // The 18+ attestation is one line on the login screen now, not a screen of its own —
    // passing that screen (by any path) records it. Same legal standing, one less gate.
    if (!ageConfirmed || !identitySeen) {
        IdentityScreen(onDone = { ConnectManager.confirmAge() })
        return
    }
    if (myProfile == null) {
        ProfileEditorScreen(viewModel = viewModel, isFirstRun = true)
        return
    }

    var tab by rememberSaveable { mutableStateOf(ConnectTab.DISCOVER.name) }
    var showInvite by rememberSaveable { mutableStateOf(false) }
    var editingCard by rememberSaveable { mutableStateOf(false) }
    var showIdentity by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var showTonight by rememberSaveable { mutableStateOf(false) }
    var showPlus by rememberSaveable { mutableStateOf(false) }
    var showWhoLiked by rememberSaveable { mutableStateOf(false) }
    val currentTab = ConnectTab.valueOf(tab)

    val matches by ConnectManager.matches.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val unreadChats by viewModel.unreadPrivateMessages.collectAsState()

    // Celebrate a brand-new mutual connection wherever the user happens to be
    val seenMatches = remember { mutableStateOf<Set<String>?>(null) }
    var celebratePeerID by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(matches.keys) {
        val previous = seenMatches.value
        if (previous != null) {
            (matches.keys - previous).firstOrNull()?.let { celebratePeerID = it }
        }
        seenMatches.value = matches.keys
    }

    val openChat: (String) -> Unit = { peerID ->
        tab = ConnectTab.CHAT.name
        viewModel.showPrivateChatSheet(peerID)
    }

    // React to the active 1:1 peer changing so navigation is deterministic:
    //  • a chat opening (an in-app tap OR a notification tap that lands in MainActivity and only
    //    sets the peer) always surfaces the chat, so tapping a message notification opens the chat
    //    instead of dumping you on the home tab;
    //  • closing a chat returns to the Chats list, not wherever we happened to be.
    // The in-chat connectivity banner can ask us to open the sign-in / keep-chats screen.
    val signInRequested by ConnectManager.signInRequested.collectAsState()
    LaunchedEffect(signInRequested) {
        if (signInRequested) {
            // The 1:1 chat sheet floats above every overlay — close it first or the
            // sign-in screen opens invisibly underneath and the button looks dead.
            viewModel.hidePrivateChatSheet()
            showIdentity = true
            ConnectManager.consumeSignInRequest()
        }
    }

    val privateChatPeer by viewModel.privateChatSheetPeer.collectAsState()
    var lastChatPeer by remember { mutableStateOf<String?>(null) }
    val keepChats by ConnectManager.keepChats.collectAsState()
    val connectStore = remember { com.bitchat.android.connect.ConnectStore(context.applicationContext) }
    val activity = context as? android.app.Activity
    LaunchedEffect(privateChatPeer) {
        if (privateChatPeer != null) {
            if (tab != ConnectTab.CHAT.name) tab = ConnectTab.CHAT.name
        } else if (lastChatPeer != null) {
            tab = ConnectTab.CONNECTIONS.name
            // Closing a chat is the app's natural "conversation over" beat — the only good
            // place to speak up without talking over the conversation itself.
            val closed = lastChatPeer!!
            val chats = viewModel.privateChats.value
            val msgs = chats[closed]
                ?: chats[com.bitchat.android.services.ContactDirectory.canonicalConversationId(closed)]
                ?: emptyList()
            val incoming = msgs.count { it.senderPeerID != viewModel.myPeerID && it.sender != "system" }
            if (incoming > 0 && !keepChats && !connectStore.isKeepChatsNudged()) {
                // They now hold a conversation they'd lose to a reinstall or dead battery —
                // the one moment "keep your chats?" is an offer, not a nag. Once, ever;
                // the in-chat banner stays as the quiet reminder.
                connectStore.setKeepChatsNudged()
                showIdentity = true
            } else if (incoming >= 2) {
                com.bitchat.android.connect.ReviewNudge.maybeAsk(activity)
            }
        }
        lastChatPeer = privateChatPeer
    }

    // System back peels one layer at a time — celebration, sheet, overlay, editor,
    // then non-home tab — instead of falling through and finishing the Activity.
    BackHandler(
        enabled = celebratePeerID != null || showInvite || showIdentity || showSettings ||
            showFilters || showQr || showTonight || showPlus || showWhoLiked || editingCard ||
            privateChatPeer != null || currentTab != ConnectTab.DISCOVER
    ) {
        when {
            celebratePeerID != null -> celebratePeerID = null
            showInvite -> showInvite = false
            showIdentity -> showIdentity = false
            showFilters -> showFilters = false
            showQr -> showQr = false
            showTonight -> showTonight = false
            showWhoLiked -> showWhoLiked = false
            showPlus -> showPlus = false
            showSettings -> showSettings = false
            editingCard -> editingCard = false
            privateChatPeer != null -> {
                // Leave the CHAT tab in the same frame the sheet closes, so the inherited
                // chat screen behind it is never revealed mid-dismiss.
                tab = ConnectTab.CONNECTIONS.name
                viewModel.hidePrivateChatSheet()
            }
            else -> tab = ConnectTab.DISCOVER.name
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (currentTab) {
                    ConnectTab.DISCOVER -> DiscoverScreen(viewModel, onInvite = { showInvite = true }, onFilters = { showFilters = true }, onOpenChat = openChat, onWhoLiked = { showWhoLiked = true })
                    ConnectTab.CONNECTIONS -> {
                        // Threads with no match behind them (peer reset identity, or messaged
                        // before a card was exchanged) still need a row — otherwise the only
                        // way back into a live conversation is its notification.
                        val privateChats by viewModel.privateChats.collectAsState()
                        val unreadStems = remember(unreadChats) {
                            unreadChats.map {
                                if (it.startsWith("contact_")) it.removePrefix("contact_").take(16) else it.take(16)
                            }.toSet()
                        }
                        val strays = remember(privateChats, matches, connectedPeers, unreadStems) {
                            val matchStems = matches.keys.map { it.take(16) }.toSet()
                            val myStem = viewModel.myPeerID.take(16)
                            val connectedSet = connectedPeers.toSet()
                            privateChats.entries
                                .mapNotNull { (key, msgs) ->
                                    val real = msgs.filter { it.sender != "system" }
                                    if (real.isEmpty()) return@mapNotNull null
                                    val stem = if (key.startsWith("contact_")) key.removePrefix("contact_").take(16) else key.take(16)
                                    if (stem.isBlank() || stem == myStem || stem in matchStems) return@mapNotNull null
                                    val theirLast = real.lastOrNull { it.senderPeerID?.take(16) == stem }
                                    Triple(stem, theirLast?.sender ?: "Nearby chat", real.maxOf { m -> m.timestamp.time })
                                }
                                .groupBy { it.first }
                                .map { (stem, rows) ->
                                    StrayThread(
                                        peerID = stem,
                                        name = rows.maxByOrNull { it.third }?.second ?: stem.take(8),
                                        here = stem in connectedSet,
                                        unread = stem in unreadStems
                                    )
                                }
                                .sortedByDescending { it.here }
                        }
                        MatchesScreen(
                            onOpenChat = openChat,
                            inRange = connectedPeers.toSet(),
                            strays = strays,
                            unreadStems = unreadStems,
                            onDiscover = { tab = ConnectTab.DISCOVER.name }
                        )
                    }
                    ConnectTab.CHAT -> {
                        // The Room is the venue-wide noticeboard; when a 1:1 chat is open, the
                        // inherited ChatScreen hosts that private conversation instead.
                        if (privateChatPeer != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .consumeWindowInsets(WindowInsets.navigationBars)
                            ) {
                                ChatScreen(
                                    viewModel = viewModel,
                                    onPrivateChatClosing = { tab = ConnectTab.CONNECTIONS.name }
                                )
                            }
                        } else {
                            RoomScreen(viewModel = viewModel, onTonight = { showTonight = true })
                        }
                    }
                    ConnectTab.YOU -> if (editingCard) {
                        ProfileEditorScreen(
                            viewModel = viewModel,
                            isFirstRun = false,
                            onInvite = { showInvite = true },
                            onDone = { editingCard = false }
                        )
                    } else {
                        YouScreen(
                            myPeerID = viewModel.myPeerID,
                            onEdit = { editingCard = true },
                            onInvite = { showInvite = true },
                            onKeepChats = { showIdentity = true },
                            onSettings = { showSettings = true },
                            onQr = { showQr = true },
                            onPlus = { showPlus = true }
                        )
                    }
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                ConnectTab.entries.forEach { t ->
                    // Chats badge = conversations with unread messages. Likes stay on the
                    // Discover ⚡ tray — they were never a Chats concern.
                    val badgeCount = when (t) {
                        ConnectTab.CONNECTIONS -> unreadChats.size
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = currentTab == t,
                        onClick = { tab = t.name },
                        icon = {
                            BadgedBox(badge = {
                                if (badgeCount > 0) Badge { Text("$badgeCount") }
                            }) {
                                Icon(
                                    painter = painterResource(t.iconRes),
                                    contentDescription = t.label
                                )
                            }
                        },
                        label = {
                            Text(
                                t.label.uppercase(),
                                fontFamily = ConnectMono,
                                fontSize = 10.sp,
                                letterSpacing = 0.08.em
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }

        if (showIdentity) {
            IdentityScreen(onDone = { showIdentity = false })
        }

        if (showSettings) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SettingsScreen(onClose = { showSettings = false }, onFilters = { showSettings = false; showFilters = true })
            }
        }

        if (showFilters) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                FiltersScreen(onClose = { showFilters = false })
            }
        }

        if (showQr) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                QrScreen(myPeerID = viewModel.myPeerID, onOpenChat = openChat, onClose = { showQr = false })
            }
        }

        if (showWhoLiked) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                WhoLikedScreen(onClose = { showWhoLiked = false })
            }
        }

        if (showPlus) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LocusPlusScreen(onClose = { showPlus = false }, onWhoLiked = { showPlus = false; showWhoLiked = true })
            }
        }

        if (showTonight) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                TonightScreen(
                    viewModel = viewModel,
                    onOpenChat = { peerID -> showTonight = false; openChat(peerID) },
                    onEditCard = { showTonight = false; editingCard = true; tab = ConnectTab.YOU.name },
                    onRoom = { showTonight = false; tab = ConnectTab.CHAT.name },
                    onClose = { showTonight = false }
                )
            }
        }

        if (showInvite) {
            InviteSheet(
                onBeam = {
                    showInvite = false
                    tab = ConnectTab.CHAT.name
                    viewModel.showAppInfo()
                },
                onDismiss = { showInvite = false }
            )
        }

        celebratePeerID?.let { peerID ->
            MatchCelebration(
                match = matches[peerID],
                myEmoji = myProfile?.emoji ?: "🜂",
                onSayHi = {
                    celebratePeerID = null
                    openChat(peerID)
                },
                onDismiss = {
                    celebratePeerID = null
                    // A dismissed celebration is a finished high note — from the second match
                    // on, the one place a review ask feels earned. (Say-hi leads into a chat;
                    // never interrupt that path.)
                    if (matches.size >= 2) {
                        com.bitchat.android.connect.ReviewNudge.maybeAsk(activity)
                    }
                }
            )
        }
    }
}
