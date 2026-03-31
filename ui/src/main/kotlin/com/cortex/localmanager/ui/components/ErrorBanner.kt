package com.cortex.localmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = CortexColors.Error.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = CortexColors.Error,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            Text(
                text = "\u2715",
                fontSize = 16.sp,
                color = CortexColors.Error.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}
