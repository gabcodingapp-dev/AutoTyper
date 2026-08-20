package com.autotyper.app

import kotlin.random.Random

/** Typing parameters set by the user. */
data class TypeConfig(
    val wpm: Int = 60,
    val humanity: Float = 0.5f // 0f = clean/robot, 1f = very human
)

enum class ActionType { COMMIT, ENTER, TAB, BACKSPACE }

data class TypeAction(
    val type: ActionType,
    val char: Char? = null,
    val delayMs: Long
)

/**
 * Builds a realistic, human-like typing plan from a string of text.
 * Produces a list of actions (commit / backspace / enter / tab) each with
 * a randomized inter-keystroke delay. Higher `humanity` = more jitter,
 * more typos and more hesitation pauses.
 */
class TypingEngine(
    private val config: TypeConfig,
    private val random: Random = Random(System.currentTimeMillis())
) {

    // chars-per-second = wpm * 5 / 60, so per-char delay = 12000 / wpm ms
    private val baseDelay: Long = (12000f / config.wpm.coerceAtLeast(1)).toLong().coerceAtLeast(15L)

    private fun jittered(): Long {
        // humanity widens the variance band (12% .. 57%)
        val variance = 0.12f + config.humanity * 0.45f
        val factor = 1f + (random.nextFloat() * 2f - 1f) * variance
        return (baseDelay * factor).toLong().coerceAtLeast(10L)
    }

    private fun typoChance(): Float = config.humanity * 0.04f // up to ~4% at full humanity

    fun build(text: String): List<TypeAction> {
        val actions = ArrayList<TypeAction>(text.length * 2)
        var i = 0
        for (c in text) {
            i++
            var delay = jittered()

            // warm-up: first ~9 keystrokes are noticeably slower
            if (i <= 9) delay = (delay * 1.55f).toLong()

            // rhythm break after punctuation
            if (c == '.' || c == ',' || c == '?' || c == '!' || c == ';' || c == ':') {
                delay += 120L + random.nextInt(180)
            }

            // occasional mid-sentence "thinking" pause
            if (config.humanity > 0.3f && i > 12 && random.nextFloat() < 0.008f) {
                delay += 300L + random.nextInt(500)
            }

            // typo + self-correction
            if (c.isLetterOrDigit() && random.nextFloat() < typoChance()) {
                actions += TypeAction(ActionType.COMMIT, typoFor(c), delay)
                actions += TypeAction(ActionType.BACKSPACE, null, 60L + random.nextInt(120))
                delay = jittered()
            }

            actions += when (c) {
                '\n' -> TypeAction(ActionType.ENTER, null, delay)
                '\t' -> TypeAction(ActionType.TAB, null, delay)
                else -> TypeAction(ActionType.COMMIT, c, delay)
            }
        }
        return actions
    }

    // QWERTY rows used to pick a "neighbouring key" typo
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private fun typoFor(c: Char): Char {
        val lc = c.lowercaseChar()
        for (row in rows) {
            val idx = row.indexOf(lc)
            if (idx >= 0) {
                val shift = if (random.nextBoolean()) 1 else -1
                val ni = (idx + shift + row.length) % row.length
                val res = row[ni]
                return if (c.isUpperCase()) res.uppercaseChar() else res
            }
        }
        // digits / symbols: shift by one code point
        val shifted = c + if (random.nextBoolean()) 1 else -1
        return if (shifted.isLetterOrDigit()) shifted else c
    }
}
