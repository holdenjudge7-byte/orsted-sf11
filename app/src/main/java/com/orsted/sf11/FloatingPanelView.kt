package com.orsted.sf11

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.*

class FloatingPanelView(context: Context) : LinearLayout(context) {
    var onEloChange: ((Int) -> Unit)? = null
    var onTurboToggle: ((Boolean) -> Unit)? = null

    private val statusText: TextView
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        orientation = VERTICAL
        setPadding(30, 30, 30, 30)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F2112b1d"))
            cornerRadius = 30f
            setStroke(4, Color.parseColor("#442ecc71"))
        }

        val title = TextView(context).apply {
            text = "⚡ orsted (stockfish 11)"
            setTextColor(Color.parseColor("#2ecc71"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        addView(title)

        statusText = TextView(context).apply {
            text = "Waiting for board..."
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 10, 0, 10)
        }
        addView(statusText)

        val eloRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val eloLabel = TextView(context).apply { text = "Elo: 2800"; setTextColor(Color.WHITE); textSize = 12f }
        val seek = SeekBar(context).apply { max = 20; progress = 20 }
        seek.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val elo = 800 + (progress * 100)
                eloLabel.text = "Elo: $elo"
                if (fromUser) onEloChange?.invoke(elo)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        eloRow.addView(eloLabel)
        eloRow.addView(seek)
        addView(eloRow)

        val turboSwitch = Switch(context).apply {
            text = "Turbo (10s)"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked -> onTurboToggle?.invoke(isChecked) }
        }
        addView(turboSwitch)
    }

    fun setStatus(text: String) {
        post { statusText.text = text }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                initialX = this.x; initialY = this.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    this.x = (initialX + dx).coerceIn(-50f, 800f)
                    this.y = (initialY + dy).coerceIn(0f, 1500f)
                }
                return true
            }
            MotionEvent.ACTION_UP -> { isDragging = false; return true }
        }
        return super.onTouchEvent(event)
    }
}
