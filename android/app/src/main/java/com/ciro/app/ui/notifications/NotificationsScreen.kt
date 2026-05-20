package com.ciro.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.ui.theme.CiroColors

/**
 * Enhanced notification feed screen with stakeholder type filtering.
 *
 * Features:
 *   • Filter chips: All, Public, Emergency, Hospital, Utility, Transport, Command Center
 *   • Notification count per type
 *   • Retraction messages styled with red accent
 *   • Stakeholder type badges with emoji indicators
 *   • Channel indicator (FCM, dashboard, SMS, email)
 *   • Delivery status indicator
 */
@Composable
fun NotificationsScreen(
    notifications: List<CiroNotification>,
    selectedFilter: String?,
    onFilterChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredNotifications = if (selectedFilter == null) {
        notifications
    } else {
        notifications.filter { it.stakeholder_type == selectedFilter }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Header
        Text(
            text = "STAKEHOLDER ALERTS",
            color = CiroColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${filteredNotifications.size} notifications",
            color = CiroColors.TextMuted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(12.dp))

        // ── Filter Chips ─────────────────────────────────────────────
        val stakeholderTypes = listOf(
            null to "All",
            "public" to "📢 Public",
            "emergency_services" to "🚨 Emergency",
            "hospital" to "🏥 Hospital",
            "utility" to "⚡ Utility",
            "transport" to "🚗 Transport",
            "command_center" to "📡 Command",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stakeholderTypes.forEach { (type, label) ->
                val isSelected = selectedFilter == type
                val count = if (type == null) notifications.size
                else notifications.count { it.stakeholder_type == type }
                val chipColor = if (type != null) stakeholderColor(type) else CiroColors.AccentCyan

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) chipColor.copy(alpha = 0.15f) else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (isSelected) chipColor.copy(alpha = 0.5f) else CiroColors.SurfaceBorder.copy(alpha = 0.3f),
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { onFilterChange(type) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) chipColor else CiroColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (count > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$count",
                            color = if (isSelected) chipColor else CiroColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    if (isSelected) chipColor.copy(alpha = 0.1f) else CiroColors.Surface,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (selectedFilter != null) "No ${selectedFilter.replace("_", " ")} notifications"
                        else "No notifications yet",
                        color = CiroColors.TextMuted,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Waiting for agent pipeline activity",
                        color = CiroColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredNotifications, key = { it.notification_id }) { notification ->
                    NotificationCard(notification = notification)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: CiroNotification) {
    val isRetraction = notification.is_retraction
    val stakeColor = stakeholderColor(notification.stakeholder_type)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRetraction) CiroColors.AccentRed.copy(alpha = 0.05f)
                            else CiroColors.SurfaceCard,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isRetraction) CiroColors.AccentRed.copy(alpha = 0.2f)
                else CiroColors.SurfaceBorder.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: stakeholder badge + channel + time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Stakeholder icon
                Text(
                    text = stakeholderEmoji(notification.stakeholder_type),
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notification.stakeholder_type.uppercase().replace("_", " "),
                    color = stakeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                // Channel badge
                Text(
                    text = notification.channel.uppercase(),
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .background(CiroColors.Surface, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(6.dp))
                // Timestamp
                Text(
                    text = formatSentAt(notification.sent_at),
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Retraction indicator
            if (isRetraction) {
                Text(
                    text = "⚠️ RETRACTION",
                    color = CiroColors.AccentRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .background(CiroColors.AccentRed.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(6.dp))
            }

            // Title
            Text(
                text = notification.message_title,
                color = if (isRetraction) CiroColors.AccentRed else CiroColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            // Body
            Text(
                text = notification.message_body,
                color = CiroColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // Footer: delivery status + incident ID
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when (notification.delivery_status) {
                                "delivered" -> CiroColors.AccentGreen
                                "failed" -> CiroColors.AccentRed
                                else -> CiroColors.AccentOrange
                            }
                        )
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = notification.delivery_status,
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "ID: ${notification.incident_id.take(8)}…",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun stakeholderEmoji(type: String): String = when (type) {
    "public" -> "📢"
    "emergency_services" -> "🚨"
    "hospital" -> "🏥"
    "utility" -> "⚡"
    "transport" -> "🚗"
    "command_center", "media" -> "📡"
    else -> "📋"
}

private fun stakeholderColor(type: String): Color = when (type) {
    "public" -> CiroColors.AccentCyan
    "emergency_services" -> CiroColors.AccentRed
    "hospital" -> CiroColors.AccentGreen
    "utility" -> CiroColors.Severity3
    "transport" -> CiroColors.AccentOrange
    "command_center", "media" -> CiroColors.AccentPurple
    else -> CiroColors.TextSecondary
}

private fun formatSentAt(iso: String): String {
    return try {
        val timePart = iso.substringAfter("T").substringBefore(".")
        timePart.take(5) // HH:mm
    } catch (e: Exception) {
        iso.takeLast(5)
    }
}
