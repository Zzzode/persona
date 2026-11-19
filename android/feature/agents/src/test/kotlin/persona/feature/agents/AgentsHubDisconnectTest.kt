package persona.feature.agents

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import persona.core.contracts.GroupKeys
import persona.core.contracts.Machine
import persona.core.contracts.Session
import persona.core.contracts.api.ConnectionState
import persona.core.contracts.api.HubGroupInfo
import persona.core.contracts.api.SessionsRepository
import persona.core.designsystem.AppStyle
import persona.core.designsystem.LocalHomeChrome
import persona.core.designsystem.PersonaTheme
import persona.core.domain.sort.AgentGrouping

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en-w400dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentsHubDisconnectTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun survivingTitleDrawsAfterDisconnectInLightMode() = disconnect(dark = false)
    @Test fun survivingTitleDrawsAfterDisconnectInDarkMode() = disconnect(dark = true)

    private fun disconnect(dark: Boolean) {
        val removed = HubGroupInfo("g00000000", "Home Hub", null, false, ConnectionState.WaitingForNetwork, emptyList())
        val remaining = HubGroupInfo("g11111111", "Work Hub", "g11111111", true, ConnectionState.Online, emptyList())
        val groups = MutableStateFlow(listOf(removed, remaining))
        val work = Machine(GroupKeys.machine("work", remaining.namespace), "Workstation", online = true)
        val home = Machine("home", "Home computer", online = false)
        val retained = (0 until 14).map { index ->
            Session(work.id, "work-$index", title = "Work task $index",
                cwd = "/sample/repository-$index", repoRoot = "/sample/repository-$index", state = "working",
                updatedAt = "2026-01-01T00:${index.toString().padStart(2, '0')}:00Z")
        }
        val departed = Session(home.id, "home-session", title = "Home task", cwd = "/sample/home", state = "working")
        val dependencies = agentsDependencies(emptyList(), emptyList())
        val repository = object : SessionsRepository by dependencies.sessions() {
            override val sessions = MutableStateFlow(retained + departed)
            override val machines = MutableStateFlow(listOf(work, home))
        }
        val model = AgentsViewModel(repository, dependencies.interactions(), dependencies.connection(),
            dependencies.sender(), dependencies.preferences())
        model.grouping(AgentGrouping.Repo)
        rule.setContent {
            val hubGroups by groups.collectAsState()
            DisposableEffect(model) { onDispose { model.viewModelScope.cancel() } }
            PersonaTheme(style = AppStyle.Glass, darkTheme = dark) {
                CompositionLocalProvider(LocalHomeChrome provides agentsHomeChrome) {
                    AgentsScreen(model, NoNavigation, AgentsMetadata(), groups = hubGroups)
                }
            }
        }
        rule.waitUntil(5_000) { model.page.value.loaded && model.page.value.settings.grouping == AgentGrouping.Repo }
        val allHubs = rule.activity.getString(R.string.agents_chip_all_hubs)
        rule.onNodeWithContentDescription(allHubs).assertIsDisplayed()

        val list = rule.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val title = retained[4].displayTitle
        list.performScrollToNode(hasText(title))
        assertTitleDraws(title)

        // Settings publishes the new membership before the merged session feed catches up.
        rule.runOnIdle { groups.value = listOf(remaining) }
        assertTitleDraws(title)
        rule.runOnIdle {
            repository.machines.value = listOf(work)
            repository.sessions.value = retained
        }
        assertEquals(retained.map { it.key }.toSet(), rule.runOnIdle { model.page.value.sessions.map { it.key }.toSet() })
        assertTitleDraws(title)

        list.performScrollToIndex(0)
        rule.onNodeWithContentDescription(allHubs).assertDoesNotExist()
    }

    private fun assertTitleDraws(title: String) {
        val row = rule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        // A transparent row still passes the semantics check. A rendered title has both ink and its background.
        val bitmap = row.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("The title \"$title\" is drawn",
            pixels.any { Color.luminance(it) < 0.25f } && pixels.any { Color.luminance(it) > 0.75f })
    }
}
