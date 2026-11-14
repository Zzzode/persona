package persona.feature.agents

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

private data class AgentReadingRow(val key: Any, val offset: Int)

private data class AgentReadingCorrection(val key: Any, val index: Int, val offset: Int, val measuredOffset: Int?)

private data class AgentReadingLayout(
    val layout: LazyListLayoutInfo,
    val scrolling: Boolean,
    val canScrollBackward: Boolean,
    val entries: List<AgentTreeEntry>,
    val hasFooter: Boolean,
)

/** Keep the middle row at its screen position while live updates move or resize the items around it. */
@Composable
internal fun KeepAgentReadingPosition(
    state: LazyListState,
    tree: List<AgentTreeEntry>,
    paginationFooter: Boolean,
    scope: List<Any?>,
) {
    val currentTree = rememberUpdatedState(tree)
    val hasFooter = rememberUpdatedState(paginationFooter)
    LaunchedEffect(state, scope) {
        var reading: AgentReadingRow? = null
        var settledRows = emptyList<AgentReadingRow>()
        var lastCorrection: AgentReadingCorrection? = null
        snapshotFlow {
            AgentReadingLayout(state.layoutInfo, state.isScrollInProgress, state.canScrollBackward,
                currentTree.value, hasFooter.value)
        }.collect { (layout, scrolling, scrolled, entries, footer) ->
            if (scrolling) {
                reading = null
                settledRows = emptyList()
                lastCorrection = null
                return@collect
            }

            // The tree is contiguous before its optional footer. Headers and notices above it are dynamic.
            val start = layout.totalItemsCount - entries.size - (if (footer) 1 else 0)
            if (start < 0) return@collect
            val visible = layout.visibleItemsInfo.filter { it.index - start in entries.indices }
            // Keep the last settled geometry until Compose reports a layout that agrees with the new tree.
            if (visible.isEmpty() || visible.any { entries[it.index - start].key != it.key }) return@collect
            val top = layout.viewportStartOffset + layout.beforeContentPadding
            val bottom = layout.viewportEndOffset - layout.afterContentPadding
            val rows = visible.filter { it.offset + it.size > top && it.offset < bottom }
            // A clipped row at either edge cannot be relied on to restore its position after it moves.
            val fullyVisible = rows.filter { it.offset >= top && it.offset + it.size <= bottom }

            val current = reading
            if (current == null) {
                if (!scrolled) return@collect
                val middle = top + (bottom - top) / 2
                val item = fullyVisible.lastOrNull { it.offset <= middle } ?: fullyVisible.firstOrNull()
                reading = item?.let { AgentReadingRow(it.key, it.offset) }
            } else {
                var anchor: AgentReadingRow? = current
                var laidOut = rows.firstOrNull { it.key == current.key }
                var index = laidOut?.index ?: entries.indexOfFirst { it.key == current.key }.takeIf { it >= 0 }?.plus(start)
                if (index == null) {
                    // Keep a surviving neighbor at its position from before the removal, even if it moved offscreen.
                    val neighbor = settledRows.sortedBy { abs(it.offset - current.offset) }
                        .firstNotNullOfOrNull { old ->
                            entries.indexOfFirst { it.key == old.key }.takeIf { it >= 0 }?.let { old to (start + it) }
                        }
                    anchor = neighbor?.first
                    index = neighbor?.second
                    laidOut = rows.firstOrNull { it.key == anchor?.key }
                    reading = anchor
                }
                // An offscreen anchor needs to be restored by key; a visible one only needs an offset correction.
                if (anchor != null && index != null && (laidOut == null || laidOut.offset != anchor.offset)) {
                    val correction = AgentReadingCorrection(anchor.key, index, -anchor.offset, laidOut?.offset)
                    // A list whose end clamps the offset can report the same result after a request.
                    if (correction != lastCorrection && !state.isScrollInProgress) {
                        lastCorrection = correction
                        state.requestScrollToItem(index, correction.offset)
                    }
                    // A pending or clamped correction must not overwrite the previous reading positions.
                    return@collect
                }
            }
            lastCorrection = null
            settledRows = fullyVisible.map { AgentReadingRow(it.key, it.offset) }
        }
    }
}
