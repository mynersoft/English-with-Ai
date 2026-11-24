package com.example.feature.vocabulary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets

object CsvImportHelper {
    const val MAX_FILE_BYTES = 2_500_000L
    const val DEFAULT_HEADER =
        "word,meaning,pronunciation,partOfSpeech,level,category,example1En,example1Bn,example2En,example2Bn,synonyms,antonyms"

    const val SAMPLE_CSV =
        "$DEFAULT_HEADER\n" +
            "grateful,কৃতজ্ঞ,/ˈɡreɪt.fəl/,adjective,A2,Emotions,I am grateful for your help.,আমি তোমার সাহায্যের জন্য কৃতজ্ঞ।,She felt grateful after the meeting.,সভার পর সে কৃতজ্ঞ বোধ করল।,thankful;appreciative,ungrateful\n" +
            "prepare,প্রস্তুত করা,/prɪˈpeər/,verb,A1,Daily Life,Please prepare breakfast early.,দয়া করে সকালে নাশতা প্রস্তুত করো।,We prepare for exams together.,আমরা একসাথে পরীক্ষার জন্য প্রস্তুতি নিই।,ready;arrange,neglect\n"

    fun stripBom(raw: String): String {
        return raw.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }

    /**
     * Splits raw CSV text into logical row strings, preserving newlines inside quoted cells.
     */
    fun extractLogicalRows(csvText: String): List<String> {
        val text = stripBom(csvText)
        val rows = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                current.append(ch)
                if (ch == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    current.append('"')
                    i++
                } else if (ch == '"') {
                    inQuotes = false
                }
            } else {
                when (ch) {
                    '"' -> {
                        inQuotes = true
                        current.append(ch)
                    }
                    '\n' -> {
                        val rowStr = current.toString()
                        if (rowStr.isNotBlank()) rows.add(rowStr)
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            i++
        }
        val tail = current.toString()
        if (tail.isNotBlank()) rows.add(tail)
        return rows
    }

    fun countDataRows(csvText: String): Int {
        val rows = extractLogicalRows(csvText)
        return (rows.size - 1).coerceAtLeast(0)
    }

    /**
     * Splits CSV into batches of at most [maxRowsPerChunk] data rows so large files never hit
     * the server's 500-row per-request cap.
     */
    fun splitIntoChunks(csvText: String, maxRowsPerChunk: Int = 400): List<String> {
        val rows = extractLogicalRows(csvText)
        if (rows.size < 2) return emptyList()
        val header = rows.first()
        val dataRows = rows.drop(1)
        if (dataRows.size <= maxRowsPerChunk) {
            return listOf((listOf(header) + dataRows).joinToString("\n"))
        }
        return dataRows.chunked(maxRowsPerChunk).map { chunk ->
            (listOf(header) + chunk).joinToString("\n")
        }
    }

    suspend fun readCsvFromUri(context: Context, uri: Uri): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                var displayName = "vocabulary.csv"
                var fileSize = -1L
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                                displayName = it
                            }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                }

                if (fileSize > MAX_FILE_BYTES) {
                    throw IOException("CSV file is too large (max 2.5 MB).")
                }

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size.toLong() > MAX_FILE_BYTES) {
                        throw IOException("CSV file is too large (max 2.5 MB).")
                    }
                    data
                } ?: throw IOException("Could not open the selected CSV file.")

                val content = stripBom(String(bytes, StandardCharsets.UTF_8)).trim()
                if (content.isBlank()) {
                    throw IOException("Selected CSV file is empty.")
                }
                if (countDataRows(content) == 0) {
                    throw IOException("CSV file must include a header row and at least 1 word row.")
                }

                displayName to content
            }
        }
}
