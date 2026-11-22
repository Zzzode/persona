package persona.feature.chat

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import persona.core.designsystem.AppStyle
import persona.core.designsystem.PersonaTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-w400dp-h800dp-xhdpi")
class AgentChatJumpToLatestTest {
    @get:Rule val rule = createComposeRule()

    @Test fun arrowRestsOnThePageWithoutAContainerInEveryStyle() {
        var style by mutableStateOf(AppStyle.Glass)
        var dark by mutableStateOf(false)
        var background = Color.Unspecified
        rule.setContent {
            PersonaTheme(darkTheme = dark, reduceMotion = true, style = style) {
                val pageColor = PersonaTheme.palette.background
                SideEffect { background = pageColor }
                Box(Modifier.fillMaxSize().background(pageColor), contentAlignment = Alignment.Center) {
                    AgentChatJumpToLatest(count = 0, onClick = {})
                }
            }
        }

        val output = File(System.getProperty("persona.shots") ?: "build/agent-jump-shots").also { it.mkdirs() }
        for (appStyle in AppStyle.entries) for (darkTheme in listOf(false, true)) {
            rule.runOnIdle { style = appStyle; dark = darkTheme }
            val jump = rule.onNodeWithContentDescription("Jump to latest").assertIsDisplayed()
            val bitmap = jump.captureToImage().asAndroidBitmap()
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val expectedBackground = rule.runOnIdle { background }
            val label = "${appStyle.name.lowercase()}-${if (darkTheme) "dark" else "light"}"

            // The hit area surrounds the arrow. The samples stay inside the former pill and outside the glyph.
            val marginSamples = listOf(
                centerX / 2 to centerY, centerX * 3 / 2 to centerY,
                centerX to centerY / 2, centerX to centerY * 3 / 2,
            )
            for ((x, y) in marginSamples) {
                assertEquals("$label background at ($x, $y)", expectedBackground.toArgb(), bitmap.getPixel(x, y))
            }
            val pageLuminance = expectedBackground.luminance()
            assertTrue("$label arrow needs a visible stroke", (centerY / 2..centerY * 3 / 2).any { y ->
                val inkLuminance = Color(bitmap.getPixel(centerX, y)).luminance()
                (maxOf(inkLuminance, pageLuminance) + 0.05f) / (minOf(inkLuminance, pageLuminance) + 0.05f) >= 3f
            })
            File(output, "agent-jump-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun keepsTheExactCountSpokenLabelAndTouchableArea() {
        var clicks = 0
        rule.setContent {
            PersonaTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AgentChatJumpToLatest(count = 123, onClick = { clicks++ })
                }
            }
        }

        rule.onNodeWithText("123").assertIsDisplayed()
        val jump = rule.onNodeWithContentDescription("Jump to latest, 123 new messages")
        val bounds = jump.fetchSemanticsNode().boundsInRoot
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("Jump to latest touch target is $bounds", bounds.width >= minimum && bounds.height >= minimum)
        jump.performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, clicks) }
    }
}
