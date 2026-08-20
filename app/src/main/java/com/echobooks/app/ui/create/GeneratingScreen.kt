package com.echobooks.app.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.generation.GenerationState
import com.echobooks.app.ui.components.GlassBackground
import com.echobooks.app.ui.components.GlassButton
import com.echobooks.app.ui.components.GlassCard
import com.echobooks.app.ui.components.GlassIconButton
import com.echobooks.app.ui.components.GlassProgressBar
import com.echobooks.app.ui.theme.Cyan
import com.echobooks.app.ui.theme.ErrorRed
import com.echobooks.app.ui.theme.Magenta
import com.echobooks.app.ui.theme.Mint
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import com.echobooks.app.ui.viewmodel.GenerationViewModel

@Composable
fun GeneratingScreen(
    vm: GenerationViewModel,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
    onDone: (Long) -> Unit,
    onListen: (Long) -> Unit
) {
    val s by vm.state.collectAsState()

    LaunchedEffect(s.done, s.bookId) {
        if (s.done) s.bookId?.let(onDone)
    }

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, onLeave)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Creating your audiobook", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("One chapter at a time", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(10.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Violet,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            phaseTitle(s.phase),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(s.detail, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (s.phase == GenerationState.Phase.Outline) {
                    GlassProgressBar(0f)
                } else if (s.phase == GenerationState.Phase.Done) {
                    GlassProgressBar(1f, fill = Mint)
                } else if (s.phase == GenerationState.Phase.Error) {
                    GlassProgressBar(0f, fill = ErrorRed)
                } else {
                    val frac = if (s.totalChapters > 0 && s.narratingSegmentTotal > 0) {
                        (s.narrated + s.narratingSegment.toFloat() / s.narratingSegmentTotal) / s.totalChapters
                    } else if (s.totalChapters > 0) {
                        s.narrated.toFloat() / s.totalChapters
                    } else 0f
                    GlassProgressBar(frac.coerceIn(0f, 1f), fill = Cyan)
                }
            }

            if (s.phase == GenerationState.Phase.Error) {
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Something went wrong", fontWeight = FontWeight.SemiBold, color = ErrorRed)
                    Spacer(Modifier.height(4.dp))
                    Text(s.error ?: "Unknown error", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Back", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
            }

            if (s.chapters.isNotEmpty()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(s.chapters) { i, ch ->
                        ChapterStatusRow(
                            number = i + 1,
                            title = ch.title,
                            status = when {
                                s.phase == GenerationState.Phase.Done -> "done"
                                i < s.narrated -> "done"
                                s.phase == GenerationState.Phase.Narrating && i == s.narrated -> "narrating"
                                i == s.written && s.phase == GenerationState.Phase.Writing -> "writing"
                                i < s.written -> "text"
                                else -> "pending"
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(120.dp))
            }

            if (s.phase != GenerationState.Phase.Done && s.phase != GenerationState.Phase.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "It keeps running in the background — you can leave this screen any time.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (s.narrated > 0 && s.bookId != null) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        "Start listening (${s.narrated} chapters ready)",
                        onClick = { s.bookId?.let(onListen) },
                        modifier = Modifier.fillMaxWidth(),
                        leading = Icons.Rounded.Check
                    )
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    GlassButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun phaseTitle(phase: GenerationState.Phase): String = when (phase) {
    GenerationState.Phase.Idle -> "Ready"
    GenerationState.Phase.Outline -> "Designing your story"
    GenerationState.Phase.Writing -> "Writing"
    GenerationState.Phase.Narrating -> "Narrating"
    GenerationState.Phase.Done -> "Complete!"
    GenerationState.Phase.Error -> "Error"
}

@Composable
private fun ChapterStatusRow(number: Int, title: String, status: String) {
    val (icon, tint) = when (status) {
        "done" -> Icons.Rounded.Check to Mint
        "writing", "narrating" -> null to Magenta
        "text" -> null to Cyan
        else -> null to TextSecondary
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$number",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.width(22.dp)
        )
        Text(title, fontSize = 13.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
        if (status == "writing") {
            Text("writing…", fontSize = 11.sp, color = Magenta)
        } else if (status == "narrating") {
            Text("narrating…", fontSize = 11.sp, color = Magenta)
        } else if (status == "text") {
            Text("text ready", fontSize = 11.sp, color = Cyan)
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.2f))
                    .padding(2.dp)
            )
        }
    }
}