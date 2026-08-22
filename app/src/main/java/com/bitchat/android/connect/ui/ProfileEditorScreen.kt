package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile
import com.bitchat.android.ui.ChatViewModel

// Curated glyph marks, not emoji — mostly-monochrome symbols that keep the palette intact.
// A picker full of 🎉🔥😎 would undo the whole system in one row.
private val EMOJI_OPTIONS = listOf(
    "🜂", "🝊", "◈", "◆", "◇", "❖", "❍", "○", "◉", "◐", "☾", "✦",
    "✧", "✶", "✷", "▲", "△", "⬡", "⬢", "⟡", "⧫", "✵", "◍", "◈"
)

/**
 * Create or edit your card. This is the only identity in the app — no login,
 * no account, nothing leaves the device except the card you choose to broadcast.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ChatViewModel,
    isFirstRun: Boolean,
    onInvite: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    val existing by ConnectManager.myProfile.collectAsState()
    val meshNickname by viewModel.nickname.collectAsState()

    var name by remember { mutableStateOf(existing?.name ?: meshNickname) }
    var ageText by remember { mutableStateOf(existing?.age?.toString() ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: EMOJI_OPTIONS.first()) }
    var bio by remember { mutableStateOf(existing?.bio ?: "") }
    var vibes by remember { mutableStateOf(existing?.vibes ?: emptyList()) }
    var hereTo by remember { mutableStateOf(existing?.hereTo ?: "") }
    var saved by remember { mutableStateOf(false) }
    val editorContext = androidx.compose.ui.platform.LocalContext.current
    val editorStore = remember { com.bitchat.android.connect.ConnectStore(editorContext.applicationContext) }
    var glyphColorArgb by remember { mutableStateOf(existing?.glyphColor ?: 0) }
    val glyphTint = if (glyphColorArgb != 0) androidx.compose.ui.graphics.Color(glyphColorArgb) else Copper
    // Both names live on, so flipping the switch never destroys the one you're not using.
    var anonName by remember { mutableStateOf(editorStore.str("name_anon", existing?.name ?: meshNickname)) }
    var realName by remember { mutableStateOf(editorStore.str("name_real", "")) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (isFirstRun) "Set up your profile" else "Profile",
            style = TitleStyle.copy(fontSize = 30.sp),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(18.dp))
        // The card as the room will see it — no label needed, it looks like what it is.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiAvatar(emoji, size = 56, glyphTint = glyphTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (name.ifBlank { "Your name" }) + (ageText.toIntOrNull()?.let { ", $it" } ?: ""),
                    style = CardNameStyle.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (hereTo.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("HERE TO ${hereTo.uppercase()}", style = EyebrowStyle.copy(fontSize = 10.sp), color = Copper, maxLines = 1)
                }
                if (bio.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(bio, style = BodyStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        // Glyph and its colour, one line each — the identity mark shouldn't cost half a screen.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(emoji, size = 56, ring = glyphTint)
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(EMOJI_OPTIONS) { option ->
                    val selected = option == emoji
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) Modifier.border(2.dp, glyphTint, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                            .background(if (selected) CopperTint else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { emoji = option; saved = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, fontSize = 20.sp, color = if (selected) glyphTint else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(GlyphColors) { c ->
                val picked = c.value.toLong() == glyphTint.value.toLong()
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(
                            if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        .clickable {
                            glyphColorArgb = c.toArgb()
                            saved = false
                        }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        // Anonymity is a real switch, not an implication — and both names persist, so
        // flipping it back and forth never loses what you typed.
        var stayAnon by remember { mutableStateOf(editorStore.bool("stay_anonymous", true)) }
        val googleFirstName = remember {
            runCatching {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.providerData
                    ?.firstOrNull { it.providerId == "google.com" }?.displayName
                    ?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stay anonymous", style = BodyStyle.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (stayAnon) "Pick any name — nobody sees who's behind it."
                    else "Your real name is on your profile.",
                    style = BodyStyle.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = Jade,
                    checkedThumbColor = androidx.compose.ui.graphics.Color(0xFF0C1210),
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                ),
                checked = stayAnon,
                onCheckedChange = { on ->
                    // Park the name you were editing under its own key before switching.
                    if (stayAnon) { anonName = name; editorStore.setStr("name_anon", name) }
                    else { realName = name; editorStore.setStr("name_real", name) }
                    stayAnon = on
                    editorStore.setBool("stay_anonymous", on)
                    name = if (on) anonName.ifBlank { meshNickname }
                           else realName.ifBlank { googleFirstName.orEmpty() }
                    saved = false
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(ConnectProfile.MAX_NAME)
                if (stayAnon) { anonName = name; editorStore.setStr("name_anon", name) }
                else { realName = name; editorStore.setStr("name_real", name) }
                saved = false
            },
            label = { Text(if (stayAnon) "Anonymous name — pick anything" else "Your name") },
            singleLine = true,
            textStyle = BodyStyle,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ageText,
            onValueChange = { ageText = it.filter(Char::isDigit).take(3); saved = false },
            label = { Text("Age (optional)") },
            singleLine = true,
            textStyle = BodyStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(ConnectProfile.MAX_BIO); saved = false },
            label = { Text("One-liner (${bio.length}/${ConnectProfile.MAX_BIO})") },
            minLines = 2,
            textStyle = BodyStyle,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("HERE TO…", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HERE_TO_OPTIONS.forEach { option ->
                FilterChip(
                    selected = hereTo == option,
                    onClick = { hereTo = if (hereTo == option) "" else option; saved = false },
                    label = { Text(option, style = BodyStyle.copy(fontSize = 13.sp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "VIBES ${vibes.size}/${ConnectProfile.MAX_VIBES}",
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VIBE_OPTIONS.forEach { vibe ->
                val selected = vibe in vibes
                FilterChip(
                    selected = selected,
                    onClick = {
                        vibes = when {
                            selected -> vibes - vibe
                            vibes.size < ConnectProfile.MAX_VIBES -> vibes + vibe
                            else -> vibes
                        }
                        saved = false
                    },
                    label = { Text(vibe, style = BodyStyle.copy(fontSize = 13.sp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                val profile = ConnectProfile(
                    name = name.trim(),
                    age = ageText.toIntOrNull()?.takeIf { it in 18..120 },
                    emoji = emoji,
                    bio = bio.trim(),
                    vibes = vibes,
                    hereTo = hereTo,
                    glyphColor = glyphColorArgb
                )
                ConnectManager.saveProfile(profile)
                if (name.trim().isNotEmpty() && name.trim() != meshNickname) {
                    viewModel.setNickname(name.trim())
                }
                saved = true
                if (!isFirstRun) onDone?.invoke()
            },
            enabled = name.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Copper,
                contentColor = OnCopper
            )
        ) {
            Text(
                when {
                    isFirstRun -> "Start discovering"
                    saved -> "Saved ✓"
                    else -> "Save & broadcast"
                },
                style = BodyStyle.copy(fontWeight = FontWeight.Bold)
            )
        }
        if (!isFirstRun && onInvite != null) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onInvite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Invite people", style = BodyStyle)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
