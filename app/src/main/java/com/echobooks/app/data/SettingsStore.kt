package com.echobooks.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "echobooks_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val VOICE = stringPreferencesKey("voice")
        val NSFW = booleanPreferencesKey("nsfw")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }
    val model: Flow<String> = context.dataStore.data.map { it[Keys.MODEL] ?: DEFAULT_MODEL }
    val speed: Flow<Float> = context.dataStore.data.map { it[Keys.SPEED] ?: 1.0f }
    val pitch: Flow<Float> = context.dataStore.data.map { it[Keys.PITCH] ?: 1.0f }
    val voice: Flow<String> = context.dataStore.data.map { it[Keys.VOICE] ?: "" }
    val nsfw: Flow<Boolean> = context.dataStore.data.map { it[Keys.NSFW] ?: false }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[Keys.API_KEY] = value.trim() }
    suspend fun setModel(value: String) = context.dataStore.edit { it[Keys.MODEL] = value.trim() }
    suspend fun setSpeed(value: Float) = context.dataStore.edit { it[Keys.SPEED] = value }
    suspend fun setPitch(value: Float) = context.dataStore.edit { it[Keys.PITCH] = value }
    suspend fun setVoice(value: String) = context.dataStore.edit { it[Keys.VOICE] = value }
    suspend fun setNsfw(value: Boolean) = context.dataStore.edit { it[Keys.NSFW] = value }
    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = value }

    companion object {
        const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct"
        val PRESET_MODELS = listOf(
            "meta-llama/llama-3.3-70b-instruct",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-haiku",
            "mistralai/mistral-small-3.1-24b-instruct"
        )
    }
}