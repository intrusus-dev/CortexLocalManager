package com.cortex.localmanager.ui.hunting

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.hunting.ExportFormat
import com.cortex.localmanager.core.hunting.IocSearchResult
import com.cortex.localmanager.core.models.FileSearchResult
import com.cortex.localmanager.ui.components.*
import com.cortex.localmanager.ui.theme.CortexColors
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path
import java.time.Instant as JavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HuntingScreen(
    viewModel: HuntingViewModel,
    onAddException: ((String?, String?) -> Unit)? = null,
    prefilledHash: String? = null
) {
    val state by viewModel.state.collectAsState()

    // Pre-fill hash from navigation
    LaunchedEffect(prefilledHash) {
        if (prefilledHash != null && prefilledHash != state.searchInput) {
            viewModel.setSearchInput(prefilledHash)
            viewModel.searchHash(prefilledHash)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Messages
        state.error?.let {
            ErrorBanner(it, onDismiss = viewModel::dismissMessage)
            Spacer(Modifier.height(8.dp))
        }
        state.message?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("\u2713 $it", fontSize = 12.sp, color = CortexColors.Success)
                Spacer(Modifier.width(8.dp))
                Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
            }
        }

        // Section 1 — Hash Search
        SectionCard("Hash Search") {
            HashSearchSection(state, viewModel, onAddException)
        }

        Spacer(Modifier.height(16.dp))

        // Section 2 — Hash DB Management
        SectionCard("Hash Database") {
            HashDbSection(state, viewModel)
        }

        Spacer(Modifier.height(16.dp))

        // Section 3 — Hash Browser
        SectionCard("Hash Database Browser") {
            HashBrowserSection(state, viewModel)
        }

        Spacer(Modifier.height(16.dp))

        // Section 4 — IoC List Management
        SectionCard("IoC List Management") {
            IocSection(state, viewModel)
        }
    }
}

// --- Section 1: Hash Search ---

@Composable
private fun HashSearchSection(state: HuntingState, viewModel: HuntingViewModel, onAddException: ((String?, String?) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (!state.inputValid) CortexColors.Error.copy(alpha = 0.15f) else CortexColors.SurfaceVariant,
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (state.searchInput.isEmpty()) {
                Text("Enter SHA256 hash (64 hex characters)...", fontSize = 13.sp, color = CortexColors.TextMuted)
            }
            BasicTextField(
                value = state.searchInput,
                onValueChange = viewModel::setSearchInput,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        ActionBtn("Search") { viewModel.searchHash() }
    }

    if (!state.inputValid) {
        Text("Must be exactly 64 hexadecimal characters", fontSize = 10.sp, color = CortexColors.Error, modifier = Modifier.padding(top = 4.dp))
    }

    Spacer(Modifier.height(12.dp))

    // Results
    when (val outcome = state.searchResult) {
        is SearchOutcome.Idle -> {}
        is SearchOutcome.Searching -> LoadingIndicator("Searching local database...")
        is SearchOutcome.NotFound -> {
            Box(
                modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).padding(16.dp)
            ) {
                Column {
                    Text("Hash not found in local database", fontSize = 14.sp, color = CortexColors.Warning)
                    Spacer(Modifier.height(4.dp))
                    Text("Try refreshing the hash database below.", fontSize = 12.sp, color = CortexColors.TextMuted)
                }
            }
        }
        is SearchOutcome.Found -> {
            HashResultCard(outcome.results, onAddException)
        }
        is SearchOutcome.Error -> ErrorBanner(outcome.message)
    }
}

@Composable
private fun HashResultCard(results: List<FileSearchResult>, onAddException: ((String?, String?) -> Unit)?) {
    val clipboard = LocalClipboardManager.current
    val sha256 = results.firstOrNull()?.sha256 ?: ""

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(CortexColors.Success.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Success.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u2713 Hash Found", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.Success)
            Text(" \u2014 ${results.size} location${if (results.size > 1) "s" else ""}", fontSize = 13.sp, color = CortexColors.TextSecondary)
        }

        Spacer(Modifier.height(8.dp))

        // SHA256 + MD5
        InfoLine("SHA256", sha256, mono = true, copyable = true)
        results.firstOrNull()?.md5?.let { InfoLine("MD5", it, mono = true, copyable = true) }

        Spacer(Modifier.height(12.dp))

        // Locations
        results.forEachIndexed { index, result ->
            if (results.size > 1) {
                Text("Location ${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
            InfoLine("Path", result.filePath, mono = true, copyable = true)
            result.fileId?.let { InfoLine("File ID", it, copyable = true) }
            result.dateCreated?.let { InfoLine("Created", it) }
            result.dateLastModified?.let { InfoLine("Last Modified", it) }
            result.createdByUser?.let { InfoLine("User", it, copyable = true) }
            result.createdBySid?.let { InfoLine("SID", it, mono = true, copyable = true) }
            if (index < results.size - 1) Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAddException != null) {
                ActionBtn("Add Exception") { onAddException(sha256, results.firstOrNull()?.filePath) }
            }
            ActionBtn("Copy SHA256") { clipboard.setText(AnnotatedString(sha256)) }
        }
    }
}

