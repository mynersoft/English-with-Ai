package com.example.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPromptsTabContent(
    draft: AiConfigModel,
    saving: Boolean,
    onUpdateDraft: ((AiConfigModel) -> AiConfigModel) -> Unit,
    onSave: () -> Unit,
    onRequestReset: () -> Unit
) {
    val instr = draft.instructions
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AiSectionCard(
            title = "AI Speaking Coach Prompt",
            subtitle = "System instructions sent on every live speaking turn. Tap a token below to insert."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("{{topic}}", "{{level}}", "{{levelRule}}", "{{replyMaxSentences}}").forEach { token ->
                    AssistChip(
                        onClick = {
                            onUpdateDraft {
                                val curr = it.instructions.speakingBasePrompt
                                val next = if (curr.endsWith(" ") || curr.endsWith("\n") || curr.isEmpty()) {
                                    "$curr$token"
                                } else {
                                    "$curr $token"
                                }
                                it.copy(instructions = it.instructions.copy(speakingBasePrompt = next))
                            }
                        },
                        label = { Text(token, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("ai_prompt_token_$token")
                    )
                }
            }
            OutlinedTextField(
                value = instr.speakingBasePrompt,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(speakingBasePrompt = v)) }
                },
                label = { Text("Base System Instruction") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_speaking_base_prompt_input")
            )
        }

        AiSectionCard(
            title = "Level-Specific Teaching Rules",
            subtitle = "Injected via {{levelRule}} based on the student's selected speaking level."
        ) {
            OutlinedTextField(
                value = instr.beginnerRule,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(beginnerRule = v)) }
                },
                label = { Text("Beginner Rule") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_beginner_rule_input")
            )
            OutlinedTextField(
                value = instr.intermediateRule,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(intermediateRule = v)) }
                },
                label = { Text("Intermediate Rule") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_intermediate_rule_input")
            )
            OutlinedTextField(
                value = instr.advancedRule,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(advancedRule = v)) }
                },
                label = { Text("Advanced Rule") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_advanced_rule_input")
            )
        }

        AiSectionCard(
            title = "Chat & Vocabulary Evaluation Prompts",
            subtitle = "Customize how AI corrects student chat messages and evaluates vocabulary practice."
        ) {
            OutlinedTextField(
                value = instr.chatGrammarPrompt,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(chatGrammarPrompt = v)) }
                },
                label = { Text("Chat Grammar Correction Prompt") },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_chat_grammar_prompt_input")
            )
            OutlinedTextField(
                value = instr.vocabSentencePrompt,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(vocabSentencePrompt = v)) }
                },
                label = { Text("Vocabulary Sentence Check Prompt ({{word}}, {{meaning}})") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_vocab_sentence_prompt_input")
            )
            OutlinedTextField(
                value = instr.speakingEvalPrompt,
                onValueChange = { v ->
                    onUpdateDraft { it.copy(instructions = it.instructions.copy(speakingEvalPrompt = v)) }
                },
                label = { Text("Speaking Assessment Prompt ({{words}})") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_speaking_eval_prompt_input")
            )
        }

        AiActionButtonsRow(
            saveLabel = "Save Instructions",
            saving = saving,
            saveTag = "ai_save_prompts_button",
            resetTag = "ai_reset_prompts_button",
            onSave = onSave,
            onReset = onRequestReset
        )
    }
}

