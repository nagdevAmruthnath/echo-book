package com.echobooks.app.ui.import

import android.net.Uri

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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
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
import com.echobooks.app.data.ParsedChapter
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
import com.echobooks.app.ui.viewmodel.ImportViewModel

object ImportArgs {
    var uri: Uri? = null
    var name: String = ""
}

@Composable
fun ImportScreen(
    vm: ImportViewModel,
    onCancel: () -> Unit,
    onDone: (Long) -> Unit
) {
    val s by vm.state.collectAsState()

    LaunchedEffect(s.done, s.bookId) {
        if (s.done) s.bookId?.let(onDone)
    }

    LaunchedEffect(Unit) {
        val uri = ImportArgs.uri
        if (uri != null && vm.state.value.phase == ImportViewModel.Phase.Idle) {
            vm.start(uri, ImportArgs.name)
        }
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
                GlassIconButton(Icons.Rounded.ArrowBack, onCancel)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Importing your book", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Reading, then narrating on-device", fontSize = 13.sp, color = TextSecondary)
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
                        Text(phaseTitle(s.phase), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(s.detail, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (s.phase) {
                    ImportViewModel.Phase.Done -> GlassProgressBar(1f, fill = Mint)
                    ImportViewModel.Phase.Error -> GlassProgressBar(0f, fill = ErrorRed)
                    ImportViewModel.Phase.Importing -> GlassProgressBar(0f)
                    else -> GlassProgressBar(
                        if (s.totalChapters > 0) s.narrated.toFloat() / s.totalChapters else 0f,
                        fill = Cyan
                    )
                }
            }

            if (s.phase == ImportViewModel.Phase.Error) {
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Could not import", fontWeight = FontWeight.SemiBold, color = ErrorRed)
                    Spacer(Modifier.height(4.dp))
                    Text(s.error ?: "Unknown error", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tip: plain .txt or .epub files import most reliably. Very recent Kindle files may use an unsupported compression.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
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
                        ImportChapterRow(
                            number = i + 1,
                            chapter = ch,
                            state = when {
                                s.phase == ImportViewModel.Phase.Done -> "done"
                                i < s.narrated -> "done"
                                s.phase == ImportViewModel.Phase.Narrating && i == s.narrated -> "narrating"
                                else -> "pending"
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(120.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (s.phase == ImportViewModel.Phase.Importing) "Reading the file…" else " ",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (s.phase != ImportViewModel.Phase.Done && s.phase != ImportViewModel.Phase.Error) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    GlassButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun phaseTitle(phase: ImportViewModel.Phase): String = when (phase) {
    ImportViewModel.Phase.Idle -> "Ready"
    ImportViewModel.Phase.Importing -> "Reading file"
    ImportViewModel.Phase.Narrating -> "Narrating"
    ImportViewModel.Phase.Done -> "Complete!"
    ImportViewModel.Phase.Error -> "Error"
}

@Composable
private fun ImportChapterRow(number: Int, chapter: ParsedChapter, state: String) {
    val (icon, tint) = if (state == "done") Icons.Rounded.Check to Mint else null to if (state == "narrating") Magenta else TextSecondary
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
            color = tint ?: TextSecondary,
            modifier = Modifier.width(22.dp)
        )
        Text(
            chapter.title,
            fontSize = 13.sp,
            color = TextPrimary, maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (state == "narrating") {
            Text("narrating…", fontSize = 11.sp, color = Magenta)
        } else if (icon != null) {
            Icon(
                icon, contentDescription = null, tint = tint,
                modifier = Modifier.size(18.dp).clip(CircleShape)
                    .background(tint.copy(alpha = 0.2f)).padding(2.dp)
            )
        }
    }
}