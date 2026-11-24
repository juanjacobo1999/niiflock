package com.niiflock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    // Sube cada vez que se vuelve a la pantalla, para releer el estado del servicio de accesibilidad.
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiifLockTheme {
                MainScreen(refreshTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }
}
