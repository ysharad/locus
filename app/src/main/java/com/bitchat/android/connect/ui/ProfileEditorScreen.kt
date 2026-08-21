package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
        ConnectHeader(
            eyebrow = if (isFirstRun) "no account · no internet" else "broadcasts to everyone in range",
            title = if (isFirstRun) "Make your card" else "Your card"
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isFirstRun)
                "This is what people around you see. Your card travels device to device."
            else
                "Changes go out with the next broadcast.",
            style = BodyStyle.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            EmojiAvatar(emoji, size = 96)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EMOJI_OPTIONS.forEach { option ->
                val selected = option == emoji
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) Modifier.border(2.dp, Copper, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        .background(if (selected) CopperTint else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { emoji = option; saved = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(option, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(ConnectProfile.MAX_NAME); saved = false },
            label = { Text("Name") },
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
                    hereTo = hereTo
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
