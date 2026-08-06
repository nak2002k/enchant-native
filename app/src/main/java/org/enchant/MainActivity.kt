package org.enchant

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.enchant.auth.AuthNavDisplay
import org.enchant.auth.AuthViewModel
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.enchant.ui.theme.NotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        handleCallIntent(intent)
        setContent {
            NotionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        val data = intent?.data ?: return
        when (data.host) {
            "call-link" -> {
                val roomId = data.pathSegments.firstOrNull()
                if (roomId != null) {
                    // Launch call link join screen
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var diReady by remember { mutableStateOf(false) }
    var initFailed by remember { mutableStateOf(false) }
    var authFlowComplete by remember { mutableStateOf(false) }
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val registrationState by authViewModel.registrationState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Debug agent: API can flip between auth flow and main app (debug APK only).
    LaunchedEffect(Unit) {
        if (!org.enchant.BuildConfig.DEBUG) return@LaunchedEffect
        runCatching {
            val clazz = Class.forName("org.enchant.agent.AgentRuntime")
            val field = clazz.getDeclaredField("onSetAuthFlowComplete")
            field.isAccessible = true
            field.set(null, { complete: Boolean -> authFlowComplete = complete })
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            val intent = Intent(context, org.enchant.core.network.WebSocketForegroundService::class.java)
            runCatching { context.startForegroundService(intent) }
        }
    }

    LaunchedEffect(Unit) {
        var attempts = 0
        while (!DI.isInitialized && attempts < 100) {
            delay(100)
            attempts++
        }
        if (!DI.isInitialized) {
            initFailed = true
        } else {
            diReady = true
        }
    }

    // On first composition, if the user was already fully registered (returning user),
    // skip the auth flow and go straight to the main app.
    LaunchedEffect(diReady) {
        if (diReady &&
            authState is AuthState.Authenticated &&
            registrationState is RegistrationState.Complete
        ) {
            authFlowComplete = true
        }
    }

    // Splash: show while booting, then for a beat once the app is ready.
    var splashDone by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }
    LaunchedEffect(diReady) {
        if (diReady && !splashFinished) {
            delay(2350)
            splashFinished = true
            splashDone = true
        }
    }

    if (initFailed) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Initialization failed")
                Spacer(Modifier.height(8.dp))
                Text("Check logs for details")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { initFailed = false }) {
                    Text("Retry")
                }
            }
        }
    } else if (!splashDone) {
        EnchantSplash(
            onFinished = { splashDone = true }
        )
    } else if (authFlowComplete) {
        MainNavDisplay()
    } else {
        AuthNavDisplay(
            viewModel = authViewModel,
            onAuthComplete = { authFlowComplete = true }
        )
    }
}