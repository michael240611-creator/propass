package org.plum.vpn

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val VPN_REQUEST_CODE = 1
    private var isVpnRunning = false
    private lateinit var statusText: TextView
    private lateinit var btnToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Главный контейнер с черным фоном
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(60, 80, 60, 50)
        }

        // Заголовок
        val titleText = TextView(this).apply {
            text = "PLUM VPN"
            textSize = 32f
            setTextColor(Color.parseColor("#FF7A00"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        // Рисунок утки со сливой
        val duckView = DuckView(this).apply {
            val density = resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                (220 * density).toInt(),
                (160 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 40, 0, 40)
            }
        }

        // Текст статуса
        statusText = TextView(this).apply {
            text = "VPN выключен"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(0)
            setPadding(0, 0, 0, 60)
        }

        // Кнопка
        btnToggle = Button(this).apply {
            text = "Включить VPN"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF7A00"))
            stateListAnimator = null 
            setPadding(60, 20, 60, 20)
            
            setOnClickListener {
                if (!isVpnRunning) {
                    val intent = VpnService.prepare(this@MainActivity)
                    if (intent != null) {
                        startActivityForResult(intent, VPN_REQUEST_CODE)
                    } else {
                        startVpn()
                    }
                } else {
                    stopVpn()
                }
            }
        }

        layout.addView(titleText)
        layout.addView(duckView) // Добавляем утку в центр
        layout.addView(statusText)
        layout.addView(btnToggle)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpn()
        }
    }

    private fun startVpn() {
        startService(Intent(this, PlumVpnService::class.java).apply {
            action = "START"
        })
        isVpnRunning = true
        statusText.text = "VPN включен"
        statusText.setTextColor(Color.parseColor("#4CAF50"))
        btnToggle.text = "Выключить VPN"
        btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
    }

    private fun stopVpn() {
        startService(Intent(this, PlumVpnService::class.java).apply {
            action = "STOP"
        })
        isVpnRunning = false
        statusText.text = "VPN выключен"
        statusText.setTextColor(Color.WHITE)
        btnToggle.text = "Включить VPN"
        btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF7A00"))
    }
}

// Класс для рисования желтой утки со сливой в клюве
class DuckView(context: Context) : View(context) {
    private val duckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700") // Желтый
        style = Paint.Style.FILL
    }
    private val beakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFA500") // Оранжевый
        style = Paint.Style.FILL
    }
    private val plumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A2BE2") // Фиолетовый (слива)
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#32CD32") // Зеленый листик
        style = Paint.Style.FILL
    }

    private val leafPath = Path()
    private val beakTopPath = Path()
    private val beakBottomPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = height / 2.8f // Радиус головы утки

        // 1. Голова утки (смещена влево)
        canvas.drawCircle(cx - 30f, cy, r, duckPaint)

        // 2. Глаз
        canvas.drawCircle(cx - 10f, cy - r/3, r/7, eyePaint)

        // 3. Верхняя часть клюва
        beakTopPath.reset()
        beakTopPath.moveTo(cx + r - 40f, cy - r/2)
        beakTopPath.lineTo(cx + r + 50f, cy - r/4)
        beakTopPath.lineTo(cx + r - 40f, cy - r/8)
        beakTopPath.close()
        canvas.drawPath(beakTopPath, beakPaint)

        // 4. Нижняя часть клюва
        beakBottomPath.reset()
        beakBottomPath.moveTo(cx + r - 40f, cy + r/8)
        beakBottomPath.lineTo(cx + r + 50f, cy + r/4)
        beakBottomPath.lineTo(cx + r - 40f, cy + r/2)
        beakBottomPath.close()
        canvas.drawPath(beakBottomPath, beakPaint)

        // 5. Слива в клюве (между верхней и нижней частью)
        canvas.drawCircle(cx + r + 10f, cy, r/3, plumPaint)

        // 6. Зеленый листик на сливе
        leafPath.reset()
        leafPath.addOval(RectF(cx + r + 15f, cy - r/2, cx + r + 45f, cy - r/4), Path.Direction.CW)
        canvas.drawPath(leafPath, leafPaint)
    }
}