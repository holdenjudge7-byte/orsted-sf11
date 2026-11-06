package com.orsted.sf11

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var overlay: ArrowOverlayView
    private lateinit var panel: FloatingPanelView
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var engineReady = false
    private var isTurbo = false
    private var lastFen = ""

    // JNI Bindings
    external fun engineInit()
    external fun engineGetBestMove(fen: String, movetime: Int): String
    external fun engineSetElo(elo: Int)
    external fun engineStop()

    companion object {
        init {
            try {
                System.loadLibrary("orsted_engine")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("Orsted", "Failed to load native library", e)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRASH CATCHER
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            runOnUiThread {
                val tv = android.widget.TextView(this).apply {
                    text = "CRASH:\n\n${sw.toString()}"
                    setTextColor(Color.RED); setBackgroundColor(Color.BLACK)
                    setPadding(40, 40, 40, 40)
                }
                setContentView(tv)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        val root = FrameLayout(this)
        setContentView(root)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        setupWebView()
        root.addView(webView)

        overlay = ArrowOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = false
        }
        root.addView(overlay)

        panel = FloatingPanelView(this).apply {
            val density = resources.displayMetrics.density
            val p = FrameLayout.LayoutParams((280 * density).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(20, 200, 0, 0)
            layoutParams = p
            onEloChange = { elo -> if (engineReady) engineScope.launch { engineSetElo(elo) } }
            onTurboToggle = { turbo -> isTurbo = turbo }
        }
        root.addView(panel)

        webView.loadUrl("https://lichess.org")

        // DELAY ENGINE LOAD so the UI renders first
        panel.setStatus("Waking up engine...")
        engineScope.launch {
            delay(2000)
            try {
                engineInit()
                engineSetElo(2800)
                engineReady = true
                panel.setStatus("Engine ready")
            } catch (e: Throwable) {
                panel.setStatus("Engine failed: ${e.message}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        }
        webView.addJavascriptInterface(WebAppInterface(), "OrstedBridge")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("lichess.org") == true) {
                    val js = """
                        if (!window.__orsted) {
                            window.__orsted = true;
                            setInterval(() => {
                                const board = document.querySelector('cg-board');
                                if (!board) return;
                                const wrap = board.closest('.cg-wrap') || board.parentElement;
                                const isFlipped = wrap && wrap.classList.contains('orientation-black');
                                const rect = board.getBoundingClientRect();
                                
                                const fenEl = document.querySelector('.round__app[data-fen]') || document.querySelector('[data-fen]');
                                if (!fenEl) return;
                                const fen = fenEl.getAttribute('data-fen');
                                
                                window.OrstedBridge.onFen(fen, rect.left, rect.top, rect.width, isFlipped);
                            }, 1000);
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onFen(fen: String, x: Float, y: Float, w: Float, flipped: Boolean) {
            if (!engineReady || fen == lastFen) return
            lastFen = fen

            engineScope.launch {
                val movetime = if (isTurbo) 10000 else 300
                val bestMove = engineGetBestMove(fen, movetime)
                
                if (bestMove.length >= 4) {
                    withContext(Dispatchers.Main) {
                        val density = resources.displayMetrics.density
                        overlay.setArrow(bestMove, flipped, x * density, y * density, w * density)
                        panel.setStatus("▶ $bestMove")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        engineScope.launch { engineStop() }
        engineScope.cancel()
        super.onDestroy()
    }
}

class ArrowOverlayView(context: android.content.Context) : View(context) {
    private var arrow: String? = null
    private var bx = 0f; private var by = 0f; private var bs = 0f
    private var flip = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#2ecc71"); style = Paint.Style.STROKE; strokeWidth = 15f 
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        color = Color.parseColor("#2ecc71"); style = Paint.Style.FILL 
    }

    fun setArrow(move: String, isFlipped: Boolean, x: Float, y: Float, size: Float) {
        arrow = move; flip = isFlipped; bx = x; by = y; bs = size; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = arrow ?: return
        if (bs <= 0f) return // Prevent drawing if board size is invalid
        
        val sq = bs / 8f
        fun cx(f: Int) = bx + (if (flip) 7 - f else f) * sq + sq / 2f
        fun cy(r: Int) = by + (if (flip) r else 7 - r) * sq + sq / 2f

        val c1 = m[0] - 'a'; val r1 = m[1] - '1'
        val c2 = m[2] - 'a'; val r2 = m[3] - '1'
        val x1 = cx(c1); val y1 = cy(r1); val x2 = cx(c2); val y2 = cy(r2)

        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val endX = (x2 - Math.cos(angle) * sq * 0.3).toFloat()
        val endY = (y2 - Math.sin(angle) * sq * 0.3).toFloat()

        canvas.drawLine(x1, y1, endX, endY, paint)
        
        val arrowSize = sq * 0.4f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo((x2 - arrowSize * Math.cos(angle - Math.PI / 6)).toFloat(), (y2 - arrowSize * Math.sin(angle - Math.PI / 6)).toFloat())
        path.lineTo((x2 - arrowSize * Math.cos(angle + Math.PI / 6)).toFloat(), (y2 - arrowSize * Math.sin(angle + Math.PI / 6)).toFloat())
        path.close()
        canvas.drawPath(path, headPaint)
    }
}
