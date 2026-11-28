package com.example.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AdminStudentsPanel(
    state: AdminStudentsUiState,
    onSearchQueryChange: (String) -> Unit,
    onAccountFilterChange: (String) -> Unit,
    onSubscriptionFilterChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onSelectStudent: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onUpdateAccountStatus: (String, String) -> Unit,
    onUpdateSubscription: (String, String, String, String?) -> Unit
) {
    val detail = state.selectedDetail
    if (state.isViewingDetail) {
        AdminStudentDetailView(
            loading = state.detailLoading,
            detail = detail,
            updatingStatus = state.updatingStatus,
            updatingSubscription = state.updatingSubscription,
            onBack = onCloseDetail,
            onUpdateAccountStatus = onUpdateAccountStatus,
            onUpdateSubscription = onUpdateSubscription
        )
    } else {
        AdminStudentsListView(
            state = state,
            onSearchQueryChange = onSearchQueryChange,
            onAccountFilterChange = onAccountFilterChange,
            onSubscriptionFilterChange = onSubscriptionFilterChange,
            onPageChange = onPageChange,
            onSelectStudent = onSelectStudent
        )
    }
}

@Composable
private fun AdminStudentsListView(
    state: AdminStudentsUiState,
    onSearchQueryChange: (String) -> Unit,
    onAccountFilterChange: (String) -> Unit,
    onSubscriptionFilterChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onSelectStudent: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudentStatBox("Total", state.counts.totalStudents.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StudentStatBox("Active", state.counts.activeCount.toString(), Color(0xFF34D399), Modifier.weight(1f))
                StudentStatBox("Premium", state.counts.premiumActiveCount.toString(), Color(0xFFFBBF24), Modifier.weight(1f))
                StudentStatBox(
                    "Restricted",
                    (state.counts.suspendedCount + state.counts.deactivatedCount).toString(),
                    MaterialTheme.colorScheme.error,
                    Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search students by name or email...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_students_search_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Account Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("all" to "All", "active" to "Active", "suspended" to "Suspended", "deactivated" to "Deactivated")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = state.accountStatusFilter == key,
                                onClick = { onAccountFilterChange(key) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("admin_students_account_filter_$key")
                            )
                        }
                }

                Text(
                    text = "Subscription",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "All Plans",
                        "active" to "Active",
                        "trial" to "Trial",
                        "free" to "Free",
                        "expired" to "Expired",
                        "cancelled" to "Cancelled"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = state.subscriptionFilter == key,
                            onClick = { onSubscriptionFilterChange(key) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("admin_students_sub_filter_$key")
                        )
                    }
                }
            }
        }

        if (state.loading && state.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.items.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No students match your filters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try clearing your search query or switching status filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.items, key = { it.id }) { student ->
                AdminStudentCard(
                    student = student,
                    onClick = { onSelectStudent(student.id) }
                )
            }

            if (state.totalPages > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onPageChange(state.page - 1) },
                            enabled = state.page > 1 && !state.loading,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Previous")
                        }
                        Text(
                            text = "Page ${state.page} of ${state.totalPages} (${state.total})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { onPageChange(state.page + 1) },
                            enabled = state.page < state.totalPages && !state.loading,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminStudentCard(
    student: AdminStudentSummary,
    onClick: () -> Unit
) {
    val initials = student.name
        .trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("admin_student_card_${student.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = student.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Inspect student profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccountStatusBadge(student.status)
                SubscriptionStatusBadge(student.subscriptionStatus, student.subscriptionLabel)
                InfoChip("${AdminStudentRules.normalizeStatusLabel(student.learningLevel)}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${student.progressSummary.vocabularyLearned} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${student.progressSummary.patternsPracticed} patterns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${student.progressSummary.readingCompleted} readings (${student.progressSummary.readingMinutes}m)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminStudentDetailView(
    loading: Boolean,
    detail: AdminStudentDetail?,
    updatingStatus: Boolean,
    updatingSubscription: Boolean,
    onBack: () -> Unit,
    onUpdateAccountStatus: (String, String) -> Unit,
    onUpdateSubscription: (String, String, String, String?) -> Unit
) {
    var pendingStatusChange by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_student_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to students list")
            }
            Text(
                text = detail?.student?.name ?: "Student Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider()

        if (loading && detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Student details unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val s = detail.student
        var editSubStatus by remember(s.id, s.subscriptionStatus) { mutableStateOf(s.subscriptionStatus) }
        var editSubPlan by remember(s.id, s.subscriptionPlan) { mutableStateOf(s.subscriptionPlan) }
        var editExpiryIso by remember(s.id, s.subscriptionExpiresAt) { mutableStateOf(s.subscriptionExpiresAt) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Profile overview card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(s.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AccountStatusBadge(s.status)
                            SubscriptionStatusBadge(s.subscriptionStatus, s.subscriptionLabel)
                            InfoChip("Level: ${AdminStudentRules.normalizeStatusLabel(s.learningLevel)}")
                            InfoChip("Goal: ${s.dailyGoalMinutes}m/day")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Joined: ${AdminStudentRules.formatShortDate(s.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Last login: ${AdminStudentRules.formatShortDate(s.lastLoginAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (s.authProviders.isNotEmpty()) {
                            Text(
                                text = "Sign-in: ${s.authProviders.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Account Status Management
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                            Icon(Icons.Filled.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Manage Account Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Suspended or deactivated students are blocked from signing in and accessing protected APIs until reactivated.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminStudentRules.ACCOUNT_STATUS_OPTIONS.forEach { target ->
                                val isCurrent = s.status == target
                                val btnColor = when (target) {
                                    "active" -> Color(0xFF34D399)
                                    "suspended" -> Color(0xFFFBBF24)
                                    else -> MaterialTheme.colorScheme.error
                                }
                                if (isCurrent) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = btnColor.copy(alpha = 0.16f),
                                            disabledContentColor = btnColor
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("admin_account_status_btn_$target")
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(AdminStudentRules.normalizeStatusLabel(target), maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (target == "active") {
                                                onUpdateAccountStatus(s.id, target)
                                            } else {
                                                pendingStatusChange = target
                                            }
                                        },
                                        enabled = !updatingStatus,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("admin_account_status_btn_$target")
                                    ) {
                                        Text(
                                            text = AdminStudentRules.normalizeStatusLabel(target),
                                            color = btnColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Subscription Status Management
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color(0xFFFBBF24))
                            Text(
                                text = "Manage Subscription Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Subscription Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdminStudentRules.SUBSCRIPTION_STATUS_OPTIONS.forEach { st ->
                                FilterChip(
                                    selected = editSubStatus == st,
                                    onClick = {
                                        editSubStatus = st
                                        if (st == "free") {
                                            editSubPlan = "free"
                                            editExpiryIso = null
                                        } else if (editSubPlan == "free") {
                                            editSubPlan = "monthly"
                                        }
                                    },
                                    label = { Text(AdminStudentRules.normalizeStatusLabel(st)) },
                                    modifier = Modifier.testTag("admin_sub_status_chip_$st")
                                )
                            }
                        }

                        if (editSubStatus != "free") {
                            Text(
                                text = "Plan Tier",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AdminStudentRules.SUBSCRIPTION_PLAN_OPTIONS
                                    .filter { it != "free" }
                                    .forEach { pl ->
                                        FilterChip(
                                            selected = editSubPlan == pl,
                                            onClick = {
                                                editSubPlan = pl
                                                if (pl == "lifetime") {
                                                    editExpiryIso = null
                                                }
                                            },
                                            label = { Text(AdminStudentRules.normalizeStatusLabel(pl)) },
                                            modifier = Modifier.testTag("admin_sub_plan_chip_$pl")
                                        )
                                    }
                            }

                            if (editSubPlan != "lifetime") {
                                Text(
                                    text = "Expiry: ${editExpiryIso?.take(10) ?: "No expiry set"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            editExpiryIso = AdminStudentRules.addDaysIso(System.currentTimeMillis(), 30)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("+30 Days")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            editExpiryIso = AdminStudentRules.addDaysIso(System.currentTimeMillis(), 365)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("+1 Year")
                                    }
                                    if (editExpiryIso != null) {
                                        TextButton(
                                            onClick = { editExpiryIso = null },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onUpdateSubscription(s.id, editSubStatus, editSubPlan, editExpiryIso)
                            },
                            enabled = !updatingSubscription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("admin_save_subscription_button")
                        ) {
                            if (updatingSubscription) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save Subscription")
                        }
                    }
                }
            }

            // 4. Learning Progress Summary & Course Breakdown
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Learning Progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${detail.stats.overallPercent}% overall",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudentStatBox("Streak", "${detail.stats.streakDays}d", Color(0xFFFB923C), Modifier.weight(1f))
                            StudentStatBox("Vocab", "${detail.stats.vocabularyLearned}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StudentStatBox("Patterns", "${detail.stats.patternsPracticed}", Color(0xFFA78BFA), Modifier.weight(1f))
                            StudentStatBox("Reading", "${detail.stats.readingMinutes}m", Color(0xFF2DD4BF), Modifier.weight(1f))
                        }

                        Text(
                            text = "Best streak: ${detail.stats.longestStreak}d · Active days: ${detail.stats.totalActiveDays} · Quizzes: ${detail.stats.quizzesCompleted} · Speaking: ${detail.stats.speakingSessions} · Chat: ${detail.stats.chatMessagesSent}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        detail.modules.forEach { mod ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = mod.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${mod.percent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (mod.percent.coerceIn(0, 100)) / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = mod.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingStatusChange?.let { targetStatus ->
        AlertDialog(
            onDismissRequest = { pendingStatusChange = null },
            title = { Text("Change account status?") },
            text = {
                Text(
                    "Set ${detail?.student?.name ?: "this student"}'s account to ${AdminStudentRules.normalizeStatusLabel(targetStatus)}? They will not be able to access the app while restricted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        detail?.student?.id?.let { id -> onUpdateAccountStatus(id, targetStatus) }
                        pendingStatusChange = null
                    },
                    modifier = Modifier.testTag("admin_confirm_account_status_dialog")
                ) {
                    Text("Confirm", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStatusChange = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AccountStatusBadge(status: String) {
    val (bg, fg) = when (status.lowercase()) {
        "active" -> Color(0xFF133A29) to Color(0xFF34D399)
        "suspended" -> Color(0xFF3E2B0D) to Color(0xFFFBBF24)
        else -> Color(0xFF451A1A) to Color(0xFFFCA5A5)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = AdminStudentRules.normalizeStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SubscriptionStatusBadge(subStatus: String, label: String) {
    val (bg, fg) = when (subStatus.lowercase()) {
        "active" -> Color(0xFF1E2654) to Color(0xFFA5B4FC)
        "trial" -> Color(0xFF113836) to Color(0xFF2DD4BF)
        "expired", "cancelled" -> Color(0xFF272D38) to Color(0xFFCBD5E1)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = label.ifBlank { AdminStudentRules.normalizeStatusLabel(subStatus) },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
