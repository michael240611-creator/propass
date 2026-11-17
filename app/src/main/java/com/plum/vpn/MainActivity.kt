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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgBlack = Color(0xFF000000)
private val CardDark = Color(0xFF1A1A1A)
private val Accent = Color(0xFF00FF88)

class MainActivity : ComponentActivity() {

    private var selectedStrategyId = ""
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService(selectedStrategyId)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                typography = Typography(
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp)
                ),
                colorScheme = darkColorScheme(background = BgBlack, surface = CardDark)
            ) {
                AppNavigation(
                    onToggleVpn = { enable, stratId ->
                        selectedStrategyId = stratId
                        if (enable) {
                            val intent = VpnService.prepare(this)
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                startVpnService(selectedStrategyId)
                            }
                        } else {
                            stopVpnService()
                        }
                    }
                )
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
    var selectedStrategy by remember { mutableStateOf(StrategyRegistry.getDefault()) }
    
    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("PLUM VPN", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isVpnEnabled) "Соединение защищено" else "Не подключено",
            color = Accent, fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                isVpnEnabled = !isVpnEnabled
                onToggleVpn(isVpnEnabled, selectedStrategy.id)
            },
            modifier = Modifier.size(180.dp),
            shape = RoundedCornerShape(90.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isVpnEnabled) Accent else CardDark)
        ) {
            Text(
                text = if (isVpnEnabled) "STOP" else "START",
                color = if (isVpnEnabled) BgBlack else Accent,
                fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable { showStrategyDialog = true },
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Стратегия", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(selectedStrategy.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (showStrategyDialog) {
            AlertDialog(
                onDismissRequest = { showStrategyDialog = false },
                containerColor = CardDark,
                title = { Text("Выбор стратегии", color = Color.White) },
                text = {
                    LazyColumn {
                        items(StrategyRegistry.all()) { strat ->
                            Text(
                                text = strat.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedStrategy = strat
                                    showStrategyDialog = false
                                }.padding(vertical = 12.dp),
                                color = Color.White
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStrategyDialog = false }) {
                        Text("Закрыть", color = Accent)
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedDns by remember { mutableStateOf(Prefs.getDns(context)) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var whitelist by remember { mutableStateOf(Prefs.getWhitelist(context)) }
    
    val installedApps = remember { AppList.getInstalledApps(context) }
    val pm = context.packageManager

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Назад", color = Accent) }
            Spacer(modifier = Modifier.weight(1f))
            Text("Настройки", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(60.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("DNS Сервер", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showDnsDialog = true },
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selectedDns, color = Color.White)
                Text("Изменить", color = Accent)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Белый список (исключения)", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(installedApps) { app ->
                val isChecked = whitelist.contains(app.packageName)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newList = whitelist.toMutableSet()
                        if (isChecked) newList.remove(app.packageName) else newList.add(app.packageName)
                        whitelist = newList
                        Prefs.setWhitelist(context, newList)
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(pm.getApplicationLabel(app).toString(), color = Color.White, fontSize = 16.sp)
                }
            }
        }

        if (showDnsDialog) {
            AlertDialog(
                onDismissRequest = { showDnsDialog = false },
                containerColor = CardDark,
                title = { Text("Выбор DNS", color = Color.White) },
                text = {
                    Column {
                        DnsRegistry.servers.forEach { dns ->
                            Text(
                                text = dns.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (dns.name == "Свой") {
                                        // Для простоты ставим Cloudflare. В идеале тут нужен ввод строки.
                                        selectedDns = "1.1.1.1"
                                        Prefs.setDns(context, "1.1.1.1")
                                    } else {
                                        selectedDns = dns.ip.ifEmpty { "8.8.8.8" } // Системный = 8.8.8.8
                                        Prefs.setDns(context, selectedDns)
                                    }
                                    showDnsDialog = false
                                }.padding(vertical = 12.dp),
                                color = Color.White
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDnsDialog = false }) {
                        Text("Отмена", color = Accent)
                    }
                }
            )
        }
    }
}
