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
import androidx.compose.material.icons.filled.ArrowBack
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
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startVpnService()
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
                AppNavigation(onToggleVpn = { enable, _ ->
                    if (enable) {
                        val intent = VpnService.prepare(this)
                        if (intent != null) vpnPermissionLauncher.launch(intent) else startVpnService()
                    } else stopVpnService()
                })
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, PlumVpnService::class.java).apply {
            action = PlumVpnService.ACTION_START
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
    var currentDomainProgress by remember { mutableStateOf(0) }
    var totalDomains by remember { mutableStateOf(DomainRegistry.domains.size) }
    var bestStrategyName by remember { mutableStateOf("") }
    var bestStrategyCount by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()

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
        
        ClaudeIcon(modifier = Modifier.size(72.dp), color = Accent)
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PLUM VPN", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black,
            style = TextStyle(shadow = Shadow(color = Accent, offset = Offset.Zero, blurRadius = 30f))
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isVpnEnabled) "Соединение защищено" else "Не подключено",
            color = if (isVpnEnabled) Accent else TextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(56.dp))

        Box(contentAlignment = Alignment.Center) {
            if (isVpnEnabled) {
                Box(modifier = Modifier.size(190.dp).background(Accent.copy(alpha = 0.1f), RoundedCornerShape(95.dp)))
            }
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
                    fontSize = 26.sp, fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable { showStrategyDialog = true },
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ТЕКУЩАЯ СТРАТЕГИЯ", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(selectedStrategy.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                showAutoDialog = true
                isTesting = true
                currentDomainProgress = 0
                bestStrategyName = ""
                bestStrategyCount = 0
                
                scope.launch(Dispatchers.IO) {
                    val list = StrategyRegistry.all()
                    var bestCount = -1
                    var bestStrat: Strategy? = null

                    for (strat in list) {
                        val passed = StrategyRegistry.testStrategy(strat) { current, total ->
                            scope.launch(Dispatchers.Main) {
                                currentDomainProgress = current
                                totalDomains = total
                            }
                        }
                        if (passed > bestCount) {
                            bestCount = passed
                            bestStrat = strat
                            scope.launch(Dispatchers.Main) {
                                bestStrategyName = strat.name
                                bestStrategyCount = passed
                            }
                        }
                    }
                    
                    if (bestStrat != null) {
                        selectedStrategy = bestStrat
                    }
                    
                    scope.launch(Dispatchers.Main) {
                        isTesting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("АВТОПОДБОР", color = Accent, fontWeight = FontWeight.Bold)
        }

        if (showStrategyDialog) {
            AlertDialog(
                onDismissRequest = { showStrategyDialog = false },
                containerColor = CardDark,
                title = { Text("Стратегии", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        items(StrategyRegistry.all()) { strat ->
                            Text(
                                text = strat.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedStrategy = strat
                                    showStrategyDialog = false
                                }.padding(vertical = 12.dp),
                                color = Color.White, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStrategyDialog = false }) { Text("Закрыть", color = Accent) }
                }
            )
        }

        if (showAutoDialog) {
            AlertDialog(
                onDismissRequest = { if (!isTesting) showAutoDialog = false },
                containerColor = CardDark,
                title = { Text("Автоподбор", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        if (isTesting) {
                            Text("Проверка домена $currentDomainProgress / $totalDomains...", color = Accent, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Текущий лидер: $bestStrategyName (обошёл $bestStrategyCount)", color = TextGray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = currentDomainProgress.toFloat() / totalDomains.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent, trackColor = CardDark
                            )
                        } else {
                            if (bestStrategyCount > 0) {
                                Text("Лучшая: $bestStrategyName", color = Color.White)
                                Text("Обошла $bestStrategyCount из $totalDomains доменов", color = Accent, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    showAutoDialog = false
                                }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                    Text("Применить", color = BgBlack)
                                }
                            } else {
                                Text("Ни одна стратегия не обошла блокировки.", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isTesting) TextButton(onClick = { showAutoDialog = false }) { Text("Закрыть", color = Accent) }
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
    var isWhitelistExpanded by remember { mutableStateOf(false) }
    var customCmd by remember { mutableStateOf(Prefs.getCustomCommand(context)) }

    val installedApps = remember { AppList.getInstalledApps(context) }
    val pm = context.packageManager

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Accent)
            }
            Text("Настройки", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("DNS Сервер", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { showDnsDialog = true }, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedDns, color = Color.White, fontSize = 16.sp)
                    Text("Изменить", color = Accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Редактор команд", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customCmd,
                    onValueChange = { customCmd = it; Prefs.setCustomCommand(context, it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("-d1 -s1+s", color = TextGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = TextGray,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { StrategyRegistry.addCustom(customCmd) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Применить команду", color = BgBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isWhitelistExpanded = !isWhitelistExpanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Белый список приложений", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (isWhitelistExpanded) "Скрыть" else "Показать", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (isWhitelistExpanded) {
                    LazyColumn(modifier = Modifier.height(300.dp).padding(horizontal = 16.dp)) {
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
                                Checkbox(checked = isChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Accent))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(pm.getApplicationLabel(app).toString(), color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
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
                                    selectedDns = if (dns.ip == "custom" || dns.ip.isEmpty()) "1.1.1.1" else dns.ip
                                    Prefs.setDns(context, selectedDns)
                                    showDnsDialog = false
                                }.padding(vertical = 12.dp),
                                color = Color.White, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDnsDialog = false }) { Text("Отмена", color = Accent) } }
            )
        }
    }
}
