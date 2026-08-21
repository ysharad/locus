package com.bitchat.android.connect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import com.bitchat.android.connect.ConnectMatch
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.Icebreakers

/**
 * Suggests opening lines for a fresh connection. Content comes from the backend
 * Cloud Function (Claude); this sheet only renders the loading / result / offline
 * states and hands a chosen line back to the caller to drop into the composer.
 */
@Composable
fun IcebreakerSheet(
    match: ConnectMatch,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val myProfile by ConnectManager.myProfile.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var openers by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(match.peerID) {
        loading = true
        openers = Icebreakers.suggest(myProfile, match.profile)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("ICEBREAKERS · MADE FROM YOUR CARDS", style = EyebrowStyle.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.14f, androidx.compose.ui.unit.TextUnitType.Em)), color = Jade)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Three ways in.",
                    style = TitleStyle.copy(fontSize = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            color = Signal,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "thinking of something good…",
                            style = BodyStyle.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    openers.isEmpty() -> Text(
                        "Couldn't reach the ideas service — you'll need internet for this. " +
                            "Say hi your own way for now.",
                        style = BodyStyle.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        openers.forEach { line ->
                            Card(
                                onClick = { onUse(line) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    line,
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    style = BodyStyle.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Tap one to drop it in the chat",
                            style = EyebrowStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (openers.isEmpty() && !loading) "Close" else "Not now",
                        style = BodyStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
