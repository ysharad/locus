package com.bitchat.android.connect.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val STORE_URL = "https://play.google.com/store/apps/details?id=com.goapps.locus"
private const val INVITE_TEXT =
    "I'm on Locus — discover and meet people around you, even with zero internet. Get it: $STORE_URL"

/**
 * Growth surface: every venue is empty until people in it have the app. The offline "beam" is the
 * hero — it works exactly where a store link doesn't (no signal, no wifi).
 */
@Composable
fun InviteSheet(onBeam: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    BottomSheet(onDismiss = onDismiss) {
        Text("Fill the room", style = TitleStyle.copy(fontSize = 24.sp), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))
        // Beam — the hero, copper outline + NO INTERNET band.
        InviteRow(
            label = "Beam the app",
            trailing = { Text("NO INTERNET", style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.1.em), color = Jade) },
            border = Copper,
            bg = CopperTint,
            onClick = onBeam
        )
        Spacer(Modifier.height(10.dp))
        InviteRow(
            label = "Send a link",
            trailing = { Text("→", style = BodyStyle.copy(fontSize = 17.sp), color = Slate) },
            border = MaterialTheme.colorScheme.outline,
            bg = MaterialTheme.colorScheme.surfaceVariant,
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, INVITE_TEXT)
                }
                context.startActivity(Intent.createChooser(send, "Invite via…"))
                onDismiss()
            }
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InviteRow(
    label: String,
    trailing: @Composable () -> Unit,
    border: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        trailing()
    }
}
