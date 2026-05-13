package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRowDefaults

object MainTopTabDefaults {
    val TabSeparatorWidth = 12.dp
    val TabRowHorizontalPadding = 12.dp
    val TabContentHeight = MainChromeDefaults.TopNavTabHeight
    // val TabContentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    val TabContentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
}

@Composable
fun MainTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MainChromeDefaults.Size),
        content = content
    )
}

@Composable
fun mainTopTabIndicator(selectedTabIndex: Int): @Composable (List<DpRect>, Boolean) -> Unit {
    return { tabPositions, doesTabRowHaveFocus ->
        tabPositions.getOrNull(selectedTabIndex)?.let { currentTabPosition ->
            TabRowDefaults.PillIndicator(
                currentTabPosition = currentTabPosition,
                doesTabRowHaveFocus = doesTabRowHaveFocus,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
fun mainTopTabColors() = TabDefaults.pillIndicatorTabColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
    focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
)

@Composable
fun MainTopTabSeparator() {
    Spacer(modifier = Modifier.width(MainTopTabDefaults.TabSeparatorWidth))
}
