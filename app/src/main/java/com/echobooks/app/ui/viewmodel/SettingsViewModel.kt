package com.echobooks.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.data.ModelsCatalog
import com.echobooks.app.data.WebModel
import com.echobooks.app.tts.TtsVoice
import com.echobooks.app.tts.TtsVoices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as EchoBooksApp

    private val _apiKey = MutableStateFlow("")
    private val _model = MutableStateFlow("")
    private val _speed = MutableStateFlow(1f)
    private val _pitch = MutableStateFlow(1f)
    private val _voice = MutableStateFlow("")
    private val _nsfw = MutableStateFlow(false)
    private val _models = MutableStateFlow<List<WebModel>>(ModelsCatalog.forFilter(false))
    private val _voices = MutableStateFlow<List<TtsVoice>>(TtsVoices.all)
    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    private val _voiceProgress = MutableStateFlow<Float?>(null)
    private val _ttsReady = MutableStateFlow(false)

    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    val model: StateFlow<String> = _model.asStateFlow()
    val speed: StateFlow<Float> = _speed.asStateFlow()
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    val voice: StateFlow<String> = _voice.asStateFlow()
    val nsfw: StateFlow<Boolean> = _nsfw.asStateFlow()
    val models: StateFlow<List<WebModel>> = _models.asStateFlow()
    val voices: StateFlow<List<TtsVoice>> = _voices.asStateFlow()
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()
    val voiceProgress: StateFlow<Float?> = _voiceProgress.asStateFlow()
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKey.value = app.settings.apiKey.first()
            _model.value = app.settings.model.first()
            _speed.value = app.settings.speed.first()
            _pitch.value = app.settings.pitch.first()
            _voice.value = app.settings.voice.first()
            _nsfw.value = app.settings.nsfw.first()
            refreshModels()
            refreshDownloaded()
        }
    }

    fun setApiKey(value: String) {
        viewModelScope.launch { app.settings.setApiKey(value) }
        _apiKey.value = value
    }

    fun setModel(value: String) {
        viewModelScope.launch { app.settings.setModel(value) }
        _model.value = value
    }

    fun setNsfw(value: Boolean) {
        _nsfw.value = value
        refreshModels()
        viewModelScope.launch { app.settings.setNsfw(value) }
    }

    private fun refreshModels() {
        val list = ModelsCatalog.forFilter(_nsfw.value)
        _models.value = list
        if (list.none { it.id == _model.value } && list.isNotEmpty()) {
            _model.value = list.first().id
        }
    }

    fun setSpeed(value: Float) {
        _speed.value = value
        viewModelScope.launch {
            app.settings.setSpeed(value)
            app.tts.ensureReady(_voice.value.ifBlank { null }, value, _pitch.value, allowDownload = false)
        }
    }

    fun setPitch(value: Float) {
        _pitch.value = value
        viewModelScope.launch {
            app.settings.setPitch(value)
            app.tts.ensureReady(_voice.value.ifBlank { null }, _speed.value, value, allowDownload = false)
        }
    }

    fun setVoice(id: String) {
        _voice.value = id
        viewModelScope.launch {
            app.settings.setVoice(id)
            _voiceProgress.value = 0f
            val ok = app.tts.ensureReady(id.ifBlank { null }, _speed.value, _pitch.value) { p ->
                _voiceProgress.value = p
            }
            _voiceProgress.value = null
            _ttsReady.value = ok
            refreshDownloaded()
        }
    }

    fun loadVoices() {
        viewModelScope.launch {
            refreshDownloaded()
            _ttsReady.value = app.tts.ensureReady(
                _voice.value.ifBlank { null }, _speed.value, _pitch.value, allowDownload = false
            )
        }
    }

    private suspend fun refreshDownloaded() {
        _downloaded.value = TtsVoices.all.filter { app.tts.isDownloaded(it.id) }.map { it.id }.toSet()
    }
}