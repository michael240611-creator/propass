// file: app/src/main/java/com/plum/vpn/MainActivity.kt
package com.plum.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plum.vpn.ui.ClaudeIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val BgBlack = Color(0xFF000000)
private val CardDark = Color(0xFF141414)
private val Accent = Color(0xFF00FF88)
private val TextGray = Color(0xFF888888)

class MainActivity : ComponentActivity() {
    private var selectedStrategyId = ""
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startVpnService(selectedStrategyId)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StrategyRegistry.addCustom(Prefs.getCustomCommand(this))

        setContent {
            MaterialTheme(
                typography = Typography(
                    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                ),
                colorScheme = darkColorScheme(background = BgBlack, surface = CardDark)
            ) {
                AppNavigation(onToggleVpn = { enable, stratId ->
                    selectedStrategyId = stratId
                    if (enable) {
                        val intent = VpnService.prepare(this)
                        if (intent != null) vpnPermissionLauncher.launch(intent) else startVpnService(selectedStrategyId)
                    } else stopVpnService()
                })
            }
        }
    }

    private fun startVpnService(strategyId: String) {
        val intent = Intent(this, PlumVpnService::class.java).apply {
            action = PlumVpnService.ACTION_START
            putExtra(PlumVpnService.EXTRA_STRATEGY, strategyId)
        }
        startForegroundService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, PlumVpnService::class.java).apply {
            action = PlumVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun AppNavigation(onToggleVpn: (Boolean, String) -> Unit) {
    var currentScreen by remember { mutableStateOf("main") }
    when (currentScreen) {
        "main" -> MainScreen(onToggleVpn, onOpenSettings = { currentScreen = "settings" })
        "settings" -> SettingsScreen(onBack = { currentScreen = "main" })
    }
}

@Composable
fun MainScreen(onToggleVpn: (Boolean, String) -> Unit, onOpenSettings: () -> Unit) {
    var isVpnEnabled by remember { mutableStateOf(false) }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf(StrategyRegistry.getDefault()) }
    
    var isTesting by remember { mutableStateOf(false) }
    var testProgress by remember { mutableStateOf(0) }
    var bestStrategy by remember { mutable
