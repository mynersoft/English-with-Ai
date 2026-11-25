package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feature.reading.WordPopupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordPopupSheet(
    state: WordPopupState,
    onListenWord: () -> Unit,
    onSaveToVocabulary: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return

    val pal = readingPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lookup = state.lookup
    val hasMeaning = !lookup?.meaningBn.isNullOrBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lookup?.word?.ifBlank { state.word } ?: state.word,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = pal.textPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!lookup?.pronunciation.isNullOrBlank()) {
                            Text(
                                text = lookup?.pronunciation.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = pal.primary
                            )
                        }
                        if (!lookup?.partOfSpeech.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = pal.primaryContainer
                            ) {
                                Text(
                                    text = lookup?.partOfSpeech.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pal.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("word_popup_close")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close word popup", tint = pal.textSecondary)
                }
            }

            HorizontalDivider(color = pal.border)

            if (state.isLoading && lookup == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking up meaning...", style = MaterialTheme.typography.bodyMedium, color = pal.textSecondary)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pal.background,
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Bengali Meaning",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = pal.textSecondary
                        )
                        Text(
                            text = if (hasMeaning) lookup?.meaningBn.orEmpty() else "Meaning not available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (hasMeaning) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (hasMeaning) pal.textPrimary else pal.textSecondary
                        )
                    }
                }

                if (!lookup?.example.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pal.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Example",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = pal.primary
                            )
                            Text(
                                text = lookup?.example.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = pal.textPrimary
                            )
                        }
                    }
                }
            }

            state.message?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.matchedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.matched,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            state.error?.let { err ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.missingBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.missing,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onListenWord,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("word_popup_listen_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen")
                }

                Button(
                    onClick = onSaveToVocabulary,
                    enabled = !state.isSaving && !state.savedDone,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pal.primary,
                        contentColor = pal.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("word_popup_save_button")
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = pal.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (state.savedDone) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.savedDone) "Saved" else "Save Word")
                }
            }
        }
    }
}
