package org.devbucket.gRide

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.GRideApp
import org.devbucket.gRide.ui.theme.GRideTheme
import org.devbucket.gRide.ui.theme.isDarkTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
        }
        enableEdgeToEdge()
        setContent {
            val settings by vm.store.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings)
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            GRideTheme(settings) { GRideApp(vm) }
        }
    }
}
