package com.echobooks.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.data.Book
import com.echobooks.app.generation.GenerationState
import com.echobooks.app.share.BookShare
import com.echobooks.app.ui.components.BookCard
import com.echobooks.app.ui.components.GlassBackground
import com.echobooks.app.ui.components.GlassButton
import com.echobooks.app.ui.components.GlassCard
import com.echobooks.app.ui.components.GlassIconButton
import com.echobooks.app.ui.components.GlassProgressBar
import com.echobooks.app.ui.components.MiniPlayer
import com.echobooks.app.ui.theme.ErrorRed
import com.echobooks.app.ui.theme.Ink
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import com.echobooks.app.ui.viewmodel.LibraryViewModel
import com.echobooks.app.ui.viewmodel.MiniPlayerViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    miniVm: MiniPlayerViewModel,
    onOpenBook: (Long) -> Unit,
    onCreateBook: () -> Unit,
    onImportBook: (Uri, String) -> Unit,
    onSettings: () -> Unit
) {
    val books by vm.books.collectAsState()
    val hasKey by vm.hasApiKey.collectAsState()
    val mini by miniVm.state.collectAsState()
    val gen by GenerationState.progress.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as EchoBooksApp

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                } ?: "imported.txt"
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            com.echobooks.app.ui.import.ImportArgs.uri = uri
            com.echobooks.app.ui.import.ImportArgs.name = name
            onImportBook(uri, name)
        }
    }

    fun share(book: Book) {
        scope.launch {
            val chapters = app.database.chapterDao().getForBook(book.id)
            val dir = File(app.getDir("books", Context.MODE_PRIVATE), book.id.toString())
            BookShare.shareBook(context, book, chapters, dir)
        }
    }

    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    GlassBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Library",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "Your audiobooks",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    GlassIconButton(Icons.Rounded.Settings, onSettings)
                }

            if (!hasKey) {
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Violet,
                            modifier = Modifier.width(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Connect your OpenRouter key",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Books are generated on-device; only the API key connects to OpenRouter.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Set up now", onClick = onSettings, modifier = Modifier.fillMaxWidth())
                }
            }

            if (gen.active) {
                GenerationBanner(gen, onCancel = {
                    com.echobooks.app.generation.GenerationService.cancel(context)
                })
            }

            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your library is empty", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Describe a story and EchoBooks will write it,\nnarrate it, and bring it into your library.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 155.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                    contentPadding = PaddingValues(bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book,
                            onClick = { onOpenBook(book.id) },
                            onShare = ::share,
                            onDelete = { deleteTarget = it }
                        )
                    }
                }
            }
        }
        }

        if (deleteTarget != null) {
            val target = deleteTarget!!
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = Ink,
                titleContentColor = TextPrimary,
                textContentColor = TextPrimary,
                title = { Text("Delete this book?") },
                text = { Text("“${target.title}” and all its chapters and audio will be permanently removed.", color = TextPrimary) },
                confirmButton = {
                    TextButton(onClick = { vm.delete(target); deleteTarget = null }) {
                        Text("Delete", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        if (mini.visible) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 22.dp, top = 8.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    MiniPlayer(
                        title = mini.title,
                        isPlaying = mini.isPlaying,
                        onToggle = miniVm::toggle,
                        onOpen = { onOpenBook(mini.bookId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerationBanner(gen: GenerationState.Progress, onCancel: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Violet,
                modifier = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (gen.title.isNotBlank()) gen.title else "Creating a new book",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(gen.detail.ifBlank { "Working…" }, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
            if (gen.phase == GenerationState.Phase.Error) {
                GlassIconButton(
                    Icons.Rounded.Close,
                    onClick = onCancel,
                    size = 36.dp,
                    tint = com.echobooks.app.ui.theme.ErrorRed,
                    contentDescription = "Discard failed generation"
                )
            } else {
                GlassIconButton(
                    Icons.Rounded.Close,
                    onClick = onCancel,
                    size = 36.dp,
                    tint = TextSecondary,
                    contentDescription = "Cancel generation"
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (gen.phase == GenerationState.Phase.Error) {
            GlassProgressBar(0f, fill = com.echobooks.app.ui.theme.ErrorRed)
            Spacer(Modifier.height(6.dp))
            Text(
                "Generation failed — tap to discard the partial book",
                fontSize = 12.sp,
                color = com.echobooks.app.ui.theme.ErrorRed
            )
        } else {
            val frac = if (gen.totalChapters > 0) gen.narrated.toFloat() / gen.totalChapters else 0f
            GlassProgressBar(frac, fill = com.echobooks.app.ui.theme.Cyan)
            Spacer(Modifier.height(6.dp))
            Text(
                "Generating in the background · ${gen.narrated}/${gen.totalChapters} chapters narrated",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}