// --- Section 2: Hash DB Management ---

@Composable
private fun HashDbSection(state: HuntingState, viewModel: HuntingViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionBtn(if (state.scanInProgress) "Scanning..." else "Refresh Hash Database") {
            if (!state.scanInProgress) viewModel.refreshHashDatabase()
        }
        state.scanStatus?.let {
            Text(it, fontSize = 12.sp, color = CortexColors.TextSecondary)
        }
    }
    if (state.scanInProgress) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            color = CortexColors.PaloAltoOrange,
            trackColor = CortexColors.SurfaceVariant,
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
    }
}

// --- Section 3: Hash Browser ---

@Composable
private fun HashBrowserSection(state: HuntingState, viewModel: HuntingViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionBtn("Load Hash Database") { viewModel.loadHashEntries() }
        if (state.hashEntries.isNotEmpty()) {
            Text("${state.hashEntries.size} entries", fontSize = 12.sp, color = CortexColors.TextMuted)
        }
    }

    if (state.hashEntriesLoading) {
        Spacer(Modifier.height(8.dp))
        LoadingIndicator("Loading file_id_hash.db...")
    }

    if (state.hashEntries.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))

        // Filter
        Box(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (state.hashEntriesFilter.isEmpty()) Text("Filter hashes...", fontSize = 12.sp, color = CortexColors.TextMuted)
            BasicTextField(
                value = state.hashEntriesFilter,
                onValueChange = viewModel::setHashEntriesFilter,
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        // Table (non-lazy since we're inside a scrollable column — limit display)
        val filtered = state.hashEntries.filter {
            state.hashEntriesFilter.isBlank() || it.value.sha256.contains(state.hashEntriesFilter, ignoreCase = true)
        }
        val displayed = filtered.take(200)

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(Modifier.weight(1f)) { Text("SHA256", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
            Box(Modifier.width(150.dp)) { Text("LAST USED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
            Box(Modifier.width(60.dp)) { Text("INDEX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
        }

        displayed.forEachIndexed { index, entry ->
            val bg = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(bg)
                    .clickable {
                        viewModel.setSearchInput(entry.value.sha256)
                        viewModel.searchHash(entry.value.sha256)
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    Text(entry.value.sha256, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CortexColors.TextPrimary, maxLines = 1)
                }
                Box(Modifier.width(150.dp)) {
                    Text(formatUnixTimestamp(entry.value.lruData.lastUsed), fontSize = 11.sp, color = CortexColors.TextSecondary)
                }
                Box(Modifier.width(60.dp)) {
                    Text(entry.value.lruData.index, fontSize = 11.sp, color = CortexColors.TextMuted)
                }
            }
        }

        if (filtered.size > 200) {
            Text("Showing 200 of ${filtered.size} entries. Use filter to narrow.", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(8.dp))
        }
    }
}

// --- Section 4: IoC Management ---

@Composable
private fun IocSection(state: HuntingState, viewModel: HuntingViewModel) {
    // Import + Add
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionBtn("Import IoC List") {
            val dialog = FileDialog(null as java.awt.Frame?, "Import IoC List", FileDialog.LOAD)
            dialog.isVisible = true
            val dir = dialog.directory ?: return@ActionBtn
            val file = dialog.file ?: return@ActionBtn
            viewModel.importIocList(File(dir, file).toPath())
        }
        Text("(.txt one hash/line, .csv, or .json)", fontSize = 10.sp, color = CortexColors.TextMuted)
    }

    Spacer(Modifier.height(8.dp))

    // Manual add
    var manualHash by remember { mutableStateOf("") }
    var manualDesc by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.weight(1f).background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (manualHash.isEmpty()) Text("SHA256...", fontSize = 11.sp, color = CortexColors.TextMuted)
            BasicTextField(
                value = manualHash, onValueChange = { manualHash = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange), modifier = Modifier.fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier.width(150.dp).background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (manualDesc.isEmpty()) Text("Description...", fontSize = 11.sp, color = CortexColors.TextMuted)
            BasicTextField(
                value = manualDesc, onValueChange = { manualDesc = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange), modifier = Modifier.fillMaxWidth()
            )
        }
        ActionBtn("Add") {
            if (manualHash.isNotBlank()) {
                viewModel.addIocManually(manualHash, manualDesc)
                manualHash = ""; manualDesc = ""
            }
        }
    }

    if (state.iocList.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn(
                if (state.iocBatchSearching) "Searching ${state.iocBatchProgress?.first ?: 0}/${state.iocBatchProgress?.second ?: 0}..."
                else "Search All IoCs"
            ) {
                if (!state.iocBatchSearching) viewModel.runBatchSearch()
            }
            ActionBtn("Clear All") { viewModel.clearIocList() }
            if (state.iocSearchResults.isNotEmpty()) {
                ActionBtn("Export Results") {
                    val dialog = FileDialog(null as java.awt.Frame?, "Export IoC Results", FileDialog.SAVE)
                    dialog.file = "ioc_results.json"
                    dialog.isVisible = true
                    val dir = dialog.directory ?: return@ActionBtn
                    val file = dialog.file ?: return@ActionBtn
                    val ext = if (file.endsWith(".csv")) ExportFormat.CSV else ExportFormat.JSON
                    viewModel.exportIocResults(File(dir, file).toPath(), ext)
                }
            }
        }

        if (state.iocBatchSearching) {
            Spacer(Modifier.height(6.dp))
            val progress = state.iocBatchProgress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.first.toFloat() / progress.second },
                    color = CortexColors.PaloAltoOrange,
                    trackColor = CortexColors.SurfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // IoC table
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(Modifier.weight(1f)) { Text("SHA256", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
            Box(Modifier.width(150.dp)) { Text("DESCRIPTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
            Box(Modifier.width(80.dp)) { Text("STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CortexColors.TextMuted) }
            Box(Modifier.width(50.dp)) {} // remove button
        }

        val resultMap = state.iocSearchResults.associateBy { it.ioc.sha256 }
        state.iocList.forEachIndexed { index, ioc ->
            val result = resultMap[ioc.sha256]
            val bg = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        Text(ioc.sha256, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CortexColors.TextPrimary, maxLines = 1)
                    }
                    Box(Modifier.width(150.dp)) {
                        Text(ioc.description.ifBlank { "-" }, fontSize = 11.sp, color = CortexColors.TextSecondary, maxLines = 1)
                    }
                    Box(Modifier.width(80.dp)) {
                        when {
                            result == null -> StatusBadge("PENDING", StatusType.INFO)
                            result.found -> StatusBadge("FOUND", StatusType.ERROR)
                            else -> StatusBadge("CLEAN", StatusType.SUCCESS)
                        }
                    }
                    Box(Modifier.width(50.dp)) {
                        Text("\u2715", fontSize = 12.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.removeIoc(ioc.sha256) })
                    }
                }
                // Show locations if found
                if (result != null && result.found) {
                    result.locations.forEach { loc ->
                        Text(
                            "  \u2192 ${loc.filePath}",
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            color = CortexColors.Warning,
                            modifier = Modifier.padding(start = 24.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }

        // Summary
        if (state.iocSearchResults.isNotEmpty() && !state.iocBatchSearching) {
            Spacer(Modifier.height(8.dp))
            val found = state.iocSearchResults.count { it.found }
            Text(
                "Result: $found of ${state.iocList.size} IoCs found on this endpoint",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (found > 0) CortexColors.Warning else CortexColors.Success
            )
        }
    }
}

// --- Shared helpers ---

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Text("$label: ", fontSize = 11.sp, color = CortexColors.TextMuted)
        Text(value, fontSize = 11.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, color = CortexColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
        if (copyable) {
            Text("\u2398", fontSize = 12.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(start = 6.dp).clickable { clipboard.setText(AnnotatedString(value)) })
        }
    }
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange)
    }
}

private fun formatUnixTimestamp(ts: String): String {
    return try {
        val instant = JavaInstant.ofEpochSecond(ts.toLong())
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant)
    } catch (_: Exception) { ts }
}
