// file: app/src/main/java/com/plum/vpn/ui/DuckAnimation.kt
package com.plum.vpn.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

@Composable
fun DuckAnimation(modifier: Modifier = Modifier) {
    // Анимация покачивания (вверх-вниз)
    val infiniteTransition = rememberInfiniteTransition(label = "duck")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "duckY"
    )

    // Анимация моргания (глаза)
    val eyeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0
                1f at 3800
                0.1f at 3900
                1f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "duckBlink"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val scale = size.minDimension / 300f

            translate(top = offsetY) {
                // 1. Тело утки (жёлтое)
                drawOval(
                    color = Color(0xFFFFD700), // Жёлтый
                    topLeft = Offset(cx - 80f * scale, cy),
                    size = Size(160f * scale, 110f * scale)
                )

                // 2. Голова (круг)
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = 50f * scale,
                    center = Offset(cx, cy - 40f * scale)
                )

                // 3. Клюв (оранжевый, смотрит на нас)
                val beakPath = Path().apply {
                    moveTo(cx - 15f * scale, cy - 20f * scale)
                    lineTo(cx + 15f * scale, cy - 20f * scale)
                    lineTo(cx, cy + 5f * scale)
                    close()
                }
                drawPath(beakPath, Color(0xFFFF8C00)) // Оранжевый

                // 4. Слива во рту (фиолетовая)
                drawCircle(
                    color = Color(0xFF8A2BE2), // Фиолетовый
                    radius = 12f * scale,
                    center = Offset(cx, cy + 10f * scale)
                )
                // Блик на сливе
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = 4f * scale,
                    center = Offset(cx - 4f * scale, cy + 6f * scale)
                )
                // Веточка сливы
                drawLine(
                    color = Color(0xFF556B2F),
                    start = Offset(cx, cy - 2f * scale),
                    end = Offset(cx + 5f * scale, cy - 10f * scale),
                    strokeWidth = 3f * scale
                )

                // 5. Глаза (смотрят прямо, черные)
                val eyeY = cy - 55f * scale
                val eyeRadius = 6f * scale
                // Левый глаз
                drawOval(
                    color = Color.Black,
                    topLeft = Offset(cx - 20f * scale - eyeRadius, eyeY - eyeRadius * eyeScale),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeScale)
                )
                // Правый глаз
                drawOval(
                    color = Color.Black,
                    topLeft = Offset(cx + 20f * scale - eyeRadius, eyeY - eyeRadius * eyeScale),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeScale)
                )

                // 6. Румянец (для милоты)
                drawCircle(
                    color = Color(0xFFFF6347).copy(alpha = 0.4f),
                    radius = 8f * scale,
                    center = Offset(cx - 30f * scale, cy - 35f * scale)
                )
                drawCircle(
                    color = Color(0xFFFF6347).copy(alpha = 0.4f),
                    radius = 8f * scale,
                    center = Offset(cx + 30f * scale, cy - 35f * scale)
                )
                
                // 7. Крылья (по бокам)
                drawOval(
                    color = Color(0xFFE6C200),
                    topLeft = Offset(cx - 100f * scale, cy + 20f * scale),
                    size = Size(40f * scale, 60f * scale)
                )
                drawOval(
                    color = Color(0xFFE6C200),
                    topLeft = Offset(cx + 60f * scale, cy + 20f * scale),
                    size = Size(40f * scale, 60f * scale)
                )
            }
        }
    }
}
