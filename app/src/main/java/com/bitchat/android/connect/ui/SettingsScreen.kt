package com.bitchat.android.connect.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bitchat.android.BuildConfig
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectStore
import com.bitchat.android.ui.theme.ThemePreference
import com.bitchat.android.ui.theme.ThemePreferenceManager

private const val PRIVACY_URL = "https://goapps.store/locus-privacy.html"
private const val SUPPORT_EMAIL = "hello@goapps.store"

@Composable
fun SettingsScreen(onClose: () -> Unit, onFilters: () -> Unit = {}) {
    var route by remember { mutableStateOf("hub") }
    // Composed deeper than ConnectRoot's BackHandler, so this wins while a
    // sub-screen is showing: back pops to the hub instead of closing Settings.
    androidx.activity.compose.BackHandler(enabled = route != "hub") { route = "hub" }
    when (route) {
        "privacy" -> PrivacySub(onBack = { route = "hub" })
        "notif" -> NotifSub(onBack = { route = "hub" })
        "blocked" -> BlockedSub(onBack = { route = "hub" })
        "safety" -> SafetySub(onBack = { route = "hub" }, onBlocked = { route = "blocked" })
        else -> SettingsHub(
            onClose = onClose, onFilters = onFilters,
            onPrivacy = { route = "privacy" }, onNotif = { route = "notif" },
            onBlocked = { route = "blocked" }, onSafety = { route = "safety" }
        )
    }
}

@Composable
private fun SettingsHub(
    onClose: () -> Unit, onFilters: () -> Unit,
    onPrivacy: () -> Unit, onNotif: () -> Unit, onBlocked: () -> Unit, onSafety: () -> Unit
) {
    val context = LocalContext.current
    val keepChats by ConnectManager.keepChats.collectAsState()
    val visible by ConnectManager.visible.collectAsState()
    val theme by ThemePreferenceManager.themeFlow.collectAsState()
    val blocked by ConnectManager.blocked.collectAsState()
    val filters by ConnectManager.filters.collectAsState()

    var anchored by remember {
        mutableStateOf(
            runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.providerData
                    ?.any { it.providerId == "google.com" || it.providerId == "phone" } == true
            }.getOrDefault(false)
        )
    }
    var confirmLogout by remember { mutableStateOf(false) }

    ScrollScreen {
        TopBar("Settings", onBack = onClose)

        if (!keepChats) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CHATS AREN'T BEING SAVED", style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.06.em), color = Slate, modifier = Modifier.weight(1f))
                Text("TURN ON", style = EyebrowStyle.copy(fontSize = 11.sp), color = Copper, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ConnectManager.setKeepChats(true) }.padding(4.dp))
            }
        }

        Section("ACCOUNT & PRIVACY") {
            NavRow("Privacy & visibility", if (visible) "Visible" else "Hidden", onPrivacy)
            if (anchored) LinkRow("Log out") { confirmLogout = true }
            NavRow("Export or delete my data", null, onPrivacy)
        }
        Section("NOTIFICATIONS") { NavRow("Notifications", null, onNotif) }
        Section("DISCOVERY") {
            ToggleRow("Pause discovery", null, !visible) { ConnectManager.setVisible(!it) }
            NavRowValue("Filters", if (filters.activeCount > 0) "${filters.activeCount} ON" else "OFF", onFilters)
        }
        Section("SAFETY") {
            NavRowValue("Blocked people", "${blocked.map { it.take(16) }.toSet().size}", onBlocked)
            NavRow("Safety centre", null, onSafety)
        }
        Section("APPEARANCE") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                ThemeToggle(theme) { ThemePreferenceManager.set(context, it) }
            }
            Hair()
        }
        Section("ABOUT") {
            LinkRow("Privacy policy") { openUrl(context, PRIVACY_URL) }
            LinkRow("Contact support") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                // GPL-3.0 obliges us to convey the licence and offer the source. It doesn't
                // oblige us to shout: tap the version and it's there.
                var showLegal by remember { mutableStateOf(false) }
                Text(
                    "Locus v${BuildConfig.VERSION_NAME}",
                    style = EyebrowStyle.copy(fontSize = 11.sp),
                    color = Slate,
                    modifier = Modifier.clickable { showLegal = !showLegal }
                )
                if (showLegal) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Free software under GPL-3.0, built on the bitchat mesh.",
                        style = BodyStyle.copy(fontSize = 12.sp),
                        color = Slate
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Source code & licences",
                        style = BodyStyle.copy(fontSize = 12.sp),
                        color = Copper,
                        modifier = Modifier.clickable {
                            openUrl(context, "https://github.com/ysharad/locus")
                        }
                    )
                }
            }
        }
    }
    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                com.bitchat.android.connect.GoogleAnchor.logout()
                anchored = false
                confirmLogout = false
            },
            onDismiss = { confirmLogout = false }
        )
    }
}

