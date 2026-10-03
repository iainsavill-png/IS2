package uk.railboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.railboard.app.ui.DeparturesScreen
import uk.railboard.app.ui.theme.RailBoardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RailBoardTheme {
                DeparturesScreen()
            }
        }
    }
}
