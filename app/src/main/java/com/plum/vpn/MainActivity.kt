// file: app/src/main/java/com/plum/vpn/MainActivity.kt
package com.plum.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgBlack = Color(0xFF000000)
private val CardDark = Color(0xFF121212)
private val Accent = Color(0xFF00FF88)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgBlack, surface = CardDark)) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var isVpnEnabled by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf("TLS Fragment") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("PLUM VPN", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isVpnEnabled) "Соединение защищено" else "Не подключено",
            color = Accent,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = { isVpnEnabled = !isVpnEnabled },
            modifier = Modifier.size(180.dp),
            shape = RoundedCornerShape(90.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isVpnEnabled) Accent else CardDark
            )
        ) {
            Text(
                text = if (isVpnEnabled) "STOP" else "START",
                color = if (isVpnEnabled) BgBlack else Accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Стратегия", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(selectedStrategy, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