@Composable
private fun PrivacySub(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ConnectStore(context) }
    val visible by ConnectManager.visible.collectAsState()
    var connOnly by remember { mutableStateOf(store.bool(ConnectStore.KEY_CONNECTIONS_ONLY, false)) }
    var readReceipts by remember { mutableStateOf(store.bool(ConnectStore.KEY_READ_RECEIPTS, true)) }
    var typing by remember { mutableStateOf(store.bool(ConnectStore.KEY_TYPING, true)) }
    var lastSeen by remember { mutableStateOf(store.bool(ConnectStore.KEY_LAST_SEEN, true)) }
    var confirmWipe by remember { mutableStateOf(false) }
    val profile by ConnectManager.myProfile.collectAsState()

    ScrollScreen {
        TopBar("Privacy", onBack = onBack)
        ToggleRow("Visible in the room", null, visible) { ConnectManager.setVisible(it) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Who can see my profile", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Narrowing this stops the broadcast.", style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SegTwo("EVERYONE IN RANGE", "CONNECTIONS ONLY", selectedSecond = connOnly) {
                connOnly = it; store.setBool(ConnectStore.KEY_CONNECTIONS_ONLY, it)
            }
        }
        Hair()

        Text("WHAT OTHERS SEE ABOUT YOU", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
        Hair()
        ToggleRow("Read receipts", "Off for them too, if you turn it off", readReceipts) { readReceipts = it; store.setBool(ConnectStore.KEY_READ_RECEIPTS, it) }
        ToggleRow("Typing indicator", null, typing) { typing = it; store.setBool(ConnectStore.KEY_TYPING, it) }
        ToggleRow("Last seen", null, lastSeen) { lastSeen = it; store.setBool(ConnectStore.KEY_LAST_SEEN, it) }

        Text("YOUR DATA", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
        Hair()
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Your key", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(store.myFingerprint()?.uppercase()?.take(16)?.chunked(4)?.joinToString(" · ") ?: "—", style = EyebrowStyle.copy(fontSize = 13.sp, letterSpacing = 0.1.em), color = Jade)
        }
        Hair()
        LinkRowValue("Export my data", "JSON") {
            val json = profile?.toJson() ?: "{}"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, json) }, "Export profile"))
        }
        Row(Modifier.fillMaxWidth().clickable { confirmWipe = true }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Delete everything", style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(3.dp))
                Text("Key, profile, chats, connections. Instant.", style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", color = MaterialTheme.colorScheme.error)
        }
        Hair()
        Footer("WE HOLD YOUR CARD AND A TIMESTAMP. NOTHING ELSE.")
    }

    if (confirmWipe) WipeDialog(onConfirm = { ConnectManager.wipeLocalData(); confirmWipe = false; onBack() }, onDismiss = { confirmWipe = false })
}

@Composable
private fun NotifSub(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ConnectStore(context) }
    val notifyMessages by ConnectManager.notifyMessages.collectAsState()
    val notifyNearby by ConnectManager.notifyNearby.collectAsState()
    var all by remember { mutableStateOf(store.bool(ConnectStore.KEY_NOTIFY_ALL, true)) }
    var mention by remember { mutableStateOf(store.bool(ConnectStore.KEY_NOTIFY_MENTIONED, true)) }
    var quiet by remember { mutableStateOf(store.bool(ConnectStore.KEY_QUIET_HOURS, false)) }

    ScrollScreen {
        TopBar("Notifications", onBack = onBack)
        ToggleRow("All notifications", null, all) { all = it; store.setBool(ConnectStore.KEY_NOTIFY_ALL, it) }
        Text("WHAT BUZZES", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
        Hair()
        ToggleRow("New message", null, notifyMessages) { ConnectManager.setNotifyMessages(it) }
        ToggleRow("Someone new in range", null, notifyNearby) { ConnectManager.setNotifyNearby(it) }
        ToggleRow("Mentioned in the Room", null, mention) { mention = it; store.setBool(ConnectStore.KEY_NOTIFY_MENTIONED, it) }
        Text("WHEN", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
        Hair()
        ToggleRow("Quiet hours", "23:00 → 09:00", quiet) { quiet = it; store.setBool(ConnectStore.KEY_QUIET_HOURS, it) }
        Footer("MESH ALERTS ARE RAISED ON THIS PHONE. NOTHING IS PUSHED THROUGH A SERVER UNLESS YOU'RE SIGNED IN.")
    }
}

@Composable
private fun BlockedSub(onBack: () -> Unit) {
    val blocked by ConnectManager.blocked.collectAsState()
    ScrollScreen {
        TopBar("Blocked", onBack = onBack)
        if (blocked.isEmpty()) {
            Footer("NO ONE BLOCKED · BLOCKS FOLLOW THE KEY, NOT THE NAME.")
        } else {
            // fp + peerID share a 16-hex stem and describe the same person — show them once.
            val people = blocked.groupBy { it.take(16) }.values.map { keys -> keys.maxByOrNull { it.length }!! }
            people.forEach { key ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                        Text("◇", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Blocked profile", style = ListNameStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(key.take(8).uppercase(), style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.08.em), color = Slate)
                    }
                    Text("UNBLOCK", style = EyebrowStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).clickable { ConnectManager.unblock(key) }.padding(horizontal = 14.dp, vertical = 9.dp))
                }
                Hair()
            }
            Footer("REPORTS ARE REVIEWED BY A HUMAN · WE SEE THE CARD AND FINGERPRINT, NOT YOUR MESSAGES.")
        }
    }
}

