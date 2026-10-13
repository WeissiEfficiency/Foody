package de.foody.feature.heute

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeuteScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun zeigtTitel() {
        compose.setContent { MaterialTheme { HeuteScreen() } }
        compose.onNodeWithText("Heute").assertIsDisplayed()
    }
}
