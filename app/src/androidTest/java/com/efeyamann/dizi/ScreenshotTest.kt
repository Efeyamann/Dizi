package com.efeyamann.dizi

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Not a real test: renders the main screens with sample data so the UI can be reviewed from CI screenshots. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()

    @Test fun screens() {
        val day = LocalDate.now()
        Store(inst.targetContext).shows = listOf(
            Show(93405, "Squid Game", "/1QdXdRYfktUSONkl1oD5gc6Be0s.jpg", 3, day.plusDays(1).toString(), 4, 1, newSeason = true),
            Show(91239, "Bridgerton", "/uXTg565ahu9RwonCX1V2Hex1NU6.jpg", 5, day.plusDays(4).toString(), 5, 3),
            Show(85552, "Euphoria", "/aJrG7OkoTMPWG5c8opz8a93AZPY.jpg", 3, day.plusDays(40).toString(), 3, 6),
            Show(97175, "Fate: The Winx Saga", "/oHj6guMrLfQcBzo3uxwBJc8Y736.jpg", 2, ended = true),
            Show(127865, "Kastanjemanden", "/naCYfDx93zfXT6MwmvTwBOkIUy9.jpg", 2),
            Show(1, "A Show With No Poster And A Long Name", null, 1),
            Show(88329, "Hawkeye", "/ct5pNE5dDHryHLDnxyZPYcqO1sz.jpg", 1, status = Status.DONE, ended = true),
            Show(90260, "I Am Not Okay with This", "/kf3yX0ILNlLJ42X3lX2iYJ3QRp6.jpg", 1, status = Status.DONE, ended = true),
            Show(2, "Severance", null, 2, status = Status.WATCHLIST),
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("1-yarida")
            compose.onNodeWithText("Bitti", substring = true).performClick()
            shot("2-bitti")
            compose.onAllNodesWithText("Hawkeye").onFirst().performClick()
            shot("3-sayfa")
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(4000) // posters load over the network
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