@Composable
private fun SafetySub(onBack: () -> Unit, onBlocked: () -> Unit) {
    val context = LocalContext.current
    ScrollScreen {
        TopBar("Safety", onBack = onBack)
        Column(
            Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Copper, RoundedCornerShape(20.dp)).background(Color(0xFF12100C)).padding(22.dp)
        ) {
            Text("MEETING SOMEONE TONIGHT?", style = EyebrowStyle.copy(letterSpacing = 0.14.em), color = Copper)
            Spacer(Modifier.height(10.dp))
            Text("Send a friend your plan. Only what you type is shared.", style = BodyStyle.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Heads up — I'm meeting someone I connected with on Locus tonight. I'll check in with you by ") }, "Share my night")) },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
            ) { Text("Share my night", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold)) }
        }
        Text("BEFORE YOU MEET", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        listOf(
            "Stay in the venue for the first conversation. In range means in the building.",
            "A profile is written by its owner. Nothing on it has been checked by anyone.",
            "If it feels wrong, block. It's instant, local, and silent."
        ).forEach { tip ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text(tip, style = BodyStyle.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(16.dp))
        NavRowValue("Blocked people", "", onBlocked)
        LinkRow("Support lines & resources") { openUrl(context, "https://www.befrienders.org/") }
        Footer("18+ ONLY · REPORTS ARE REVIEWED BY A HUMAN")
    }
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Log out", style = TitleStyle.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your chats stay on this phone. Log back in any time to pick them up again.",
                    style = BodyStyle.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
                ) { Text("Log out", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold)) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Text("Cancel", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- shared building blocks ----

@Composable
private fun ScrollScreen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding(),
        content = content
    )
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Footer(text: String) {
    Text(text, style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em, lineHeight = 16.sp), color = Slate, modifier = Modifier.padding(20.dp))
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Text(label, style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
    Hair()
    content()
}

@Composable
private fun Hair() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(on) { onToggle(!on) }
    }
    Hair()
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("→", style = BodyStyle.copy(fontSize = 16.sp), color = Slate)
    }
    Hair()
}

@Composable
private fun NavRowValue(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(if (value.isBlank()) "→" else "$value →", style = EyebrowStyle.copy(fontSize = 11.sp), color = Slate)
    }
    Hair()
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("↗", style = BodyStyle.copy(fontSize = 15.sp), color = Slate)
    }
    Hair()
}

@Composable
private fun LinkRowValue(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("$value →", style = EyebrowStyle.copy(fontSize = 11.sp), color = Slate)
    }
    Hair()
}

@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) Jade else MaterialTheme.colorScheme.outline).clickable { onToggle() },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(if (on) OnJade else MaterialTheme.colorScheme.surface))
    }
}

@Composable
private fun SegTwo(first: String, second: String, selectedSecond: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to first, true to second).forEach { (isSecond, label) ->
            val active = selectedSecond == isSecond
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (active) Copper else Color.Transparent).clickable { onSelect(isSecond) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(label, style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em), color = if (active) OnCopper else Slate)
            }
        }
    }
}

@Composable
private fun ThemeToggle(current: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(ThemePreference.System to "AUTO", ThemePreference.Dark to "DARK", ThemePreference.Light to "LIGHT").forEach { (pref, label) ->
            val active = current == pref
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (active) Copper else Color.Transparent).clickable { onSelect(pref) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(label, style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em), color = if (active) OnCopper else Slate)
            }
        }
    }
}

@Composable
private fun WipeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp)) {
                Text("Delete everything", style = TitleStyle.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Text("Your profile, connections, and settings are erased from this phone, and your cloud profile is removed. This can't be undone.", style = BodyStyle.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Text("Delete everything", style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
}
