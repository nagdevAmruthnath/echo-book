package com.echobooks.app

import android.app.Application
import com.echobooks.app.data.AppDatabase
import com.echobooks.app.data.SettingsStore
import com.echobooks.app.llm.ChapterGenerator
import com.echobooks.app.llm.OpenRouterClient
import com.echobooks.app.tts.TtsEngine

class EchoBooksApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val settings by lazy { SettingsStore(this) }
    val llm by lazy { OpenRouterClient() }
    val generator by lazy { ChapterGenerator(llm) }
    val tts by lazy { TtsEngine(this) }
}