package com.example.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val EmeraldColor = Color(0xFF34D399)
private val EmeraldBg = Color(0xFF133A29)
private val AmberColor = Color(0xFFFBBF24)
private val AmberBg = Color(0xFF3E2B0D)
private val IndigoColor = Color(0xFFA5B4FC)
private val IndigoBg = Color(0xFF1E2654)

@Composable
fun AdminAiManagementPanel(
    state: AdminAiManagementUiState,
    onRefresh: () -> Unit,
    onSelectWindowDays: (Int) -> Unit,
    onToggleGlobalEnabled: (Boolean) -> Unit,
    onUpdateDraft: ((AiConfigModel) -> AiConfigModel) -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectPresetModel: (AiModelOption) -> Unit,
    onSaveInstructions: () -> Unit,
    onSaveLimits: () -> Unit,
    onSaveModelAndConversation: () -> Unit,
    onResetSection: (String) -> Unit
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) } // 0=Monitor, 1=Prompts, 2=Limits, 3=Model & Chat
    var pendingResetSection by remember { mutableStateOf<String?>(null) }

    val overview = state.overview
    val draft = state.draftConfig

    Column(
        modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxSize()
            .testTag("admin_ai_management_panel")
    ) {
        ScrollableTabRow(
            selectedTabIndex = subTab,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "Monitor & Costs" to "ai_subtab_monitor",
                "Prompts" to "ai_subtab_prompts",
                "Usage Limits" to "ai_subtab_limits",
                "Model & Chat" to "ai_subtab_model"
            ).forEachIndexed { idx, (label, tag) ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(label) },
                    modifier = Modifier.testTag(tag)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AiEngineMasterStatusCard(
                    config = draft,
                    saving = state.saving,
                    loading = state.loading,
                    hasUnsavedChanges = state.hasUnsavedChanges,
                    onToggleGlobalEnabled = onToggleGlobalEnabled,
                    onRefresh = onRefresh
                )
            }

            if (!state.error.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_management_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = onRefresh, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            if (state.loading && overview == null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text("Loading AI configuration, usage & cost metrics...")
                        }
                    }
                }
            } else {
                when (subTab) {
                    0 -> item {
                        AiMonitorTabContent(
                            usage = overview?.usage ?: AiUsageMonitorSummary(),
                            config = draft,
                            selectedWindowDays = state.windowDays,
                            onSelectWindowDays = onSelectWindowDays
                        )
                    }
                    1 -> item {
                        AiPromptsTabContent(
                            draft = draft,
                            saving = state.saving,
                            onUpdateDraft = onUpdateDraft,
                            onSave = onSaveInstructions,
                            onRequestReset = { pendingResetSection = "instructions" }
                        )
                    }
                    2 -> item {
                        AiLimitsTabContent(
                            draft = draft,
                            saving = state.saving,
                            onUpdateDraft = onUpdateDraft,
                            onSave = onSaveLimits,
                            onRequestReset = { pendingResetSection = "limits" }
                        )
                    }
                    3 -> item {
                        AiModelAndConversationTabContent(
                            draft = draft,
                            providers = overview?.providers.orEmpty(),
                            speakingTopics = overview?.speakingTopics.orEmpty(),
                            saving = state.saving,
                            onSelectProvider = onSelectProvider,
                            onSelectPresetModel = onSelectPresetModel,
                            onUpdateDraft = onUpdateDraft,
                            onSave = onSaveModelAndConversation,
                            onRequestReset = { pendingResetSection = "model" }
                        )
                    }
                }
            }
        }
    }

    pendingResetSection?.let { section ->
        AlertDialog(
            onDismissRequest = { pendingResetSection = null },
            title = { Text("Reset AI settings?") },
            text = {
                Text("This will restore the ${section.replaceFirstChar { it.uppercase() }} settings to their recommended defaults.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingResetSection = null
                        onResetSection(section)
                    },
                    modifier = Modifier.testTag("ai_confirm_reset_button")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetSection = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AiEngineMasterStatusCard(
    config: AiConfigModel,
    saving: Boolean,
    loading: Boolean,
    hasUnsavedChanges: Boolean,
    onToggleGlobalEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_master_status_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndigoBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = IndigoColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${config.provider.replaceFirstChar { it.uppercase() }} · ${config.model}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusBadgePill(
                            text = if (config.apiKeyConfigured) "Server Key Ready" else "API Key Missing",
                            textColor = if (config.apiKeyConfigured) EmeraldColor else AmberColor,
                            bgColor = if (config.apiKeyConfigured) EmeraldBg else AmberBg
                        )
                        if (hasUnsavedChanges) {
                            StatusBadgePill(
                                text = "Unsaved Edits",
                                textColor = AmberColor,
                                bgColor = AmberBg
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("ai_management_refresh_button")
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh AI overview")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (config.isEnabled) "AI Engine Active" else "AI Engine Paused",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (config.isEnabled) EmeraldColor else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Master toggle for all student AI endpoints",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.isEnabled,
                    enabled = !saving,
                    onCheckedChange = onToggleGlobalEnabled,
                    modifier = Modifier.testTag("ai_global_enable_switch")
                )
            }
        }
    }
}

@Composable
private fun StatusBadgePill(text: String, textColor: Color, bgColor: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = bgColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiMonitorTabContent(
    usage: AiUsageMonitorSummary,
    config: AiConfigModel,
    selectedWindowDays: Int,
    onSelectWindowDays: (Int) -> Unit
) {
    val activeStats = if (selectedWindowDays == 1) usage.today else usage.window
    val dailyCap = config.limits.dailyCostCapUsd.coerceAtLeast(0.0)
    val todayCost = usage.today.estimatedCostUsd
    val budgetProgress = if (dailyCap > 0.0) (todayCost / dailyCap).toFloat().coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1 to "Today", 7 to "Last 7 Days", 30 to "Last 30 Days").forEach { (days, label) ->
                FilterChip(
                    selected = selectedWindowDays == days,
                    onClick = { onSelectWindowDays(days) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("ai_window_chip_${days}d")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiKpiTile(
                icon = Icons.Filled.AutoAwesome,
                value = activeStats.requests.toString(),
                label = "AI Requests",
                sublabel = "${activeStats.successCount} ok · ${activeStats.errorCount} err",
                accent = IndigoColor,
                modifier = Modifier.weight(1f)
            )
            AiKpiTile(
                icon = Icons.Filled.Token,
                value = AdminAiManagementParser.formatCompactTokens(activeStats.totalTokens),
                label = "Total Tokens",
                sublabel = "In ${AdminAiManagementParser.formatCompactTokens(activeStats.promptTokens)} / Out ${AdminAiManagementParser.formatCompactTokens(activeStats.completionTokens)}",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiKpiTile(
                icon = Icons.Filled.AttachMoney,
                value = AdminAiManagementParser.formatUsd(activeStats.estimatedCostUsd),
                label = "Est. Cost",
                sublabel = "All-time: ${AdminAiManagementParser.formatUsd(usage.allTime.estimatedCostUsd)}",
                accent = EmeraldColor,
                modifier = Modifier.weight(1f)
            )
            AiKpiTile(
                icon = Icons.Filled.Speed,
                value = "${activeStats.avgLatencyMs} ms",
                label = "Avg Latency",
                sublabel = "${activeStats.errorRatePercent}% error · ${activeStats.blockedCount} blocked",
                accent = AmberColor,
                modifier = Modifier.weight(1f)
            )
        }

        AiSectionCard(
            title = "Today's Budget Guardrail",
            subtitle = "Daily cap ${AdminAiManagementParser.formatUsd(dailyCap)} · Free ${config.limits.freeDailyTurns}/day · Premium ${config.limits.premiumDailyTurns}/day"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today Spend: ${AdminAiManagementParser.formatUsd(todayCost)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (dailyCap > 0.0) "${(budgetProgress * 100).toInt()}% of cap" else "No Cap",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (budgetProgress > 0.85f) MaterialTheme.colorScheme.error else EmeraldColor
                )
            }
            LinearProgressIndicator(
                progress = { budgetProgress },
                color = if (budgetProgress > 0.85f) MaterialTheme.colorScheme.error else EmeraldColor,
                trackColor = EmeraldBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
        }

        if (usage.dailySeries.isNotEmpty()) {
            AiSectionCard(
                title = "Daily AI Volume",
                subtitle = "Tracked requests over the selected window"
            ) {
                val points = usage.dailySeries.takeLast(10)
                val maxReq = (points.maxOfOrNull { it.requests } ?: 1).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEach { pt ->
                        val frac = if (pt.requests == 0) 0.08f else (pt.requests.toFloat() / maxReq).coerceIn(0.15f, 1f)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = pt.requests.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pt.isToday) IndigoColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height((42.dp * frac).coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(if (pt.isToday) IndigoColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = pt.dayShort,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        AiSectionCard(
            title = "Usage & Cost by Feature",
            subtitle = "Breakdown across Speaking, Chat Grammar, Vocabulary & OCR (${usage.allTime.legacyInteractions} historical interactions)"
        ) {
            if (usage.byFeature.isEmpty()) {
                Text(
                    text = "No tracked AI requests in this window yet. Metrics populate live as learners use Speaking, Chat Grammar, and Vocabulary AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                usage.byFeature.forEach { f ->
                    AiBreakdownRow(
                        title = f.label,
                        subtitle = "${AdminAiManagementParser.formatCompactTokens(f.tokens)} tokens · ${f.avgLatencyMs} ms avg",
                        rightPrimary = "${f.requests} calls",
                        rightSecondary = AdminAiManagementParser.formatUsd(f.costUsd)
                    )
                }
            }
        }

        if (usage.byModel.isNotEmpty()) {
            AiSectionCard(
                title = "Usage by Provider & Model",
                subtitle = "Cost and token totals grouped by model"
            ) {
                usage.byModel.forEach { m ->
                    AiBreakdownRow(
                        title = "${m.provider.replaceFirstChar { it.uppercase() }} · ${m.model}",
                        subtitle = "${AdminAiManagementParser.formatCompactTokens(m.tokens)} tokens",
                        rightPrimary = "${m.requests} calls",
                        rightSecondary = AdminAiManagementParser.formatUsd(m.costUsd)
                    )
                }
            }
        }

        if (usage.topUsers.isNotEmpty()) {
            AiSectionCard(
                title = "Top Active Students",
                subtitle = "Highest AI consumption in selected period"
            ) {
                usage.topUsers.forEach { u ->
                    AiBreakdownRow(
                        title = u.name + if (u.isPremium) " (Premium)" else "",
                        subtitle = u.email.ifBlank { u.userId },
                        rightPrimary = "${u.requests} calls",
                        rightSecondary = AdminAiManagementParser.formatUsd(u.costUsd)
                    )
                }
            }
        }

        if (usage.recentLogs.isNotEmpty()) {
            AiSectionCard(
                title = "Recent AI Activity",
                subtitle = "Latest 12 requests with latency, tokens & status"
            ) {
                usage.recentLogs.forEach { log ->
                    AiBreakdownRow(
                        title = "${log.featureLabel} (${log.status})",
                        subtitle = "${log.model} · ${log.totalTokens} tok · ${log.latencyMs} ms",
                        rightPrimary = AdminAiManagementParser.formatUsd(log.estimatedCostUsd),
                        rightSecondary = log.errorCode.ifBlank { log.status }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiKpiTile(
    icon: ImageVector,
    value: String,
    label: String,
    sublabel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AiBreakdownRow(
    title: String,
    subtitle: String,
    rightPrimary: String,
    rightSecondary: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(rightPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(rightSecondary, style = MaterialTheme.typography.labelSmall, color = EmeraldColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}
