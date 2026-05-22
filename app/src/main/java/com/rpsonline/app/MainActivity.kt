package com.rpsonline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rpsonline.app.navigation.RpsNavGraph
import com.rpsonline.app.ui.theme.RpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RpsTheme {
                RpsNavGraph()
            }
        }
    }
}
