package com.echobooks.app.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.echobooks.app.data.ModelsCatalog
import com.echobooks.app.ui.components.GlassBackground
import com.echobooks.app.ui.components.GlassButton
import com.echobooks.app.ui.components.GlassCard
import com.echobooks.app.ui.components.GlassIconButton
import com.echobooks.app.ui.components.GlassSlider
import com.echobooks.app.ui.components.GlassTextField
import com.echobooks.app.ui.components.SectionTitle
import com.echobooks.app.ui.theme.Cyan
import com.echobooks.app.ui.theme.Mint
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import com.echobooks.app.tts.TtsVoice
import com.echobooks.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val apiKey by vm.apiKey.collectAsState()
    val model by vm.model.collectAsState()
    val nsfw by vm.nsfw.collectAsState()
    val models by vm.models.collectAsState()
    val speed by vm.speed.collectAsState()
    val pitch by vm.pitch.collectAsState()
    val voice by vm.voice.collectAsState()
    val voices by vm.voices.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val voiceProgress by vm.voiceProgress.collectAsState()
    val ttsReady by vm.ttsReady.collectAsState()

    var keyDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf("") }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apiKey) { keyDraft = apiKey }
    LaunchedEffect(model) { modelDraft = model }
    LaunchedEffect(Unit) { vm.loadVoices() }
    LaunchedEffect(savedMsg) {
        if (savedMsg != null) {
            delay(1800)
            savedMsg = null
        }
    }

    GlassBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Your key, your voice", fontSize = 13.sp, color = TextSecondary)
                }
            }

            SectionTitle("OpenRouter")
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = Violet, modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("API key", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "sk-or-…",
                    singleLine = true,
                    password = true,
                    keyboardType = KeyboardType.Password
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Get a free key at openrouter.ai — stored only on this device.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    "Save API key",
                    onClick = {
                        vm.setApiKey(keyDraft)
                        savedMsg = if (keyDraft.isBlank()) "API key cleared" else "API key saved"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionTitle("Model")
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow mature content", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            "Enables uncensored models for adult-friendly writing. Off by default.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = nsfw,
                        onCheckedChange = { vm.setNsfw(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Mint,
                            checkedTrackColor = Mint.copy(alpha = 0.35f)
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (nsfw) "Model" else "Free models",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.forEach { m ->
                        GlassModelRow(m, selected = m.id == model) {
                            vm.setModel(m.id)
                            savedMsg = "${m.label} selected"
                        }
                    }
                }
                if (nsfw) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mature models may produce explicit content. Use at your own discretion.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Custom model", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (nsfw) "openrouter/model-id (any, incl. uncensored)" else "openrouter/model-id",
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    "Use custom model",
                    onClick = {
                        vm.setModel(modelDraft)
                        savedMsg = if (modelDraft.isBlank()) "Custom model cleared" else "Custom model saved"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionTitle("Voice")
            GlassCard {
                Text("Narration voice", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .clickable { voiceMenuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            currentVoiceLabel(voice, voices),
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = voiceMenuOpen,
                        onDismissRequest = { voiceMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default — Amy (US)") },
                            onClick = { vm.setVoice(""); voiceMenuOpen = false }
                        )
                        voices.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(voiceLabel(v)) },
                                onClick = { vm.setVoice(v.id); voiceMenuOpen = false }
                            )
                        }
                    }
                }
                when {
                    voiceProgress != null -> {
                        val progress = voiceProgress ?: 0f
                        Spacer(Modifier.height(8.dp))
                        val pct = (progress * 100).toInt()
                        Text(
                            "Downloading voice pack… $pct% (one-time, ${selectedVoice(voice, voices).downloadMb} MB)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { voiceProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Cyan,
                            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
                        )
                    }

                    !downloaded.contains(selectedVoice(voice, voices).id) -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Downloads on first use (~${selectedVoice(voice, voices).downloadMb} MB, one-time).",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    !ttsReady -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Loading voice…",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Speed", color = TextSecondary, fontSize = 13.sp)
                GlassSlider(
                    value = speed,
                    onValueChange = vm::setSpeed,
                    valueRange = 0.5f..2f,
                    steps = 29
                )
                Spacer(Modifier.height(6.dp))
                Text("Pitch", color = TextSecondary, fontSize = 13.sp)
                GlassSlider(
                    value = pitch,
                    onValueChange = vm::setPitch,
                    valueRange = 0.5f..2f,
                    steps = 29
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Speech is synthesized fully on-device with open-source neural voices " +
                        "(sherpa-onnx + Piper). Voice packs download once and work offline.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            SectionTitle("Privacy")
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Mint, modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Everything stays on your device", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            "Your books, audio, progress and bookmarks are stored locally. " +
                                "The only network call is to OpenRouter when generating text.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            }
            AnimatedVisibility(
                visible = savedMsg != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Spacer(Modifier.height(30.dp))
                Row(
                    Modifier
                        .padding(bottom = 26.dp)
                        .background(Color.White.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .border(1.dp, Violet.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Mint, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        savedMsg ?: "Saved",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassModelRow(m: com.echobooks.app.data.WebModel, selected: Boolean, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (selected) 0.10f else 0.05f), shape)
            .border(1.dp, if (selected) Violet else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) Violet else TextPrimary)
                if (m.free) {
                    Spacer(Modifier.width(6.dp))
                    Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Mint,
                        modifier = Modifier.background(Mint.copy(alpha = 0.15f), shape).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
            Text(m.id, fontSize = 11.sp, color = TextSecondary, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${m.context} context", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

private fun currentVoiceLabel(voice: String, voices: List<TtsVoice>): String {
    if (voice.isBlank()) return "Default — Amy (US)"
    val v = voices.firstOrNull { it.id == voice }
    return v?.let { voiceLabel(it) } ?: voice
}

private fun selectedVoice(voice: String, voices: List<TtsVoice>): TtsVoice =
    voices.firstOrNull { it.id == voice } ?: voices.first()

private fun voiceLabel(v: TtsVoice): String = "${v.label} — ${v.detail}"