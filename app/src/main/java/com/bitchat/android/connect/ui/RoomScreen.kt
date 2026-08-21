package com.bitchat.android.connect.ui

import android.os.Build
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Glyphs used to give each speaker a stable, monochrome mark in the noticeboard. */
private val ROOM_GLYPHS = listOf("◈", "☾", "✦", "🝊", "❍", "◇", "⬡", "✷", "◐", "⟡", "△", "◉")
private fun glyphFor(name: String): String = ROOM_GLYPHS[(name.hashCode() and 0x7fffffff) % ROOM_GLYPHS.size]

/**
 * The Room — the venue-wide channel, deliberately NOT bubbles. A noticeboard: hairline-separated
 * rows, the speaker's glyph and name inline, mono timestamps. It reads like a board people post to,
 * not an intimate thread — and nothing is kept.
 */
@Composable
fun RoomScreen(viewModel: ChatViewModel, onTonight: () -> Unit = {}) {
    val messages by viewModel.messages.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val public = remember(messages) { messages.filter { !it.isPrivate } }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(public.size) {
        if (public.isNotEmpty()) listState.animateScrollToItem(public.size)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            // Pre-R the framework still legacy-resizes the edge-to-edge window for
            // adjustResize, so imePadding() here would double-inset and shove the
            // composer clean out of view (typing blind — seen on Android 10).
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Modifier.imePadding() else Modifier)
    ) {
        // Header
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("The Room", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(
                    "TONIGHT", style = EyebrowStyle.copy(fontSize = 12.sp), color = Jade,
                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)).clickable { onTonight() }.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (connectedPeers.isEmpty()) MaterialTheme.colorScheme.outlineVariant else Jade, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connectedPeers.isEmpty()) "LISTENING · PHONE TO PHONE"
                    else "${connectedPeers.size} IN RANGE · PHONE TO PHONE",
                    style = EyebrowStyle.copy(letterSpacing = 0.14.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

        // Board. A room feed reads from the bottom up: an empty room centres its invitation, and a
        // sparse one rests its messages just above the composer rather than stranding them up top.
        if (public.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("QUIET IN HERE", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Say something to the room.", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.Bottom
            ) {
                items(public.size) { i -> RoomRow(public[i], timeFmt) }
                item {
                    Text(
                        "NOTHING IS ARCHIVED",
                        style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.1.em),
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    )
                }
            }
        }

        // Composer
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(23.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = BodyStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Copper),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text("Say it to the room", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
            }
            val canSend = draft.isNotBlank()
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Copper else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = canSend) {
                        val text = draft.trim()
                        draft = ""
                        viewModel.sendMessage(text)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("↑", fontSize = 20.sp, color = if (canSend) OnCopper else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoomRow(msg: BitchatMessage, timeFmt: SimpleDateFormat) {
    if (msg.sender == "system") {
        Text(
            msg.content,
            style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em),
            color = Slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp)
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyphFor(msg.sender), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(msg.sender, style = ListNameStyle.copy(fontSize = 14.5.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text(timeFmt.format(msg.timestamp), style = EyebrowStyle.copy(fontSize = 11.sp, letterSpacing = 0.04.em), color = Slate)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            msg.content,
            style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 23.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
}
