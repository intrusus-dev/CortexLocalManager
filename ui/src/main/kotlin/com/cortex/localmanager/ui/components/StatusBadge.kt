package com.cortex.localmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.models.Severity
import com.cortex.localmanager.ui.theme.CortexColors

enum class StatusType(val bgColor: Color, val textColor: Color) {
    SUCCESS(Color(0xFF1B3A1B), CortexColors.Success),
    WARNING(Color(0xFF3A2E1B), CortexColors.Warning),
    ERROR(Color(0xFF3A1B1B), CortexColors.Error),
    INFO(Color(0xFF1B2A3A), CortexColors.Info)
}

@Composable
fun StatusBadge(text: String, type: StatusType) {
    Box(
        modifier = Modifier
            .background(type.bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = type.textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SeverityBadge(severity: Severity) {
    val (type, label) = when (severity) {
        Severity.CRITICAL -> StatusType.ERROR to "CRITICAL"
        Severity.HIGH -> StatusType.WARNING to "HIGH"
        Severity.MEDIUM -> StatusType.WARNING to "MEDIUM"
        Severity.LOW -> StatusType.INFO to "LOW"
        Severity.INFO -> StatusType.INFO to "INFO"
    }
    StatusBadge(label, type)
}
