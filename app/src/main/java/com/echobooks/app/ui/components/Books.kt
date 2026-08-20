package com.echobooks.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.data.Book
import com.echobooks.app.ui.theme.Cyan
import com.echobooks.app.ui.theme.ErrorRed
import com.echobooks.app.ui.theme.Mint
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary

@Composable
fun MiniPlayer(
    title: String,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    GlassCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Now Playing",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            GlassActionButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = onToggle,
                active = true,
                size = 52.dp,
                activeTint = Color.White,
                badge = null
            )
        }
    }
}

@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onShare: (Book) -> Unit,
    onDelete: ((Book) -> Unit)? = null
) {
    GlassCard(onClick = onClick) {
        Box {
            GradientCover(book.coverHue.toFloat(), book.title, Modifier.fillMaxWidth(), height = 105.dp)
            GlassIconButton(
                icon = Icons.Rounded.Share,
                onClick = { onShare(book) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                size = 36.dp,
                tint = Color.White,
                contentDescription = "Share ${book.title}"
            )
            if (onDelete != null) {
                GlassIconButton(
                    icon = Icons.Rounded.Delete,
                    onClick = { onDelete(book) },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    size = 36.dp,
                    tint = ErrorRed,
                    contentDescription = "Delete ${book.title}"
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            book.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            book.author,
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        if (book.completed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Completed", fontSize = 11.sp, color = Mint, fontWeight = FontWeight.SemiBold)
            }
        } else if (book.durationMs > 0) {
            GlassProgressBar(book.progressFraction, fill = Cyan)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("${book.chapterCount} chapters")
                if (book.durationMs > 0) append(" · ${formatTime(book.durationMs)}")
            },
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun BookCardRow(book: Book, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientCover(book.coverHue.toFloat(), book.title, Modifier.width(72.dp), height = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${book.chapterCount} chapters" +
                        if (book.durationMs > 0) " · ${formatTime(book.durationMs)}" else "",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}