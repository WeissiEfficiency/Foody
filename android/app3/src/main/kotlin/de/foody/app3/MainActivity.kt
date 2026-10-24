package de.foody.app3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund

/** Einzige Activity der V3; die Navigation hängt in Task 4 ein. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FoodyTheme { FoodyHintergrund { Text("Foody") } } }
    }
}
