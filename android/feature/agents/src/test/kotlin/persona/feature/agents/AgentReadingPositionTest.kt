package persona.feature.agents

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import persona.core.contracts.Session
import persona.core.domain.sort.AgentSession

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentReadingPositionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun liveRemovalKeepsTheClosestSurvivingRowAtItsOldHeight() {
        val initialRows = (0 until 50).map { number ->
            TreeRowEntry(AgentSession(Session("demo", "s-$number")), depth = 0, first = false, last = false, located = false)
        }
        val first = initialRows[20]
        val neighbor = initialRows[21]
        val reader = initialRows[22]
        var rows by mutableStateOf(initialRows)
        rule.setContent {
            val state = rememberLazyListState()
            KeepAgentReadingPosition(state, rows, paginationFooter = false, scope = emptyList())
            LazyColumn(Modifier.height(276.dp), state = state) {
                item("header") { Box(Modifier.height(52.dp)) }
                items(rows, key = { it.key }) { row ->
                    Box(Modifier.fillMaxWidth().height(if (row == neighbor) 40.dp else 52.dp).testTag(row.key))
                }
            }
        }

        val list = rule.onNode(hasScrollToKeyAction())
        list.performScrollToKey(first.key)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val oldReader = rule.onNodeWithTag(reader.key).assertIsDisplayed()
        val oldReaderBounds = oldReader.fetchSemanticsNode().boundsInRoot
        assertTrue("the removed row crosses the viewport midpoint", viewport.center.y in oldReaderBounds.top..oldReaderBounds.bottom)
        val closest = rule.onNodeWithTag(neighbor.key).assertIsDisplayed()
        val before = closest.fetchSemanticsNode().boundsInRoot.top

        // The shorter row above the reader was closer than the row below. Move it beyond the old first key's viewport.
        rule.runOnIdle {
            rows = rows.filterNot { it == reader || it == neighbor }.toMutableList().apply { add(8, neighbor) }
        }

        oldReader.assertDoesNotExist()
        val after = closest.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals("the nearest surviving row keeps its old position", before, after, 2f)
    }
}
