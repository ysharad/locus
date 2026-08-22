package com.bitchat.android.connect.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bitchat.android.connect.Countries
import com.bitchat.android.connect.Country
import com.bitchat.android.connect.PhoneAnchor
import kotlinx.coroutines.delay

private const val RESEND_SECONDS = 30

/**
 * Phone login: country + number, then the code. Deliberately a stable little state machine —
 * the code step survives recomposition and rotation, the resend button waits out a visible
 * countdown, and Android's SMS User Consent fills the code in without the app ever asking to
 * read the inbox.
 */
@Composable
internal fun PhoneLoginPane(
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onAnchored: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var country by rememberSaveable(stateSaver = CountrySaver) {
        mutableStateOf(Countries.default(context))
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    // Flipped the instant "Send code" is tapped so the code screen appears immediately;
    // the SMS request finishes in the background and just enables Verify when it lands.
    var awaitingCode by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by rememberSaveable { mutableStateOf(0) }

    val result: (Boolean, String?) -> Unit = { ok, message ->
        setBusy(false)
        if (ok) onAnchored() else onError(message)
    }

    // Countdown that gates "Resend code".
    LaunchedEffect(awaitingCode, secondsLeft) {
        if (awaitingCode && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    // Auto-detect note: Firebase Auth runs its OWN SMS retriever for this number and calls
    // onVerificationCompleted when it reads the code — that is the auto-fill, and it needs no
    // permission. Do NOT also start SMS User Consent: it fires the same broadcast in a shape
    // Firebase's receiver doesn't expect (consent intent, no message body), and Firebase then
    // regexes a null string and takes the whole process down. One retriever only.

    fun sendCode(resend: Boolean) {
        val act = activity ?: return
        onError(null)
        val digits = phone.filter(Char::isDigit)
        if (digits.length < 6) {
            onError("That number looks too short.")
            return
        }
        // Move to the code screen NOW — waiting on the network to change screens felt broken.
        awaitingCode = true
        secondsLeft = RESEND_SECONDS
        PhoneAnchor.start(
            act,
            "+${country.dial}$digits",
            resend = resend,
            onCodeSent = { id -> verificationId = id },
            onDone = { ok, message ->
                if (!ok) {
                    // Failed before a code could arrive: return to the number, say why.
                    awaitingCode = false
                    verificationId = null
                    secondsLeft = 0
                }
                result(ok, message)
            }
        )
    }

    if (!awaitingCode) {
        // Step 1 — country + number.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Text(country.label, style = BodyStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                DarkField(phone, { phone = it.filter(Char::isDigit).take(15) }, "Phone number", KeyboardType.Phone)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Send code", enabled = !busy) { sendCode(resend = false) }
    } else {
        // Step 2 — the code. This screen stays put until it succeeds or you go back.
        Text(
            "Code sent to +${country.dial} ${phone.filter(Char::isDigit)}",
            style = BodyStyle.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.semantics { contentType = ContentType.SmsOtpCode }
        ) {
            DarkField(code, { code = it.filter(Char::isDigit).take(6) }, "6-digit code", KeyboardType.Number)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (verificationId == null) "Sending the code…"
            else "Waiting for the code — it fills in by itself if your phone can read it.",
            style = BodyStyle.copy(fontSize = 12.sp),
            color = Slate
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (busy) "Verifying…" else "Verify",
            enabled = !busy && code.length == 6 && verificationId != null
        ) {
            onError(null)
            setBusy(true)
            PhoneAnchor.confirmCode(verificationId!!, code, result)
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = { if (secondsLeft == 0) { code = ""; verificationId = null; sendCode(resend = true) } },
            enabled = secondsLeft == 0 && !busy,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(
                if (secondsLeft > 0) "Resend code in ${secondsLeft}s" else "Resend code",
                style = BodyStyle.copy(fontSize = 14.sp),
                color = if (secondsLeft > 0) Slate else Copper
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    TextButton(
        onClick = {
            if (awaitingCode) {
                awaitingCode = false
                verificationId = null
                code = ""
                secondsLeft = 0
                onError(null)
            } else {
                onBack()
            }
        },
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Text(
            if (awaitingCode) "Change number" else "Back",
            style = BodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showPicker) {
        CountryPicker(current = country, onPick = { country = it; showPicker = false }, onDismiss = { showPicker = false })
    }
}

@Composable
private fun CountryPicker(current: Country, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) Countries.all
        else Countries.all.filter {
            it.name.lowercase().contains(q) || it.iso.lowercase().contains(q) || it.dial.startsWith(q.removePrefix("+"))
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Text("Country", style = TitleStyle.copy(fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            DarkField(query, { query = it }, "Search country or code", KeyboardType.Text)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(list, key = { it.iso }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(c) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            c.name,
                            style = BodyStyle.copy(fontSize = 15.sp),
                            color = if (c.iso == current.iso) Copper else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            c.label,
                            style = EyebrowStyle.copy(fontSize = 12.sp),
                            color = if (c.iso == current.iso) Copper else Slate
                        )
                    }
                }
            }
        }
    }
}

/** Countries survive rotation as "ISO:dial", so the picked country isn't lost mid-flow. */
private val CountrySaver = androidx.compose.runtime.saveable.Saver<Country, String>(
    save = { "${it.iso}:${it.dial}" },
    restore = { raw ->
        val parts = raw.split(":")
        if (parts.size == 2) Country(parts[0], parts[1]) else null
    }
)
