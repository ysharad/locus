package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.CloudSync
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.GoogleAnchor
import com.bitchat.android.connect.PhoneAnchor

/**
 * Optional sign-in — the one benefit named plainly: keep your chats. Signed in, messages survive
 * closing the app and can follow you online (encrypted); skipped, they stay on this phone. Either
 * way the identity key never leaves the device.
 */
@Composable
fun IdentityScreen(onDone: () -> Unit) {
    var showForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LocusField(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 26.dp)
        ) {
            Spacer(Modifier.weight(1f))

            val context = androidx.compose.ui.platform.LocalContext.current
            val googleAvailable = remember { GoogleAnchor.webClientId(context) != null }
            var anchored by remember {
                mutableStateOf(
                    runCatching {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.providerData
                            ?.any { it.providerId == "google.com" || it.providerId == "phone" } == true
                    }.getOrDefault(false)
                )
            }
            val googleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                GoogleAnchor.handleResult(result.data) { ok, message ->
                    busy = false
                    if (ok) anchored = true else error = message
                }
            }

            var phoneMode by remember { mutableStateOf(false) }

            if (anchored) {
                Text("✓", style = TitleStyle.copy(fontSize = 46.sp), color = Jade)
                Spacer(Modifier.height(10.dp))
                Text("You're logged in", style = TitleStyle.copy(fontSize = 28.sp), color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your chats and connections are saved.",
                    style = BodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Done") {
                    ConnectManager.setKeepChats(true)
                    GoogleAnchor.retryClaimIfNeeded()
                    onDone()
                }
            } else if (phoneMode) {
                Text("Log in", style = TitleStyle.copy(fontSize = 30.sp), color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(18.dp))
                PhoneLoginPane(
                    busy = busy,
                    setBusy = { busy = it },
                    onError = { error = it },
                    onAnchored = { phoneMode = false; anchored = true },
                    onBack = { phoneMode = false; error = null }
                )
            } else {
                Text("Log in?", style = TitleStyle.copy(fontSize = 30.sp), color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Saves your chats and connections, and earns the ✓ verified badge.",
                    style = BodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(26.dp))
                if (googleAvailable) {
                    // Google-branded: white button, the G mark, dark label.
                    Button(
                        onClick = {
                            error = null
                            val intent = GoogleAnchor.signInIntent(context)
                            if (intent != null) {
                                busy = true
                                googleLauncher.launch(intent)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color.White,
                            contentColor = androidx.compose.ui.graphics.Color(0xFF1F1F1F)
                        )
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.bitchat.android.R.drawable.ic_google_g),
                            contentDescription = null
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            if (busy) "  Connecting…" else "  Log in with Google",
                            style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { error = null; phoneMode = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Log in with phone number", style = BodyStyle, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    PrimaryButton("Log in with phone number") { error = null; phoneMode = true }
                }
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = BodyStyle.copy(fontSize = 13.sp), color = Rust)
            }
            if (!anchored && !phoneMode) {
                // "OR" rule separating the two real choices, then the quieter one as a proper
                // (outlined, low-emphasis) button rather than a line of text pretending to be one.
                Spacer(Modifier.height(18.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Text(
                        "OR",
                        style = EyebrowStyle.copy(fontSize = 11.sp),
                        color = Slate,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = { ConnectManager.markIdentitySeen(); onDone() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        "Continue without logging in",
                        style = BodyStyle.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chats and connections stay on this phone only.",
                    style = BodyStyle.copy(fontSize = 12.sp),
                    color = Slate,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            // The 18+ attestation lives here now — one quiet line instead of a whole screen.
            // Every path off this screen passes it.
            Text(
                "By continuing, you confirm you're 18 or older.",
                style = BodyStyle.copy(fontSize = 12.sp),
                color = Slate,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
    ) {
        Text(label, style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
internal fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = BodyStyle.copy(fontSize = 14.sp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Copper,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = Copper,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
