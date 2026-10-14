package com.vastutalks.app.data.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The language Ananya replies in, and the agent screens are shown in. */
enum class AgentLanguage { ENGLISH, HINDI }

/**
 * The chosen [AgentLanguage], English unless the user switched to Hindi
 * (on the chat or call screen). Saved on the phone. Compose state, so
 * screens that read it redraw when it changes.
 */
object AgentLanguagePref {
    private const val PREFS = "agent_language"
    private const val KEY = "language"
    private var prefs: android.content.SharedPreferences? = null

    var current by mutableStateOf(AgentLanguage.ENGLISH)
        private set

    val isHindi: Boolean get() = current == AgentLanguage.HINDI

    /** Reads the saved choice; call once at app start. */
    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        current = runCatching { AgentLanguage.valueOf(p.getString(KEY, null) ?: "") }.getOrDefault(AgentLanguage.ENGLISH)
    }

    fun set(language: AgentLanguage) {
        current = language
        prefs?.edit()?.putString(KEY, language.name)?.apply()
    }
}

/** The English or Hindi version of a piece of UI text, for the chosen language. */
fun tr(english: String, hindi: String): String = if (AgentLanguagePref.isHindi) hindi else english
