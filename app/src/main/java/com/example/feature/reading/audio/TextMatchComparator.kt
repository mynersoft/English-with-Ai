package com.example.feature.reading.audio

import com.example.domain.model.TextMatchComparisonResult
import com.example.domain.model.WordMatchStatus
import com.example.domain.model.WordMatchToken
import kotlin.math.roundToInt

/**
 * Pure Kotlin deterministic word-level text matcher.
 * Compares spoken transcript against expected sentence text to identify
 * matched, missing, extra, and mismatched words (never a pronunciation score).
 */
object TextMatchComparator {

    private val NON_WORD_REGEX = Regex("[^a-z0-9']")

    fun tokenizeDisplay(text: String): List<String> =
        text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    fun normalizeWord(raw: String): String =
        raw.lowercase()
            .replace(NON_WORD_REGEX, "")
            .trim('\'')

    fun compare(expectedText: String, spokenText: String): TextMatchComparisonResult {
        val expDisplay = tokenizeDisplay(expectedText)
        val spkDisplay = tokenizeDisplay(spokenText)
        val expNorm = expDisplay.map(::normalizeWord)
        val spkNorm = spkDisplay.map(::normalizeWord)

        val n = expNorm.size
        val m = spkNorm.size

        // Longest Common Subsequence DP table
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (expNorm[i].isNotEmpty() && expNorm[i] == spkNorm[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // Trace aligned pairs & gaps
        data class Op(val expIdx: Int?, val spkIdx: Int?)
        val ops = mutableListOf<Op>()
        var i = 0
        var j = 0
        while (i < n || j < m) {
            when {
                i < n && j < m && expNorm[i].isNotEmpty() && expNorm[i] == spkNorm[j] -> {
                    ops.add(Op(i, j))
                    i++
                    j++
                }
                i < n && (j == m || dp[i + 1][j] >= dp[i][j + 1]) -> {
                    ops.add(Op(i, null))
                    i++
                }
                else -> {
                    ops.add(Op(null, j))
                    j++
                }
            }
        }

        val expectedTokens = MutableList(n) { idx ->
            WordMatchToken(text = expDisplay[idx], status = WordMatchStatus.MISSING)
        }
        val spokenTokens = MutableList(m) { idx ->
            WordMatchToken(text = spkDisplay[idx], status = WordMatchStatus.EXTRA)
        }

        val missingWords = mutableListOf<String>()
        val extraWords = mutableListOf<String>()
        val mismatchedPairs = mutableListOf<Pair<String, String>>()
        var matchedCount = 0

        // Group adjacent unmatched spans between matched anchors so 1-to-1 substitutions appear as MISMATCHED
        var cursor = 0
        while (cursor < ops.size) {
            val op = ops[cursor]
            if (op.expIdx != null && op.spkIdx != null) {
                expectedTokens[op.expIdx] = WordMatchToken(expDisplay[op.expIdx], WordMatchStatus.MATCHED)
                spokenTokens[op.spkIdx] = WordMatchToken(spkDisplay[op.spkIdx], WordMatchStatus.MATCHED)
                matchedCount++
                cursor++
            } else {
                val missIdx = mutableListOf<Int>()
                val extraIdx = mutableListOf<Int>()
                while (cursor < ops.size && (ops[cursor].expIdx == null || ops[cursor].spkIdx == null)) {
                    ops[cursor].expIdx?.let { missIdx.add(it) }
                    ops[cursor].spkIdx?.let { extraIdx.add(it) }
                    cursor++
                }
                val pairCount = minOf(missIdx.size, extraIdx.size)
                for (k in 0 until pairCount) {
                    val ei = missIdx[k]
                    val si = extraIdx[k]
                    val expWord = expDisplay[ei]
                    val spkWord = spkDisplay[si]
                    expectedTokens[ei] = WordMatchToken(expWord, WordMatchStatus.MISMATCHED, expectedWord = spkWord)
                    spokenTokens[si] = WordMatchToken(spkWord, WordMatchStatus.MISMATCHED, expectedWord = expWord)
                    mismatchedPairs.add(expWord to spkWord)
                }
                for (k in pairCount until missIdx.size) {
                    val ei = missIdx[k]
                    expectedTokens[ei] = WordMatchToken(expDisplay[ei], WordMatchStatus.MISSING)
                    missingWords.add(expDisplay[ei])
                }
                for (k in pairCount until extraIdx.size) {
                    val si = extraIdx[k]
                    spokenTokens[si] = WordMatchToken(spkDisplay[si], WordMatchStatus.EXTRA)
                    extraWords.add(spkDisplay[si])
                }
            }
        }

        val matchPercent = if (n > 0) ((matchedCount.toFloat() / n.toFloat()) * 100f).roundToInt() else 0
        return TextMatchComparisonResult(
            expectedText = expectedText,
            spokenText = spokenText,
            matchedCount = matchedCount,
            missingWords = missingWords,
            extraWords = extraWords,
            mismatchedPairs = mismatchedPairs,
            expectedTokens = expectedTokens,
            spokenTokens = spokenTokens,
            matchPercent = matchPercent.coerceIn(0, 100)
        )
    }
}
