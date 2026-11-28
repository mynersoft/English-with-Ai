package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PurchasedCourseItem
import com.example.domain.model.RecommendedCourseItem
import com.example.domain.model.StudentDashboardData
import com.example.ui.theme.LocalIsDarkTheme
import kotlin.math.roundToInt

@Composable
fun LearningReportOverviewSection(
    data: StudentDashboardData,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) Color(0xFF0B1120) else Color.White
    val cardBg = if (isDark) Color(0xFF131C31) else Color(0xFFF8FBF9)
    val border = if (isDark) Color(0xFF23304A) else Color(0xFFE5E7EB)
    val emerald = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val emeraldSoft = if (isDark) Color(0xFF133A29) else Color(0xFFE6F4EC)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    val totalStudyMinutes = data.stats.speakingMinutes + data.stats.readingMinutes
    val overallFraction = (data.courseReport.overallPercent.coerceIn(0, 100) / 100f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("student_learning_report_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Assessment,
                    contentDescription = null,
                    tint = emerald,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Learning Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = emeraldSoft
            ) {
                Text(
                    text = "${data.user.learningLevel} · ${data.user.dailyGoalMinutes}m/day",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = emerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(1.dp, border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall Mastery Progress",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = "${data.courseReport.overallPercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = emerald
                        )
                    }
                    LinearProgressIndicator(
                        progress = { overallFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = emerald,
                        trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                        strokeCap = StrokeCap.Round
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportMetricChip(
                        icon = Icons.Filled.Schedule,
                        iconTint = emerald,
                        value = "${totalStudyMinutes}m",
                        label = "Study Time",
                        modifier = Modifier.weight(1f)
                    )
                    ReportMetricChip(
                        icon = Icons.Filled.Quiz,
                        iconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                        value = "${data.stats.quizzesCompleted}",
                        label = "Quizzes",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReportMetricChip(
                        icon = Icons.Filled.Forum,
                        iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        value = "${data.stats.chatMessagesSent}",
                        label = "Chat Practice",
                        modifier = Modifier.weight(1f)
                    )
                    ReportMetricChip(
                        icon = Icons.Filled.School,
                        iconTint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
                        value = "${data.stats.purchasedCoursesCount}",
                        label = "Courses Owned",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportMetricChip(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val surfaceColor = if (isDark) Color(0xFF1A253B) else Color.White
    val border = if (isDark) Color(0xFF28354F) else Color(0xFFE5E7EB)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, border),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MyCoursesAndPurchasesSection(
    purchasedCourses: List<PurchasedCourseItem>,
    recommendedCourses: List<RecommendedCourseItem>,
    onOpenCoursesCatalog: () -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) Color(0xFF0B1120) else Color.White
    val cardBg = if (isDark) Color(0xFF131C31) else Color(0xFFF9FBF9)
    val border = if (isDark) Color(0xFF23304A) else Color(0xFFE5E7EB)
    val emerald = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val emeraldBtn = if (isDark) Color(0xFF10B981) else Color(0xFF0E6B3B)
    val emeraldSoft = if (isDark) Color(0xFF133A29) else Color(0xFFE6F4EC)
    val goldBg = if (isDark) Color(0xFF2D220B) else Color(0xFFFEF3C7)
    val goldFg = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("student_courses_purchases_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = emerald,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Courses & Purchases",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
            TextButton(
                onClick = onOpenCoursesCatalog,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_browse_all_courses_btn")
            ) {
                Text("Browse All", color = emerald, fontWeight = FontWeight.SemiBold)
            }
        }

        if (purchasedCourses.isNotEmpty()) {
            Text(
                text = "Purchased Courses (${purchasedCourses.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = emerald
            )
            purchasedCourses.forEach { course ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, emerald.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenCourseDetail(course.courseId) }
                        .testTag("dashboard_purchased_course_${course.courseId}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = emeraldSoft
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = emerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Purchased",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = emerald
                                    )
                                }
                            }
                            Text(
                                text = "${course.level} · ${course.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textMuted
                            )
                        }

                        Text(
                            text = course.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (course.totalLessons > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${course.completedLessons} of ${course.totalLessons} lessons",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textMuted
                                    )
                                    Text(
                                        text = "${course.completionPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = emerald
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (course.completionPercent.coerceIn(0, 100) / 100f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = emerald,
                                    trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (course.amountPaid > 0) {
                                Text(
                                    text = "Paid ${course.currency} ${course.amountPaid.roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textMuted
                                )
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            Button(
                                onClick = { onOpenCourseDetail(course.courseId) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = emeraldBtn,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Continue", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = goldBg
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = goldFg,
                                modifier = Modifier.padding(10.dp).size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upgrade with Premium Courses",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Unlock full video lessons, PDFs & structured study plans with SSLCOMMERZ (bKash, Nagad, Cards).",
                                style = MaterialTheme.typography.bodySmall,
                                color = textMuted
                            )
                        }
                    }
                    Button(
                        onClick = onOpenCoursesCatalog,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldBtn,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_purchase_courses_cta")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Browse & Purchase Courses", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (recommendedCourses.isNotEmpty()) {
            Text(
                text = if (purchasedCourses.isEmpty()) "Available Courses" else "More Courses to Explore",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = textMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            recommendedCourses.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenCourseDetail(item.courseId) }
                        .testTag("dashboard_recommended_course_${item.courseId}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (item.isPremium) goldBg else emeraldSoft
                        ) {
                            Icon(
                                imageVector = if (item.isPremium) Icons.Filled.WorkspacePremium else Icons.Filled.School,
                                contentDescription = null,
                                tint = if (item.isPremium) goldFg else emerald,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.level} · ${item.totalLessons} lessons",
                                style = MaterialTheme.typography.labelSmall,
                                color = textMuted
                            )
                        }
                        val priceLabel = if (item.isPremium) {
                            val amount = if (item.price >= 10) item.price.roundToInt() else 499
                            "৳$amount"
                        } else {
                            "Free"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (item.isPremium) emeraldBtn else emeraldSoft
                        ) {
                            Text(
                                text = if (item.isPremium) "Buy $priceLabel" else "Open",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPremium) Color.White else emerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentAccountFeaturesSection(
    isAdmin: Boolean,
    onOpenProfile: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenVideoCalls: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) Color(0xFF0B1120) else Color.White
    val cardBg = if (isDark) Color(0xFF131C31) else Color(0xFFF8FAFC)
    val border = if (isDark) Color(0xFF23304A) else Color(0xFFE5E7EB)
    val emerald = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("student_account_features_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Profile & Necessary Features",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Text(
            text = "Manage your account, study goals, courses, and security",
            style = MaterialTheme.typography.bodySmall,
            color = textMuted
        )
        Spacer(Modifier.height(2.dp))

        FeatureNavTile(
            icon = Icons.Filled.ManageAccounts,
            iconTint = emerald,
            title = "Edit Profile & Learning Goal",
            subtitle = "Update display name, level & daily target minutes",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_edit_profile",
            onClick = onOpenProfile
        )
        FeatureNavTile(
            icon = Icons.Filled.School,
            iconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
            title = "Course Store & Purchases",
            subtitle = "Browse courses, buy with SSLCOMMERZ & track lessons",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_courses",
            onClick = onOpenCourses
        )
        FeatureNavTile(
            icon = Icons.Filled.Videocam,
            iconTint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
            title = "Video Practice Calls",
            subtitle = "1-on-1, Random Partner Match & Group study rooms",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_video_calls",
            onClick = onOpenVideoCalls
        )
        FeatureNavTile(
            icon = Icons.Filled.AutoStories,
            iconTint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E),
            title = "Reading Passages & Read-Aloud",
            subtitle = "Comprehension quizzes and pronunciation reading",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_reading",
            onClick = onOpenReading
        )
        FeatureNavTile(
            icon = Icons.Filled.Devices,
            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            title = "Active Devices & Sessions",
            subtitle = "Review logged-in devices and revoke access",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_sessions",
            onClick = onOpenSessions
        )
        FeatureNavTile(
            icon = Icons.Filled.Security,
            iconTint = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
            title = "Security & Password",
            subtitle = "Password management and account protection",
            cardBg = cardBg,
            border = border,
            textPrimary = textPrimary,
            textMuted = textMuted,
            testTag = "profile_feature_security",
            onClick = onOpenSecurity
        )
        if (isAdmin) {
            FeatureNavTile(
                icon = Icons.Filled.AdminPanelSettings,
                iconTint = emerald,
                title = "Admin Dashboard",
                subtitle = "Manage courses, students, AI & content",
                cardBg = cardBg,
                border = border,
                textPrimary = textPrimary,
                textMuted = textMuted,
                testTag = "profile_feature_admin",
                onClick = onOpenAdmin
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = border)

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("profile_feature_sign_out")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeatureNavTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    cardBg: Color,
    border: Color,
    textPrimary: Color,
    textMuted: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textMuted
            )
        }
    }
}
