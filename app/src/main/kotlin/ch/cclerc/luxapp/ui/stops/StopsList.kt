package ch.cclerc.luxapp.ui.stops

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.SearchResult

@Composable
fun StopsList(
    stops: List<SearchResult>,
    isSearching: Boolean,
    onOpenStop: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val uniqueStops = remember(stops) {
        val seen = mutableSetOf<String>()
        stops.filter { seen.add(it.id) }
    }
    val listState = rememberLazyListState()

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 100.dp + navBottom)
    ) {
        itemsIndexed(
            items = uniqueStops,
            key = { _, stop -> stop.id }
        ) { index, stop ->
            StopRowView(
                stop = stop,
                isSearching = isSearching,
                modifier = Modifier.scaleClickable { onOpenStop(stop) }
            )
            if (index != uniqueStops.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = LuxTheme.colors.separator
                )
            }
        }
    }
}
