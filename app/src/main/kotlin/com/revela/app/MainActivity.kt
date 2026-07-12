package com.revela.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.revela.app.ui.RevelaRoot
import com.revela.app.ui.theme.RevelaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RevelaApp).container
        setContent {
            RevelaTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RevelaRoot(container)
                }
            }
        }
    }
}
