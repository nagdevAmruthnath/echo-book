package com.echobooks.app.ui.create

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.llm.BookSpec
import com.echobooks.app.speech.SpeechToText
import com.echobooks.app.ui.components.GlassBackground
import com.echobooks.app.ui.components.GlassButton
import com.echobooks.app.ui.components.GlassCard
import com.echobooks.app.ui.components.GlassChip
import com.echobooks.app.ui.components.GlassIconButton
import com.echobooks.app.ui.components.GlassSlider
import com.echobooks.app.ui.components.GlassTextField
import com.echobooks.app.ui.components.SectionTitle
import com.echobooks.app.ui.theme.Magenta
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val GENRES = listOf(
    "Fantasy", "Sci-Fi", "Mystery", "Romance", "Thriller",
    "Adventure", "Horror", "Self-Help", "Biography", "History"
)

@Composable
fun CreateScreen(
    onBack: () -> Unit,
    onStart: (BookSpec) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) }
    var brief by remember { mutableStateOf("") }
    var titleHint by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var lengthMin by remember { mutableIntStateOf(60) }
    var listening by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speech = remember { SpeechToText() }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Mic permission is needed for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    fun startListening() {
        if (listening) return
        listening = true
        val base = brief
        scope.launch {
            val result = speech.listen(context,
                onPartial = { partial -> brief = base + " " + partial },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )
            if (result.isNotBlank()) brief = base.trim() + " " + result.trim()
            listening = false
        }
    }

    val canStart = brief.isNotBlank()

    GlassBackground {
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
                    Text("Create a book", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Tell EchoBooks what to write", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(10.dp))

GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassChip("Describe it", mode == 0, onClick = { mode = 0 })
                    GlassChip("Use a form", mode == 1, onClick = { mode = 1 })
                }
                Spacer(Modifier.height(14.dp))

                if (mode == 0) {
                    Text(
                        "Speak or type what you want to listen to. Be as detailed as you like.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Box {
                        GlassTextField(
                            value = brief,
                            onValueChange = { brief = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "e.g. A cozy mystery set in a seaside lighthouse, where the new keeper finds a locked diary that predicts storms before they arrive…",
                            minHeight = 150.dp
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            GlassIconButton(
                                icon = Icons.Rounded.Mic,
                                onClick = {
                                    if (listening) return@GlassIconButton
                                    if (android.content.pm.PackageManager.PERMISSION_GRANTED ==
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        )
                                    ) {
                                        startListening()
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                tint = if (listening) Magenta else Violet,
                                size = 46.dp
                            )
                        }
                    }
                    if (listening) {
                        Text(
                            "Listening… speak now",
                            fontSize = 13.sp,
                            color = Magenta,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(
                        value = titleHint,
                        onValueChange = { titleHint = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Optional title hint",
                        singleLine = true
                    )
                } else {
                    Text("Genre", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GENRES.forEach { g ->
                            GlassChip(g, genre == g, onClick = { genre = if (genre == g) "" else g })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Or type any genre",
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(
                        value = author,
                        onValueChange = { author = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Narrator / author style, e.g. \"warm, poetic, like a fireside storyteller\"",
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(
                        value = brief,
                        onValueChange = { brief = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "What should the story be about?",
                        minHeight = 130.dp
                    )
                }
            }

            SectionTitle("Length")
            GlassCard {
                Text(
                    formatLength(lengthMin),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    "≈ ${estimatedChapters(lengthMin)} chapters · ~10 min each",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                GlassSlider(
                    value = lengthMin.toFloat(),
                    onValueChange = { lengthMin = it.roundToInt().coerceIn(15, 600) },
                    valueRange = 15f..600f,
                    steps = 38
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("15 min", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Text("10 h", fontSize = 11.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassButton(
                text = if (mode == 0) "Write my audiobook" else "Generate this book",
                onClick = {
                    onStart(
                        BookSpec(
                            titleHint = titleHint,
                            author = author,
                            genre = genre,
                            brief = brief,
                            lengthMin = lengthMin
                        )
                    )
                },
                enabled = canStart,
                leading = Icons.Rounded.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

fun formatLength(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return buildString {
        if (h > 0) append("$h h")
        if (h > 0 && m > 0) append(" ")
        if (m > 0) append("$m min")
        if (h == 0 && m == 0) append("15 min")
    }
}

fun estimatedChapters(minutes: Int): Int = (minutes / 10).coerceAtLeast(1)