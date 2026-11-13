package io.github.ricardomonroy.canalcero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.ricardomonroy.canalcero.ui.CanalCeroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CanalCeroTheme { Inicio() } }
    }
}

@Composable
private fun Inicio() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CanalCero", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Cuando hay cero señal", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
