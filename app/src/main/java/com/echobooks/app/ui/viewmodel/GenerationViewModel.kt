package com.echobooks.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echobooks.app.generation.GenerationService
import com.echobooks.app.generation.GenerationState
import com.echobooks.app.llm.BookSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GenerationViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val phase: GenerationState.Phase = GenerationState.Phase.Idle,
        val bookId: Long? = null,
        val chapters: List<com.echobooks.app.llm.OutlineChapter> = emptyList(),
        val totalChapters: Int = 0,
        val written: Int = 0,
        val narrated: Int = 0,
        val narratingSegment: Int = 0,
        val narratingSegmentTotal: Int = 0,
        val detail: String = "",
        val error: String? = null,
        val done: Boolean = false
    )

    /** Mirrors the background service's progress so the Generating screen stays live. */
    val state: StateFlow<State> = GenerationState.progress
        .map { p ->
            State(
                phase = p.phase,
                bookId = p.bookId,
                chapters = p.chapters,
                totalChapters = p.totalChapters,
                written = p.written,
                narrated = p.narrated,
                narratingSegment = p.narratingSegment,
                narratingSegmentTotal = p.narratingSegmentTotal,
                detail = p.detail,
                error = p.error,
                done = p.done
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun start(spec: BookSpec) {
        GenerationService.start(getApplication(), spec)
    }

    fun cancel() {
        GenerationService.cancel(getApplication())
    }
}