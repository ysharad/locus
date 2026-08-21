package com.bitchat.android.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bitchat.android.connect.ConnectManager
import com.bitchat.android.connect.ConnectProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(onClose: () -> Unit) {
    val saved by ConnectManager.filters.collectAsState()
    val nearby by ConnectManager.nearby.collectAsState()
    val passed by ConnectManager.passed.collectAsState()

    var intents by remember { mutableStateOf(saved.intents) }
    var vibes by remember { mutableStateOf(saved.vibes) }
    var ageRange by remember { mutableStateOf(saved.ageMin.toFloat()..saved.ageMax.toFloat()) }

    val pending = ConnectManager.DiscoverFilters(intents, vibes, ageRange.start.toInt(), ageRange.endInclusive.toInt())
    val matchCount = remember(pending, nearby) {
        nearby.values.count { p -> !p.peerID.startsWith("demo-") && passes(p, pending) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(6.dp))
                Text("Filters", style = TitleStyle.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(
                    "RESET", style = EyebrowStyle.copy(fontSize = 11.sp), color = Slate,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        intents = emptySet(); vibes = emptySet(); ageRange = 18f..99f
                    }.padding(6.dp)
                )
            }

            if (passed.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Copper, RoundedCornerShape(16.dp))
                        .background(CopperTint).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Passed someone by mistake?", style = BodyStyle.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(3.dp))
                        Text("${passed.size} PASSED THIS SESSION", style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.08.em), color = Slate)
                    }
                    Text(
                        "UNDO", style = EyebrowStyle.copy(fontSize = 11.sp), color = OnCopper,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Copper).clickable { ConnectManager.resetPasses() }.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }

            SectionLabel("HERE TO…")
            ChipFlow(HERE_TO_OPTIONS, intents, accent = Copper) { intents = toggle(intents, it) }

            SectionLabel("VIBES · ANY OF")
            ChipFlow(VIBE_OPTIONS, vibes, accent = Jade) { vibes = toggle(vibes, it) }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("AGE", style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate, modifier = Modifier.weight(1f))
                Text("${ageRange.start.toInt()} – ${ageRange.endInclusive.toInt()}", style = EyebrowStyle.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurface)
            }
            RangeSlider(
                value = ageRange,
                onValueChange = { ageRange = it },
                valueRange = 18f..99f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Copper,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "FILTERS RUN ON THIS PHONE. EVERYONE IN RANGE STILL GETS YOUR CARD.",
                style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 0.06.em), color = Slate
            )
        }

        // Sticky CTA
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            Button(
                onClick = { ConnectManager.setFilters(pending); onClose() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = OnCopper)
            ) {
                Text(if (matchCount == 1) "Show 1 person" else "Show $matchCount people", style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

private fun passes(p: ConnectProfile, f: ConnectManager.DiscoverFilters): Boolean {
    if (f.intents.isNotEmpty() && p.hereTo !in f.intents) return false
    if (f.vibes.isNotEmpty() && p.vibes.none { it in f.vibes }) return false
    val age = p.age
    if (age != null && (age < f.ageMin || age > f.ageMax)) return false
    return true
}

private fun toggle(set: Set<String>, item: String): Set<String> =
    if (item in set) set - item else set + item

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, style = EyebrowStyle.copy(letterSpacing = 0.16.em), color = Slate)
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(options: List<String>, selected: Set<String>, accent: Color, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val on = opt in selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .border(1.dp, if (on) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .background(if (on) (if (accent == Jade) Color(0xFF17211C) else CopperTint) else Color.Transparent)
                    .clickable { onToggle(opt) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(opt, style = BodyStyle.copy(fontSize = 13.5.sp), color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