@Composable
fun AiLimitsTabContent(
    draft: AiConfigModel,
    saving: Boolean,
    onUpdateDraft: ((AiConfigModel) -> AiConfigModel) -> Unit,
    onSave: () -> Unit,
    onRequestReset: () -> Unit
) {
    val limits = draft.limits
    val conv = draft.conversation
    val pricing = draft.pricing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AiSectionCard(
            title = "Feature Access & Guardrails",
            subtitle = "Enable or pause individual AI capabilities and daily quota enforcement."
        ) {
            AiSwitchRow(
                title = "Enforce Daily Quotas & Spend Caps",
                subtitle = "Blocks student AI requests when daily turn or USD budget limits are reached",
                checked = limits.enforceLimits,
                testTag = "ai_enforce_limits_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(limits = it.limits.copy(enforceLimits = c)) }
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            AiSwitchRow(
                title = "AI Speaking Partner",
                subtitle = "Real-time voice conversation & pronunciation checks",
                checked = conv.speakingEnabled,
                testTag = "ai_feature_speaking_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(conversation = it.conversation.copy(speakingEnabled = c)) }
                }
            )
            AiSwitchRow(
                title = "Chat AI Grammar & Handwriting OCR",
                subtitle = "Auto grammar corrections in chat rooms and notebook photo OCR",
                checked = conv.chatGrammarEnabled,
                testTag = "ai_feature_chat_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(conversation = it.conversation.copy(chatGrammarEnabled = c)) }
                }
            )
            AiSwitchRow(
                title = "Vocabulary Sentence AI Evaluation",
                subtitle = "AI feedback when learners practice new words in sentences",
                checked = conv.vocabEvalEnabled,
                testTag = "ai_feature_vocab_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(conversation = it.conversation.copy(vocabEvalEnabled = c)) }
                }
            )
        }

        AiSectionCard(
            title = "Daily Quotas & Rate Limits",
            subtitle = "Set per-student daily AI turns, message length cap, and platform spend ceiling."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = limits.freeDailyTurns.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onUpdateDraft { it.copy(limits = it.limits.copy(freeDailyTurns = parsed.coerceIn(0, 50000))) }
                    },
                    label = { Text("Free Daily Turns") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_free_daily_turns_input")
                )
                OutlinedTextField(
                    value = limits.premiumDailyTurns.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onUpdateDraft { it.copy(limits = it.limits.copy(premiumDailyTurns = parsed.coerceIn(0, 50000))) }
                    },
                    label = { Text("Premium Daily Turns") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_premium_daily_turns_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = limits.dailyCostCapUsd.toString(),
                    onValueChange = { v ->
                        val cleaned = v.filter { it.isDigit() || it == '.' }
                        val parsed = cleaned.toDoubleOrNull() ?: 0.0
                        onUpdateDraft { it.copy(limits = it.limits.copy(dailyCostCapUsd = parsed.coerceIn(0.0, 10000.0))) }
                    },
                    label = { Text("Daily Cost Cap (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_daily_cost_cap_input")
                )
                OutlinedTextField(
                    value = limits.rpmPerUser.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 1
                        onUpdateDraft { it.copy(limits = it.limits.copy(rpmPerUser = parsed.coerceIn(1, 300))) }
                    },
                    label = { Text("Req / Min / User") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_rpm_input")
                )
            }

            OutlinedTextField(
                value = limits.maxMessageChars.toString(),
                onValueChange = { v ->
                    val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 100
                    onUpdateDraft { it.copy(limits = it.limits.copy(maxMessageChars = parsed.coerceIn(100, 4000))) }
                },
                label = { Text("Max Student Input Characters (100 - 4000)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_max_chars_input")
            )
        }

        AiSectionCard(
            title = "Cost Estimation Rates (USD / 1M Tokens)",
            subtitle = "Used to calculate live token spend across monitoring charts & budget caps."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = pricing.inputPerMillionUsd.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                        onUpdateDraft { it.copy(pricing = it.pricing.copy(inputPerMillionUsd = parsed.coerceIn(0.0, 500.0))) }
                    },
                    label = { Text("Input $/1M") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input_price_input")
                )
                OutlinedTextField(
                    value = pricing.outputPerMillionUsd.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                        onUpdateDraft { it.copy(pricing = it.pricing.copy(outputPerMillionUsd = parsed.coerceIn(0.0, 500.0))) }
                    },
                    label = { Text("Output $/1M") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_output_price_input")
                )
            }
        }

        AiActionButtonsRow(
            saveLabel = "Save Limits",
            saving = saving,
            saveTag = "ai_save_limits_button",
            resetTag = "ai_reset_limits_button",
            onSave = onSave,
            onReset = onRequestReset
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiModelAndConversationTabContent(
    draft: AiConfigModel,
    providers: List<AiProviderOption>,
    speakingTopics: List<String>,
    saving: Boolean,
    onSelectProvider: (String) -> Unit,
    onSelectPresetModel: (AiModelOption) -> Unit,
    onUpdateDraft: ((AiConfigModel) -> AiConfigModel) -> Unit,
    onSave: () -> Unit,
    onRequestReset: () -> Unit
) {
    val activeProviderOption = providers.firstOrNull { it.id == draft.provider }
    val topics = speakingTopics.ifEmpty {
        listOf("Daily Life", "Self Introduction", "Job Interviews", "Travel", "Workplace", "Food")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AiSectionCard(
            title = "Model & Provider Configuration",
            subtitle = "API keys remain safely on the backend server; configure provider, model, and sampling here."
        ) {
            Text(
                text = "Active AI Provider",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = providers.ifEmpty {
                    listOf(
                        AiProviderOption("gemini", "Google Gemini", "gemini-2.0-flash"),
                        AiProviderOption("groq", "Groq Cloud", "llama-3.3-70b-versatile"),
                        AiProviderOption("anthropic", "Anthropic Claude", "claude-haiku-5-5")
                    )
                }
                options.forEach { p ->
                    FilterChip(
                        selected = draft.provider == p.id,
                        onClick = { onSelectProvider(p.id) },
                        label = { Text(p.label) },
                        modifier = Modifier.testTag("ai_provider_chip_${p.id}")
                    )
                }
            }

            if (activeProviderOption != null && activeProviderOption.models.isNotEmpty()) {
                Text(
                    text = "Preset Models",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeProviderOption.models.forEach { m ->
                        FilterChip(
                            selected = draft.model == m.id,
                            onClick = { onSelectPresetModel(m) },
                            label = { Text(m.label) },
                            modifier = Modifier.testTag("ai_model_chip_${m.id}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.model,
                onValueChange = { v -> onUpdateDraft { it.copy(model = v) } },
                label = { Text("Model ID (or custom override)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_id_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temperature (Creativity)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.2f", draft.temperature),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = draft.temperature.coerceIn(0f, 1.5f),
                onValueChange = { v ->
                    val rounded = (Math.round(v * 20f) / 20f).coerceIn(0f, 1.5f)
                    onUpdateDraft { it.copy(temperature = rounded) }
                },
                valueRange = 0f..1.5f,
                modifier = Modifier.testTag("ai_temperature_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.maxOutputTokens.toString(),
                    onValueChange = { v ->
                        val parsed = v.filter { it.isDigit() }.toIntOrNull() ?: 64
                        onUpdateDraft { it.copy(maxOutputTokens = parsed.coerceIn(64, 4096)) }
                    },
                    label = { Text("Max Output Tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_max_tokens_input")
                )
                OutlinedTextField(
                    value = (draft.requestTimeoutMs / 1000).toString(),
                    onValueChange = { v ->
                        val secs = (v.filter { it.isDigit() }.toIntOrNull() ?: 5).coerceIn(5, 60)
                        onUpdateDraft { it.copy(requestTimeoutMs = secs * 1000) }
                    },
                    label = { Text("Timeout (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_timeout_sec_input")
                )
            }
        }

        AiSectionCard(
            title = "Conversation Settings",
            subtitle = "Control context memory depth, reply brevity, default practice topic, and feedback style."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Context History Turns",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${draft.conversation.maxHistoryTurns} turns",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = draft.conversation.maxHistoryTurns.toFloat().coerceIn(1f, 30f),
                onValueChange = { v ->
                    onUpdateDraft {
                        it.copy(conversation = it.conversation.copy(maxHistoryTurns = v.toInt().coerceIn(1, 30)))
                    }
                },
                valueRange = 1f..30f,
                modifier = Modifier.testTag("ai_history_turns_slider")
            )

            Text(
                text = "Max Sentences Per AI Reply",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3, 4, 5).forEach { count ->
                    FilterChip(
                        selected = draft.conversation.replyMaxSentences == count,
                        onClick = {
                            onUpdateDraft { it.copy(conversation = it.conversation.copy(replyMaxSentences = count)) }
                        },
                        label = { Text("$count") }
                    )
                }
            }

            Text(
                text = "Default Speaking Level",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("beginner", "intermediate", "advanced").forEach { lvl ->
                    FilterChip(
                        selected = draft.conversation.defaultLevel == lvl,
                        onClick = {
                            onUpdateDraft { it.copy(conversation = it.conversation.copy(defaultLevel = lvl)) }
                        },
                        label = { Text(lvl.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text(
                text = "Default Speaking Topic",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                topics.forEach { topic ->
                    FilterChip(
                        selected = draft.conversation.defaultTopic == topic,
                        onClick = {
                            onUpdateDraft { it.copy(conversation = it.conversation.copy(defaultTopic = topic)) }
                        },
                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            AiSwitchRow(
                title = "Auto Grammar Correction",
                subtitle = "Automatically inspect messages for grammar fixes in chat & speaking",
                checked = draft.conversation.autoCorrectGrammar,
                testTag = "ai_auto_correct_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(conversation = it.conversation.copy(autoCorrectGrammar = c)) }
                }
            )
            AiSwitchRow(
                title = "Include Bangla Explanations",
                subtitle = "Show grammar explanations in simple Bangla (switch off for English-only)",
                checked = draft.conversation.includeBanglaExplanation,
                testTag = "ai_bangla_explanation_switch",
                onCheckedChange = { c ->
                    onUpdateDraft { it.copy(conversation = it.conversation.copy(includeBanglaExplanation = c)) }
                }
            )
        }

        AiActionButtonsRow(
            saveLabel = "Save Model & Chat",
            saving = saving,
            saveTag = "ai_save_model_button",
            resetTag = "ai_reset_model_button",
            onSave = onSave,
            onReset = onRequestReset
        )
    }
}

@Composable
fun AiSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun AiSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun AiActionButtonsRow(
    saveLabel: String,
    saving: Boolean,
    saveTag: String,
    resetTag: String,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onReset,
            enabled = !saving,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .minimumInteractiveComponentSize()
                .testTag(resetTag)
        ) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset")
        }
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier
                .weight(1.6f)
                .height(48.dp)
                .minimumInteractiveComponentSize()
                .testTag(saveTag)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(saveLabel)
            }
        }
    }
}
