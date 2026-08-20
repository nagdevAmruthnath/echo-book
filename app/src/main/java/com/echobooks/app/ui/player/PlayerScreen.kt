package com.echobooks.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.data.Bookmark
import com.echobooks.app.share.BookShare
import com.echobooks.app.ui.components.GlassBackground
import com.echobooks.app.ui.components.GlassActionButton
import com.echobooks.app.ui.components.GlassButton
import com.echobooks.app.ui.components.GlassCard
import com.echobooks.app.ui.components.GlassIconButton
import com.echobooks.app.ui.components.GlassSlider
import com.echobooks.app.ui.components.GradientCover
import com.echobooks.app.ui.components.SectionTitle
import com.echobooks.app.ui.components.formatTime
import com.echobooks.app.ui.theme.Cyan
import com.echobooks.app.ui.theme.ErrorRed
import com.echobooks.app.ui.theme.Magenta
import com.echobooks.app.ui.theme.Mint
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import com.echobooks.app.ui.viewmodel.PlayerViewModel
import androidx.compose.material.icons.rounded.MusicNote
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    bookId: Long,
    onBack: () -> Unit
) {
    val s by vm.state.collectAsState()

    LaunchedEffect(bookId) { vm.load(bookId) }

    var showChapters by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var dragMs by remember { mutableStateOf<Float?>(null) }
    var readMode by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(18) }
    var readChapter by remember { mutableStateOf(-1) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun shareBook() {
        val b = s.book ?: return
        val dir = File(context.getDir("books", android.content.Context.MODE_PRIVATE), b.id.toString())
        scope.launch { BookShare.shareBook(context, b, s.chapters, dir) }
    }

    val book = s.book
    val dur = s.bookDurationMs.coerceAtLeast(1L)
    val pos = dragMs ?: s.bookPositionMs.toFloat().coerceIn(0f, dur.toFloat())
    val activeChapter = if (readChapter >= 0 && readChapter < s.chapters.size) readChapter else s.chapterIndex

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Now Listening", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(book?.title ?: "", fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassIconButton(
                    Icons.Rounded.MenuBook,
                    onClick = { readMode = !readMode; if (readMode) readChapter = s.chapterIndex },
                    tint = if (readMode) Cyan else Color.White,
                    size = 44.dp
                )
                GlassIconButton(
                    Icons.Rounded.Share,
                    onClick = { shareBook() },
                    tint = Color.White,
                    size = 44.dp,
                    contentDescription = "Share book"
                )
                GlassIconButton(
                    Icons.Rounded.Delete,
                    onClick = { showDeleteDialog = true },
                    tint = ErrorRed,
                    size = 44.dp
                )
            }

            Spacer(Modifier.height(8.dp))

            if (readMode) {
                ReadModeContent(
                    chapters = s.chapters,
                    chapterIndex = activeChapter,
                    fontSize = fontSize,
                    onFontSize = { fontSize = it },
                    onSelectChapter = { i ->
                        readChapter = i
                        vm.seekToChapter(i)
                    }
                )
            } else {

            book?.let {
                GradientCover(it.coverHue.toFloat(), it.title, Modifier.fillMaxWidth(), height = 170.dp)
                Spacer(Modifier.height(14.dp))
                Text(it.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 2)
                Text(it.author, fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (s.totalChapters > 0) "Chapter ${s.chapterIndex + 1} of ${s.totalChapters}" else "",
                fontSize = 13.sp,
                color = Cyan,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                s.chapterTitle,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))
            GlassSlider(
                value = pos,
                onValueChange = { dragMs = it },
                onValueChangeFinished = {
                    dragMs?.let { vm.seekToBook(it.toLong()) }
                    dragMs = null
                },
                valueRange = 0f..dur.toFloat()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(pos.toLong()), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(formatTime(dur), fontSize = 11.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Rounded.FastRewind, vm::prevChapter, size = 52.dp)
                GlassIconButton(
                    if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    vm::togglePlay,
                    size = 78.dp,
                    tint = Color.White
                )
                GlassIconButton(Icons.Rounded.FastForward, vm::nextChapter, size = 52.dp)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassActionButton(
                    Icons.Rounded.Stop,
                    onClick = { vm.stop() },
                    active = s.bookPositionMs > 0 || s.isPlaying
                )
                GlassActionButton(
                    Icons.Rounded.Speed,
                    onClick = { vm.setSpeed(nextSpeed(s.speed)) },
                    badge = "${speedLabel(s.speed)}x",
                    active = s.speed != 1f
                )
                GlassActionButton(
                    Icons.Rounded.Timer,
                    onClick = { vm.setSleepTimer(nextSleep(s.sleepMinutes)) },
                    active = s.sleepMinutes > 0,
                    badge = if (s.sleepMinutes > 0) "${s.sleepMinutes}m" else null
                )
                GlassActionButton(
                    Icons.Rounded.Bookmark,
                    onClick = { showBookmarkDialog = true },
                    active = s.bookmarks.isNotEmpty()
                )
                GlassActionButton(
                    Icons.Rounded.List,
                    onClick = { showChapters = true }
                )
            }

            if (s.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(s.error!!, fontSize = 12.sp, color = ErrorRed)
            }

            if (s.bookmarks.isNotEmpty()) {
                SectionTitle("Bookmarks")
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)
                ) {
                    items(s.bookmarks, key = { it.id }) { b ->
                        BookmarkRow(b) {
                            vm.jumpToBookmark(b)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
            }
        }
    }

    if (showChapters) {
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                "Chapters",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.fillMaxHeight(0.7f).padding(horizontal = 20.dp, vertical = 8.dp)) {
                items(s.chapters.size) { i ->
                    val ch = s.chapters[i]
                    val current = i == s.chapterIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable { vm.seekToChapter(i); showChapters = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (current) Cyan else TextSecondary,
                            modifier = Modifier.width(26.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, fontSize = 14.sp, color = if (current) Cyan else TextPrimary, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatTime(ch.durationMs), fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showBookmarkDialog) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Bookmark this moment", color = TextPrimary) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Note (optional)", color = TextSecondary) },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = TextSecondary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.addBookmark(label); showBookmarkDialog = false }) {
                    Text("Save", color = Violet)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete this book?", color = TextPrimary) },
            text = { Text("This removes the book, its audio and bookmarks from your device.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.deleteBook(); showDeleteDialog = false; onBack() }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ReadModeContent(
    chapters: List<com.echobooks.app.data.Chapter>,
    chapterIndex: Int,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    onSelectChapter: (Int) -> Unit
) {
    val ch = chapters.getOrNull(chapterIndex)

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (chapters.isNotEmpty()) "Chapter ${chapterIndex + 1} of ${chapters.size} · Reading" else "Reading",
                    fontSize = 13.sp,
                    color = Cyan,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    ch?.title ?: "", fontSize = 16.sp, color = TextPrimary,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassActionButton(
                    Icons.Rounded.Remove,
                    onClick = { onFontSize((fontSize - 2).coerceAtLeast(14)) },
                    active = fontSize > 14
                )
                Spacer(Modifier.width(8.dp))
                GlassActionButton(
                    Icons.Rounded.Add,
                    onClick = { onFontSize((fontSize + 2).coerceAtMost(30)) },
                    active = fontSize < 30
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val listState = rememberLazyListState()

        if (ch != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)
            ) {
                item {
                    Text(
                        ch.text,
                        fontSize = fontSize.sp,
                        color = TextPrimary,
                        lineHeight = (fontSize + 8).sp
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No text available for this chapter.", color = TextSecondary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassActionButton(
                Icons.Rounded.ChevronLeft,
                onClick = { onSelectChapter(((chapterIndex - 1).coerceAtLeast(0))) },
                active = chapterIndex > 0
            )
            GlassActionButton(
                Icons.Rounded.ChevronRight,
                onClick = { onSelectChapter(((chapterIndex + 1).coerceAtMost(chapters.size - 1))) },
                active = chapterIndex < chapters.size - 1
            )
        }
    }
}

@Composable
private fun BookmarkRow(b: Bookmark, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            Icons.Rounded.Bookmark,
            contentDescription = null,
            tint = Mint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(b.label, fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${b.chapterTitle} · ${formatTime(b.positionMs)}", fontSize = 11.sp, color = TextSecondary)
        }
        Text("Jump", fontSize = 12.sp, color = Cyan, fontWeight = FontWeight.SemiBold)
    }
}

private fun speedLabel(speed: Float): String = when (speed) {
    0.75f -> "0.75"
    1f -> "1.0"
    1.25f -> "1.25"
    1.5f -> "1.5"
    else -> "2.0"
}

private fun nextSpeed(speed: Float): Float = when (speed) {
    0.75f -> 1f
    1f -> 1.25f
    1.25f -> 1.5f
    1.5f -> 2f
    else -> 0.75f
}

private fun nextSleep(current: Int): Int = when (current) {
    0 -> 15
    15 -> 30
    30 -> 60
    else -> 0
}