package com.example.feature.reading

import com.example.domain.model.ReadingSentence

/**
 * Pure Kotlin deterministic sentence splitter matching backend readingUtils.splitTextIntoSentences.
 */
object ReadingSentenceSplitter {
    private val ABBREV_REGEX =
        Regex("\\b(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs|etc|e\\.g|i\\.e|a\\.m|p\\.m|U\\.S|U\\.K)\\.", RegexOption.IGNORE_CASE)

    fun splitEnglishText(englishText: String): List<ReadingSentence> {
        val cleaned = englishText.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return emptyList()

        val protected = ABBREV_REGEX.replace(cleaned) { mr ->
            mr.value.replace('.', '\u0000')
        }
        val decimalProtected = Regex("(\\d)\\.(\\d)").replace(protected) { mr ->
            "${mr.groupValues[1]}\u0000${mr.groupValues[2]}"
        }

        val rawParts = Regex("(?<=[.!?])\\s+").split(decimalProtected)
        return rawParts
            .map { it.replace('\u0000', '.').trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { idx, text ->
                ReadingSentence(
                    sentenceId = "s_${idx + 1}",
                    order = idx,
                    en = text,
                    bn = ""
                )
            }
    }
}
