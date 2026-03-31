package com.cortex.localmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.theme.CortexColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HashText(
    hash: String,
    truncate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val displayHash = if (truncate && hash.length > 16) {
        hash.take(8) + "..." + hash.takeLast(8)
    } else {
        hash
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = displayHash,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = CortexColors.TextPrimary,
            maxLines = 1
        )
        Text(
            text = if (copied) "\u2713" else "\u2398",
            fontSize = 13.sp,
            color = if (copied) CortexColors.Success else CortexColors.TextMuted,
            modifier = Modifier
                .padding(start = 6.dp)
                .clickable {
                    clipboard.setText(AnnotatedString(hash))
                    copied = true
                    scope.launch {
                        delay(1500)
                        copied = false
                    }
                }
        )
    }
}
