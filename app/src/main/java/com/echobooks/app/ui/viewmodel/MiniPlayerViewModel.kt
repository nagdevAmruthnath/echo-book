package com.echobooks.app.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.echobooks.app.audio.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MiniPlayerViewModel(application: Application) : AndroidViewModel(application) {

    data class MiniState(
        val visible: Boolean = false,
        val bookId: Long = 0,
        val title: String = "",
        val isPlaying: Boolean = false
    )

    private val _state = MutableStateFlow(MiniState())
    val state: StateFlow<MiniState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var polling = false

    init {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect() {
        val app = getApplication<Application>()
        try {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = withContext(Dispatchers.IO) {
                MediaController.Builder(app, token).buildAsync().get()
            }
            controller = c
            startPolling()
        } catch (_: Exception) {
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(500)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) {
            _state.value = _state.value.copy(visible = false)
            return
        }
        val meta = item.mediaMetadata
        val title = (meta.artist ?: meta.albumTitle)?.toString()?.takeIf { it.isNotBlank() } ?: ""
        val bookId = item.localConfiguration?.uri?.path
            ?.let { File(it).parentFile?.name?.toLongOrNull() }
            ?: 0L
        _state.value = MiniState(
            visible = bookId != 0L,
            bookId = bookId,
            title = title,
            isPlaying = c.isPlaying
        )
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    override fun onCleared() {
        super.onCleared()
        polling = false
        controller?.release()
        controller = null
    }
}