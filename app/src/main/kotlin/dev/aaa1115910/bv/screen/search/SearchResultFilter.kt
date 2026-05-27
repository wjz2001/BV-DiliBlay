package dev.aaa1115910.bv.screen.search

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.repositories.SearchFilterDuration
import dev.aaa1115910.biliapi.repositories.SearchFilterOrderType
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.wjzFocus
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.util.Partition
import dev.aaa1115910.bv.util.PartitionUtil
import dev.aaa1115910.bv.ui.theme.C

private enum class SearchResultFilterRow {
    Order,
    Duration,
    Partition,
    ChildPartition
}

private fun searchResultFilterNodeId(
    row: SearchResultFilterRow,
    key: String
) = WjzFocusNodeId("search/result/filter/${row.name.lowercase()}/$key")

private val SearchResultFilterDialogScopeId = WjzFocusScopeId("search/result/filter")
private val SearchResultFilterDialogContainerNodeId = WjzFocusNodeId("search/result/filter/container")

@Composable
fun SearchResultVideoFilter(
    modifier: Modifier = Modifier,
    show: Boolean,
    sourceScopeId: WjzFocusScopeId? = null,
    onHideFilter: () -> Unit,
    selectedOrder: SearchFilterOrderType,
    selectedDuration: SearchFilterDuration,
    selectedPartition: Partition?,
    selectedChildPartition: Partition?,
    onSelectedOrderChange: (SearchFilterOrderType) -> Unit,
    onSelectedDurationChange: (SearchFilterDuration) -> Unit,
    onSelectedPartitionChange: (Partition?) -> Unit,
    onSelectedChildPartitionChange: (Partition?) -> Unit,
) {
    val context = LocalContext.current
    val partitions = remember { PartitionUtil.partitions }

    val filterRowSpace = 8.dp

    if (show) {
        TvAlertDialog(
            modifier = modifier
                .fillMaxWidth(0.8f),
            onDismissRequest = onHideFilter,
            title = { Text(text = stringResource(R.string.filter_dialog_title)) },
            sourceScopeId = sourceScopeId,
            dialogScopeId = SearchResultFilterDialogScopeId,
            containerNodeId = SearchResultFilterDialogContainerNodeId,
            text = {
                Column {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(filterRowSpace)
                    ) {
                        items(
                            items = SearchFilterOrderType.webFilters,
                            key = { orderType -> orderType.name }
                        ) { orderType ->
                            FilterDialogFilterChip(
                                nodeId = searchResultFilterNodeId(
                                    row = SearchResultFilterRow.Order,
                                    key = orderType.name
                                ),
                                fallback = orderType == selectedOrder,
                                selected = orderType == selectedOrder,
                                onClick = { onSelectedOrderChange(orderType) },
                                label = { Text(text = orderType.getDisplayName(context)) },
                            )
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(filterRowSpace)
                    ) {
                        items(
                            items = SearchFilterDuration.entries,
                            key = { duration -> duration.name }
                        ) { duration ->
                            FilterDialogFilterChip(
                                nodeId = searchResultFilterNodeId(
                                    row = SearchResultFilterRow.Duration,
                                    key = duration.name
                                ),
                                fallback = duration == selectedDuration,
                                selected = duration == selectedDuration,
                                onClick = { onSelectedDurationChange(duration) },
                                label = { Text(text = duration.getDisplayName(context)) }
                            )
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(filterRowSpace)
                    ) {
                        item {
                            FilterDialogFilterChip(
                                nodeId = searchResultFilterNodeId(
                                    row = SearchResultFilterRow.Partition,
                                    key = "all"
                                ),
                                fallback = selectedPartition == null,
                                selected = null == selectedPartition,
                                onClick = {
                                    onSelectedPartitionChange(null)
                                    onSelectedChildPartitionChange(null)
                                },
                                label = { Text(text = "全部分区") }
                            )
                        }
                        items(
                            items = partitions,
                            key = { partition -> partition.tid }
                        ) { partition ->
                            FilterDialogFilterChip(
                                nodeId = searchResultFilterNodeId(
                                    row = SearchResultFilterRow.Partition,
                                    key = partition.tid.toString()
                                ),
                                fallback = partition == selectedPartition,
                                selected = partition == selectedPartition,
                                onClick = {
                                    onSelectedPartitionChange(partition)
                                    onSelectedChildPartitionChange(null)
                                },
                                label = { Text(text = partition.strRes) }
                            )
                        }
                    }
                    AnimatedVisibility(visible = selectedPartition != null) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(filterRowSpace)
                        ) {
                            items(
                                items = selectedPartition?.children ?: emptyList(),
                                key = { partition -> partition.tid }
                            ) { partition ->
                                FilterDialogFilterChip(
                                    nodeId = searchResultFilterNodeId(
                                        row = SearchResultFilterRow.ChildPartition,
                                        key = partition.tid.toString()
                                    ),
                                    fallback = partition == selectedChildPartition,
                                    selected = partition == selectedChildPartition,
                                    onClick = {
                                        onSelectedChildPartitionChange(
                                            if (partition != selectedChildPartition) partition
                                            else null
                                        )
                                    },
                                    label = { Text(text = partition.strRes) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    BackHandler(
        enabled = show,
        onBack = onHideFilter
    )
}

@Composable
private fun FilterDialogFilterChip(
    modifier: Modifier = Modifier,
    nodeId: WjzFocusNodeId,
    fallback: Boolean = false,
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }

    FilterChip(
        modifier = modifier
            .wjzFocus(
                id = nodeId.toDialogLocalFocusId(),
                layer = WjzFocusLayer.Dialog,
                fallback = fallback,
                onFocusChanged = { hasFocus = it }
            ),
        selected = selected,
        onClick = onClick,
        label = label,
        border = if (hasFocus) FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = C.selectedBorder,
            borderWidth = 2.dp,
            selectedBorderColor = C.selectedBorder,
            selectedBorderWidth = 2.dp
        )
        else FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected
        )
    )
}

private fun WjzFocusNodeId.toDialogLocalFocusId(): String {
    val scopePrefix = "${SearchResultFilterDialogScopeId.value}/"
    return if (value.startsWith(scopePrefix)) {
        value.removePrefix(scopePrefix)
    } else {
        value
    }
}

fun SearchFilterOrderType.getDisplayName(context: Context) = when (this) {
    SearchFilterOrderType.ComprehensiveSort -> context.getString(R.string.search_result_filter_order_type_comprehensive_sort)
    SearchFilterOrderType.MostClicks -> context.getString(R.string.search_result_filter_order_type_most_clicks)
    SearchFilterOrderType.LatestPublish -> context.getString(R.string.search_result_filter_order_type_latest_publish)
    SearchFilterOrderType.MostDanmaku -> context.getString(R.string.search_result_filter_order_type_most_danmaku)
    SearchFilterOrderType.MostFavorites -> context.getString(R.string.search_result_filter_order_type_most_favorites)
    SearchFilterOrderType.MostComment -> "最多评论"
    SearchFilterOrderType.MostLikes -> "最多点赞"
}

fun SearchFilterDuration.getDisplayName(context: Context) = when (this) {
    SearchFilterDuration.All -> context.getString(R.string.search_result_filter_duration_all)
    SearchFilterDuration.LessThan10Minutes -> context.getString(R.string.search_result_filter_duration_less_than_10)
    SearchFilterDuration.Between10And30Minutes -> context.getString(R.string.search_result_filter_duration_10_to_30)
    SearchFilterDuration.Between30And60Minutes -> context.getString(R.string.search_result_filter_duration_30_to_60)
    SearchFilterDuration.MoreThan60Minutes -> context.getString(R.string.search_result_filter_duration_more_than_60)
}
