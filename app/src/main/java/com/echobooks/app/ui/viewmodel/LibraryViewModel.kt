package com.echobooks.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.data.Book
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EchoBooksApp

    val books: StateFlow<List<Book>> = app.database.bookDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasApiKey: StateFlow<Boolean> = app.settings.apiKey
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun delete(book: Book) {
        viewModelScope.launch {
            app.database.chapterDao().deleteForBook(book.id)
            app.database.bookmarkDao().deleteForBook(book.id)
            app.database.bookDao().delete(book)
            File(app.getDir("books", android.content.Context.MODE_PRIVATE), book.id.toString())
                .deleteRecursively()
        }
    }
}