package com.cortex.localmanager.ui.detections

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.models.*
import com.cortex.localmanager.ui.components.StatusBadge
import com.cortex.localmanager.ui.components.StatusType
import com.cortex.localmanager.ui.theme.CortexColors
import java.time.Instant as JInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timeline visualization of alerts — chronological vertical layout with
 * severity indicators, process trees, and expandable detail.
 */
@Composable
fun TimelineView(
    alerts: List<UnifiedAlert>,
    onAlertClick: (UnifiedAlert) -> Unit
) {
    if (alerts.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("No alerts to display in timeline", fontSize = 14.sp, color = CortexColors.TextMuted)
        }
        return
    }

    // Group by date
    val grouped = alerts.groupBy { formatDate(it.timestamp) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        grouped.forEach { (date, dayAlerts) ->
            // Date header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(CortexColors.Border))
                Text(
                    date, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = CortexColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(CortexColors.Border))
            }

            // Timeline entries
            dayAlerts.forEachIndexed { index, alert ->
                val isLast = index == dayAlerts.size - 1
                TimelineEntry(alert, isLast, onAlertClick)
            }
        }
    }
}

@Composable
private fun TimelineEntry(
    alert: UnifiedAlert,
    isLast: Boolean,
    onClick: (UnifiedAlert) -> Unit
) {
    val severityColor = when (alert.severity) {
        Severity.CRITICAL -> CortexColors.SeverityCritical
        Severity.HIGH -> CortexColors.SeverityHigh
        Severity.MEDIUM -> CortexColors.SeverityMedium
        Severity.LOW -> CortexColors.SeverityLow
        Severity.INFO -> CortexColors.SeverityInfo
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick(alert) }
            .padding(start = 16.dp)
    ) {
        // Timeline spine
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(severityColor)
                    .border(2.dp, severityColor.copy(alpha = 0.5f), CircleShape)
            )
            // Line to next entry
            if (!isLast) {
                Box(
                    modifier = Modifier.width(2.dp).weight(1f)
                        .background(CortexColors.Border)
                )
            }
        }

        // Content card
        Column(
            modifier = Modifier.weight(1f)
                .padding(start = 12.dp, end = 16.dp, bottom = 12.dp)
                .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, severityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            // Top row: time + severity + type
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatTime(alert.timestamp),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = CortexColors.TextMuted
                )
                StatusBadge(alert.severity.name, when (alert.severity) {
                    Severity.CRITICAL, Severity.HIGH -> StatusType.ERROR
                    Severity.MEDIUM -> StatusType.WARNING
                    else -> StatusType.INFO
                })
                alert.componentName?.let {
                    Text(it, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = CortexColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Description
            Text(alert.description, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CortexColors.TextPrimary)

            // Process / file info
            val processOrFile = alert.processPath?.substringAfterLast("\\")
                ?: alert.filePath?.substringAfterLast("\\")
            if (processOrFile != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    processOrFile,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = CortexColors.PaloAltoOrange
                )
            }

            // MITRE ATT&CK
            if (alert.mitreTechniques.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    alert.mitreTechniques.forEach { tech ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2A1B3A), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(tech, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCE93D8))
                        }
                    }
                }
            }

            // Hash
            alert.sha256?.let { hash ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "${hash.take(16)}...${hash.takeLast(8)}",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = CortexColors.TextMuted
                )
            }

            // Action + user
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                alert.actionTaken?.let {
                    StatusBadge(it, when (it.lowercase()) {
                        "blocked" -> StatusType.BLOCKED
                        "quarantined" -> StatusType.QUARANTINED
                        else -> StatusType.INFO
                    })
                }
                alert.user?.let {
                    Text(it, fontSize = 10.sp, color = CortexColors.TextMuted)
                }
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatDate(timestamp: kotlinx.datetime.Instant): String {
    return try {
        val ji = JInstant.ofEpochSecond(timestamp.epochSeconds)
        dateFmt.format(ji.atZone(ZoneId.systemDefault()))
    } catch (_: Exception) { "Unknown date" }
}

private fun formatTime(timestamp: kotlinx.datetime.Instant): String {
    return try {
        val ji = JInstant.ofEpochSecond(timestamp.epochSeconds)
        timeFmt.format(ji.atZone(ZoneId.systemDefault()))
    } catch (_: Exception) { "??:??:??" }
}
