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

    if (!ageConfirmed) {
        AgeGateScreen()
        return
    }
    if (!identitySeen) {
        // Skip / save both flip identitySeen → this recomposes onward to the card.
        IdentityScreen(onDone = {})
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
    val currentTab = ConnectTab.valueOf(tab)

    val matches by ConnectManager.matches.collectAsState()
    val likesReceived by ConnectManager.likesReceived.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()

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
    LaunchedEffect(privateChatPeer) {
        if (privateChatPeer != null) {
            if (tab != ConnectTab.CHAT.name) tab = ConnectTab.CHAT.name
        } else if (lastChatPeer != null) {
            tab = ConnectTab.CONNECTIONS.name
        }
        lastChatPeer = privateChatPeer
    }

    // System back peels one layer at a time — celebration, sheet, overlay, editor,
    // then non-home tab — instead of falling through and finishing the Activity.
    BackHandler(
        enabled = celebratePeerID != null || showInvite || showIdentity || showSettings ||
            showFilters || showQr || showTonight || showPlus || editingCard ||
            privateChatPeer != null || currentTab != ConnectTab.DISCOVER
    ) {
        when {
            celebratePeerID != null -> celebratePeerID = null
            showInvite -> showInvite = false
            showIdentity -> showIdentity = false
            showFilters -> showFilters = false
            showQr -> showQr = false
            showTonight -> showTonight = false
            showPlus -> showPlus = false
            showSettings -> showSettings = false
            editingCard -> editingCard = false
            privateChatPeer != null -> viewModel.hidePrivateChatSheet()
            else -> tab = ConnectTab.DISCOVER.name
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (currentTab) {
                    ConnectTab.DISCOVER -> DiscoverScreen(viewModel, onInvite = { showInvite = true }, onFilters = { showFilters = true })
                    ConnectTab.CONNECTIONS -> MatchesScreen(onOpenChat = openChat, inRange = connectedPeers.toSet())
                    ConnectTab.CHAT -> {
                        // The Room is the venue-wide noticeboard; when a 1:1 chat is open, the
                        // inherited ChatScreen hosts that private conversation instead.
                        if (privateChatPeer != null) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .consumeWindowInsets(WindowInsets.navigationBars)
                            ) {
                                ChatScreen(viewModel = viewModel)
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
                    val badgeCount = when (t) {
                        ConnectTab.CONNECTIONS -> likesReceived.size
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

        if (showPlus) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LocusPlusScreen(onClose = { showPlus = false })
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
                onDismiss = { celebratePeerID = null }
            )
        }
    }
}
