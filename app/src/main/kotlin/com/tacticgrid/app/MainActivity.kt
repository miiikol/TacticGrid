package com.tacticgrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tacticgrid.app.ui.GameScreen
import com.tacticgrid.app.ui.theme.TacticGridTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TacticGridTheme {
                GameScreen()
            }
        }
    }
}
