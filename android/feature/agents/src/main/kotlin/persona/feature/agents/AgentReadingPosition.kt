package persona.feature.agents

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlin.math.abs

private data class AgentReadingRow(val key: Any, val offset: Int)

private data class AgentReadingCorrection(val key: Any, val index: Int, val offset: Int, val measuredOffset: Int?)

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
        var previousRows = emptyList<LazyListItemInfo>()
        var lastCorrection: AgentReadingCorrection? = null
        snapshotFlow { Triple(state.layoutInfo, state.isScrollInProgress, state.canScrollBackward) }
            .collect { (layout, scrolling, scrolled) ->
                if (scrolling) {
                    reading = null
                    previousRows = emptyList()
                    lastCorrection = null
                    return@collect
                }

                val entries = currentTree.value
                // The tree is contiguous before its optional footer. Headers and notices above it are dynamic.
                val start = layout.totalItemsCount - entries.size - (if (hasFooter.value) 1 else 0)
                if (start < 0) return@collect
                fun isTreeRow(item: LazyListItemInfo) = entries.getOrNull(item.index - start)?.key == item.key
                val visible = layout.visibleItemsInfo.filter { it.index - start in entries.indices }
                // The list can still be reporting its previous layout when the new tree is composed.
                if (visible.isEmpty() || visible.any { !isTreeRow(it) }) return@collect
                val bottom = layout.viewportEndOffset - layout.afterContentPadding
                val rows = visible.filter { it.offset + it.size > 0 && it.offset < bottom }

                val current = reading
                if (current == null) {
                    if (!scrolled) return@collect
                    val middle = bottom / 2
                    val item = rows.lastOrNull { it.offset <= middle } ?: rows.firstOrNull()
                    reading = item?.let { AgentReadingRow(it.key, it.offset) }
                } else {
                    var anchor: AgentReadingRow? = current
                    var laidOut = rows.firstOrNull { it.key == current.key }
                    var index = laidOut?.index ?: entries.indexOfFirst { it.key == current.key }.takeIf { it >= 0 }?.plus(start)
                    if (index == null) {
                        // A fold or removal can hide the reading row. Keep the closest surviving neighbor instead.
                        val neighbor = previousRows.sortedBy { abs(it.offset - current.offset) }
                            .firstNotNullOfOrNull { old ->
                                entries.indexOfFirst { it.key == old.key }.takeIf { it >= 0 }?.let { old to (start + it) }
                            }
                        anchor = neighbor?.first?.let { AgentReadingRow(it.key, it.offset) }
                        index = neighbor?.second
                        laidOut = rows.firstOrNull { it.key == anchor?.key }
                        reading = anchor
                    }
                    if (anchor != null && index != null && laidOut?.offset != anchor.offset) {
                        val correction = AgentReadingCorrection(anchor.key, index, -anchor.offset, laidOut?.offset)
                        // A list whose end clamps the offset can report the same result after a request.
                        if (correction != lastCorrection && !state.isScrollInProgress) {
                            lastCorrection = correction
                            state.requestScrollToItem(index, correction.offset)
                            return@collect
                        }
                    } else {
                        lastCorrection = null
                    }
                }
                previousRows = rows
            }
    }
}
