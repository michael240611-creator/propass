// file: app/src/main/java/com/plum/vpn/ui/ClaudeIcon.kt
package com.plum.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun ClaudeIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 3.5f
        val innerRadius = size.minDimension / 6f

        drawCircle(
            color = color,
            radius = innerRadius,
            center = Offset(centerX, centerY)
        )

        for (i in 0 until 8) {
            val angle = i * 45f
            rotate(degrees = angle, pivot = Offset(centerX, centerY)) {
                val path = Path().apply {
                    moveTo(centerX - radius * 0.15f, centerY - innerRadius - 8.dp.toPx())
                    lineTo(centerX + radius * 0.15f, centerY - innerRadius - 8.dp.toPx())
                    lineTo(centerX + radius * 0.05f, centerY - radius - 12.dp.toPx())
                    lineTo(centerX - radius * 0.05f, centerY - radius - 12.dp.toPx())
                    close()
                }
                drawPath(path = path, color = color)
            }
        }
    }
}
