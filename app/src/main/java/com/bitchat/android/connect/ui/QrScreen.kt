@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)

package com.bitchat.android.connect.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile
import com.bitchat.android.hotspot.QrCodeGenerator
import com.bitchat.android.ui.ScannerView
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private const val QR_TAG = "LOCUSQR1|"

/** Show-my-code / scan-theirs. A scan is a mutual connect on the spot — it carries the key, not a link. */
@Composable
fun QrScreen(myPeerID: String, onOpenChat: (String) -> Unit, onClose: () -> Unit) {
    val profile by ConnectManager.myProfile.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var connectedName by remember { mutableStateOf<String?>(null) }
    // Back from the scanner returns to my code rather than closing the screen.
    androidx.activity.compose.BackHandler(enabled = scanning) { scanning = false }
    val myFp = remember { ConnectManager.myFingerprint() ?: myPeerID }

    val keyHex = remember(myFp) { myFp.uppercase().take(16).chunked(4).joinToString(" · ") }
    val payload = remember(profile, myFp) { profile?.let { "$QR_TAG$myFp|${it.copy(peerID = myFp).toJson()}" } ?: "" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { if (scanning) scanning = false else onClose() }, contentAlignment = Alignment.Center) {
                Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(6.dp))
            Text("In person", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground)
        }

        when {
            connectedName != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                EmojiAvatar("⚡", size = 88, ring = Jade)
                Spacer(Modifier.height(20.dp))
                Text("MUTUAL", style = EyebrowStyle.copy(letterSpacing = 0.28.em), color = Copper)
                Spacer(Modifier.height(12.dp))
                Text("You're connected with ${connectedName}.", style = TitleStyle.copy(fontSize = 24.sp), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)) {
                    Text("Done", style = BodyStyle.copy(fontSize = 17.sp))
                }
            }

            scanning -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)
                if (camPerm.status.isGranted) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        ScannerView(onScan = { code ->
                            if (connectedName == null && code.startsWith(QR_TAG)) {
                                parseCard(code)?.let { p ->
                                    ConnectManager.connectViaQr(p)
                                    connectedName = p.name
                                }
                            }
                        })
                        Box(Modifier.size(220.dp).border(2.dp, Copper, RoundedCornerShape(20.dp)))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("POINT AT THEIR LOCUS CODE", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate)
                    Spacer(Modifier.height(20.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                    Text("Camera access is needed to scan a code.", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { camPerm.launchPermissionRequest() }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)) {
                        Text("Allow camera", style = BodyStyle)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                val bmp = remember(payload) { if (payload.isNotEmpty()) QrCodeGenerator.generateQrBitmap(payload, 640) else null }
                Box(Modifier.size(212.dp).clip(RoundedCornerShape(20.dp)).background(Bone).padding(14.dp), contentAlignment = Alignment.Center) {
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = "Your Locus code", modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(24.dp))
                Text("POINT THEIR CAMERA AT THIS", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate)
                Spacer(Modifier.height(12.dp))
                Text("Connect without swiping.", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("A scan is a mutual connect on the spot. It carries your key, not a link.", style = BodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text(keyHex, style = EyebrowStyle.copy(fontSize = 12.sp, letterSpacing = 0.1.em), color = Jade)
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text("Scan theirs instead", style = BodyStyle, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun parseCard(code: String): ConnectProfile? = try {
    val rest = code.removePrefix(QR_TAG)
    val sep = rest.indexOf('|')
    if (sep <= 0) null else {
        val fp = rest.take(sep)
        val json = rest.substring(sep + 1)
        // Key the scanned card by the 16-hex mesh peerID (the first half of the 64-hex fingerprint,
        // exactly how the mesh derives it), so a QR match matches on the same id as everything else —
        // presence, the deck's dedup, Tonight, and the chat thread all key on the 16-hex peerID.
        val peerID = if (fp.length >= 16) fp.take(16) else fp
        ConnectProfile.fromJson(json, peerID, System.currentTimeMillis())?.copy(peerID = peerID)
    }
} catch (e: Exception) { null }
