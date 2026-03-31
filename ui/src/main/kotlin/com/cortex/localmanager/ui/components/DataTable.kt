package com.cortex.localmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.theme.CortexColors

data class TableColumn<T>(
    val header: String,
    val weight: Float = 1f,
    val sortKey: ((T) -> Comparable<*>?)? = null,
    val content: @Composable (T) -> Unit
)

@Composable
fun <T> DataTable(
    items: List<T>,
    columns: List<TableColumn<T>>,
    onRowClick: ((T) -> Unit)? = null,
    selectedItem: T? = null,
    modifier: Modifier = Modifier
) {
    var sortColumnIndex by remember { mutableStateOf(-1) }
    var sortAscending by remember { mutableStateOf(true) }

    val sortedItems = remember(items, sortColumnIndex, sortAscending) {
        if (sortColumnIndex >= 0) {
            val sortKey = columns.getOrNull(sortColumnIndex)?.sortKey
            if (sortKey != null) {
                val sorted = items.sortedWith(compareBy { sortKey(it) as? Comparable<Any> })
                if (sortAscending) sorted else sorted.reversed()
            } else items
        } else items
    }

    Column(modifier = modifier) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CortexColors.SurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            columns.forEachIndexed { index, column ->
                Box(modifier = Modifier.weight(column.weight)) {
                    Text(
                        text = column.header + when {
                            sortColumnIndex == index && sortAscending -> " \u25B2"
                            sortColumnIndex == index && !sortAscending -> " \u25BC"
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sortColumnIndex == index) CortexColors.PaloAltoOrange
                        else CortexColors.TextSecondary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.clickable {
                            if (column.sortKey != null) {
                                if (sortColumnIndex == index) {
                                    sortAscending = !sortAscending
                                } else {
                                    sortColumnIndex = index
                                    sortAscending = true
                                }
                            }
                        }
                    )
                }
            }
        }

        // Data rows
        LazyColumn {
            itemsIndexed(sortedItems) { index, item ->
                val isSelected = item == selectedItem
                val bgColor = when {
                    isSelected -> CortexColors.OrangeDim
                    index % 2 == 0 -> CortexColors.Surface
                    else -> CortexColors.Background
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .then(
                            if (onRowClick != null) Modifier.clickable { onRowClick(item) }
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    columns.forEach { column ->
                        Box(modifier = Modifier.weight(column.weight)) {
                            column.content(item)
                        }
                    }
                }
            }
        }
    }
}
