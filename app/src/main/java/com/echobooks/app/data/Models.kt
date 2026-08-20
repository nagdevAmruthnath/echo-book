package com.echobooks.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val genre: String,
    val brief: String,
    val lengthMin: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val coverHue: Int = 265,
    val chapterCount: Int = 0,
    val progressItem: Int = 0,
    val progressMs: Long = 0,
    val progressFraction: Float = 0f,
    val durationMs: Long = 0,
    val completed: Boolean = false
)

@Entity(tableName = "chapters", indices = [Index(value = ["bookId"])])
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    @ColumnInfo(name = "chapter_index") val index: Int,
    val title: String,
    val text: String,
    val segments: String,
    val durationMs: Long
)

@Serializable
data class SegmentInfo(
    val file: String,
    val d: Long
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val itemIndex: Int,
    val positionMs: Long,
    val chapterTitle: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)