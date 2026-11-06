package com.afrizalmy.hybridbrain

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.afrizalmy.hybridbrain.ui.components.ChatScreen
import com.afrizalmy.hybridbrain.ui.theme.HybridBrainTheme
import com.afrizalmy.hybridbrain.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            HybridBrainTheme {
                ChatScreen(viewModel = viewModel)
            }
        }
    }
}
