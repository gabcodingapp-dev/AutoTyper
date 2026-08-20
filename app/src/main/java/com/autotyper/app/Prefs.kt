package com.autotyper.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    private const val NAME = "autotyper"

    private const val KEY_WPM = "wpm"
    private const val KEY_HUMANITY = "humanity"
    private const val KEY_SNIPPETS = "snippets"
    private const val KEY_LAST_TEXT = "last_text"
    private const val KEY_PANEL_X = "panel_x"
    private const val KEY_PANEL_Y = "panel_y"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- typing config ----
    fun getWpm(ctx: Context): Int = sp(ctx).getInt(KEY_WPM, 60)
    fun setWpm(ctx: Context, v: Int) = sp(ctx).edit().putInt(KEY_WPM, v).apply()

    fun getHumanity(ctx: Context): Float = sp(ctx).getFloat(KEY_HUMANITY, 0.5f)
    fun setHumanity(ctx: Context, v: Float) = sp(ctx).edit().putFloat(KEY_HUMANITY, v).apply()

    // ---- last typed text (used by the floating panel's play button) ----
    fun getLastText(ctx: Context): String = sp(ctx).getString(KEY_LAST_TEXT, "") ?: ""
    fun setLastText(ctx: Context, v: String) = sp(ctx).edit().putString(KEY_LAST_TEXT, v).apply()

    // ---- saved snippets ----
    fun getSnippets(ctx: Context): List<String> {
        val raw = sp(ctx).getString(KEY_SNIPPETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSnippet(ctx: Context, text: String) {
        val list = getSnippets(ctx).toMutableList()
        list.removeAll { it == text }
        list.add(0, text)
        while (list.size > 20) list.removeAt(list.size - 1)
        sp(ctx).edit().putString(KEY_SNIPPETS, JSONArray(list).toString()).apply()
    }

    fun removeSnippet(ctx: Context, text: String) {
        val list = getSnippets(ctx).filter { it != text }
        sp(ctx).edit().putString(KEY_SNIPPETS, JSONArray(list).toString()).apply()
    }

    // ---- floating panel position ----
    fun getPanelX(ctx: Context, default: Int): Int = sp(ctx).getInt(KEY_PANEL_X, default)
    fun getPanelY(ctx: Context, default: Int): Int = sp(ctx).getInt(KEY_PANEL_Y, default)
    fun setPanelPos(ctx: Context, x: Int, y: Int) =
        sp(ctx).edit().putInt(KEY_PANEL_X, x).putInt(KEY_PANEL_Y, y).apply()
}
