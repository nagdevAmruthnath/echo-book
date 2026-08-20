package com.echobooks.app.generation

import com.echobooks.app.llm.OutlineChapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GenerationState {
    enum class Phase { Idle, Outline, Writing, Narrating, Done, Error }

    data class Progress(
        val active: Boolean = false,
        val phase: Phase = Phase.Idle,
        val bookId: Long? = null,
        val title: String = "",
        val chapters: List<OutlineChapter> = emptyList(),
        val totalChapters: Int = 0,
        val written: Int = 0,
        val narrated: Int = 0,
        val narratingSegment: Int = 0,
        val narratingSegmentTotal: Int = 0,
        val detail: String = "",
        val error: String? = null,
        val done: Boolean = false
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    fun begin() {
        _progress.value = Progress(active = true, phase = Phase.Outline, detail = "Designing your story…")
    }

    fun update(block: (Progress) -> Progress) {
        _progress.update(block)
    }

    fun end() {
        _progress.value = Progress()
    }
}