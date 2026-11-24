package com.asistente.movil

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Botón flotante («tú») y panel de chat, dibujados con ventanas de accesibilidad por encima de cualquier app.
 * - Toque: abre o cierra el panel (si hay una orden en marcha, la detiene).
 * - Mantener pulsado: hablar.
 * - Arrastrar: moverlo; se pega al borde.
 */
class Overlay(private val svc: AssistantService) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val imm = svc.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val ui = Handler(Looper.getMainLooper())
    private val density = svc.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(svc).scaledTouchSlop
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private val night: Boolean
        get() = (svc.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun col(day: Long, nightColor: Long): Int = (if (night) nightColor else day).toInt()

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    private val cIdle = 0xFF3B5BDB.toInt()
    private val cRun = 0xFFE03131.toInt()
    private val cListen = 0xFF2F9E44.toInt()

    // ───────────────────────── burbuja ─────────────────────────

    private val bubbleSize = dp(56)
    private val bubble = FrameLayout(svc)
    private val bubbleIcon = ImageView(svc)
    private val bubbleBg = GradientDrawable()
    private val bubbleLp = WindowManager.LayoutParams(
        bubbleSize,
        bubbleSize,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )
    private var bubbleShown = false

    // ───────────────────────── etiqueta de estado ─────────────────────────

    private val pill = TextView(svc)
    private val pillLp = WindowManager.LayoutParams(
        wrap,
        wrap,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    )
    private var pillShown = false

    // ───────────────────────── panel ─────────────────────────

    private var panelOpen = false
    private var panelShown = false
    private var panelFocusable = false

    private val panelRoot = object : LinearLayout(svc) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
    private val panelLp = WindowManager.LayoutParams(
        0,
        wrap,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )

    private val header = LinearLayout(svc)
    private val scroll = ScrollView(svc)
    private val msgList = LinearLayout(svc)
    private val confirmCard = LinearLayout(svc)
    private val confirmText = TextView(svc)
    private val inputRow = LinearLayout(svc)
    private val input = EditText(svc)

    init {
        buildBubble()
        buildPill()
        buildPanel()
    }

    // ───────────────────────── construcción ─────────────────────────

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(radiusDp).toFloat()
        return d
    }

    private fun buildBubble() {
        bubbleBg.shape = GradientDrawable.OVAL
        bubbleBg.setColor(cIdle)
        bubble.background = bubbleBg
        bubble.elevation = dp(6).toFloat()
        bubbleIcon.setImageResource(R.drawable.ic_chat)
        val pad = dp(15)
        bubbleIcon.setPadding(pad, pad, pad, pad)
        bubble.addView(bubbleIcon, FrameLayout.LayoutParams(match, match))
        bubbleLp.gravity = Gravity.TOP or Gravity.START

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false
            var longPressed = false
            val longRun = Runnable {
                if (!moved) {
                    longPressed = true
                    startVoice()
                }
            }

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = bubbleLp.x
                        startY = bubbleLp.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                        longPressed = false
                        ui.postDelayed(longRun, 550L)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                            moved = true
                            ui.removeCallbacks(longRun)
                        }
                        if (moved) {
                            bubbleLp.x = (startX + dx).toInt()
                            bubbleLp.y = (startY + dy).toInt()
                            updateBubble()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        ui.removeCallbacks(longRun)
                        if (moved) {
                            snapToEdge()
                        } else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP) {
                            onTap()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun buildPill() {
        pill.setTextColor(Color.WHITE)
        pill.textSize = 13f
        pill.setPadding(dp(14), dp(8), dp(14), dp(8))
        pill.background = round(0xE6202124.toInt(), 18)
        pill.maxWidth = (svc.resources.displayMetrics.widthPixels * 0.8f).toInt()
        pill.maxLines = 2
        pillLp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        pillLp.y = dp(54)
    }

    private fun smallButton(label: String, onClick: () -> Unit): TextView {
        val t = TextView(svc)
        t.text = label
        t.textSize = 13f
        t.setTextColor(col(0xFF3B5BDB, 0xFF91A7FF))
        t.setPadding(dp(10), dp(6), dp(10), dp(6))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun circleButton(iconRes: Int, color: Int, onClick: () -> Unit): ImageView {
        val b = ImageView(svc)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        b.background = bg
        b.setImageResource(iconRes)
        val p = dp(10)
        b.setPadding(p, p, p, p)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buildPanel() {
        panelRoot.orientation = LinearLayout.VERTICAL
        panelRoot.background = round(col(0xFFFFFFFF, 0xFF1E1F24), 20)
        panelRoot.elevation = dp(8).toFloat()
        panelRoot.setPadding(dp(12), dp(8), dp(12), dp(10))
        panelLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        // Cabecera
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(svc)
        title.text = "Asistente"
        title.textSize = 16f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(col(0xFF1B1B1F, 0xFFE8EAED))
        header.addView(title, LinearLayout.LayoutParams(0, wrap, 1f))
        header.addView(smallButton("Nueva") { Session.newConversation() })
        header.addView(smallButton("Abrir") { openApp() })
        header.addView(smallButton("×") { closePanel() })
        panelRoot.addView(header, LinearLayout.LayoutParams(match, wrap))

        // Mensajes
        msgList.orientation = LinearLayout.VERTICAL
        scroll.addView(msgList, ViewGroup.LayoutParams(match, wrap))
        panelRoot.addView(scroll, LinearLayout.LayoutParams(match, dp(240)))

        // Tarjeta de confirmación
        confirmCard.orientation = LinearLayout.VERTICAL
        confirmCard.setPadding(dp(4), dp(8), dp(4), dp(4))
        val confirmTitle = TextView(svc)
        confirmTitle.text = "¿Permitir esta acción?"
        confirmTitle.textSize = 16f
        confirmTitle.typeface = Typeface.DEFAULT_BOLD
        confirmTitle.setTextColor(col(0xFF1B1B1F, 0xFFE8EAED))
        confirmCard.addView(confirmTitle)
        confirmText.textSize = 14f
        confirmText.setTextColor(col(0xFF3C4043, 0xFFBDC1C6))
        confirmText.setPadding(0, dp(6), 0, dp(10))
        confirmCard.addView(confirmText)
        val answers = LinearLayout(svc)
        answers.orientation = LinearLayout.HORIZONTAL
        val no = TextView(svc)
        no.text = "Cancelar"
        no.gravity = Gravity.CENTER
        no.textSize = 15f
        no.setTextColor(col(0xFF1B1B1F, 0xFFE8EAED))
        no.background = round(col(0xFFE8EAED, 0xFF3C4043), 14)
        no.setPadding(0, dp(12), 0, dp(12))
        no.setOnClickListener { Session.answer(false) }
        val yes = TextView(svc)
        yes.text = "Permitir"
        yes.gravity = Gravity.CENTER
        yes.textSize = 15f
        yes.setTextColor(Color.WHITE)
        yes.background = round(cIdle, 14)
        yes.setPadding(0, dp(12), 0, dp(12))
        yes.setOnClickListener { Session.answer(true) }
        val lpNo = LinearLayout.LayoutParams(0, wrap, 1f)
        lpNo.rightMargin = dp(6)
        val lpYes = LinearLayout.LayoutParams(0, wrap, 1f)
        lpYes.leftMargin = dp(6)
        answers.addView(no, lpNo)
        answers.addView(yes, lpYes)
        confirmCard.addView(answers, LinearLayout.LayoutParams(match, wrap))
        confirmCard.visibility = View.GONE
        panelRoot.addView(confirmCard, LinearLayout.LayoutParams(match, wrap))

        // Entrada de texto
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        input.hint = "Escribe una orden…"
        input.textSize = 15f
        input.setTextColor(col(0xFF1B1B1F, 0xFFE8EAED))
        input.setHintTextColor(col(0xFF80868B, 0xFF9AA0A6))
        input.background = round(col(0xFFF1F3F7, 0xFF2B2D33), 22)
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        input.maxLines = 4
        input.setHorizontallyScrolling(false)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }
        input.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN && !panelFocusable) {
                setPanelFocusable(true)
                ui.postDelayed({
                    v.requestFocus()
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }, 150L)
            }
            false
        }
        val lpInput = LinearLayout.LayoutParams(0, wrap, 1f)
        inputRow.addView(input, lpInput)
        val size = dp(44)
        val lpBtn = LinearLayout.LayoutParams(size, size)
        lpBtn.leftMargin = dp(6)
        inputRow.addView(circleButton(R.drawable.ic_mic, cListen) { startVoice() }, lpBtn)
        val lpBtn2 = LinearLayout.LayoutParams(size, size)
        lpBtn2.leftMargin = dp(6)
        inputRow.addView(circleButton(R.drawable.ic_send, cIdle) { send() }, lpBtn2)
        val lpRow = LinearLayout.LayoutParams(match, wrap)
        lpRow.topMargin = dp(8)
        panelRoot.addView(inputRow, lpRow)
    }

    // ───────────────────────── ciclo de vida ─────────────────────────

    fun show() {
        if (bubbleShown) return
        val m = svc.resources.displayMetrics
        val savedX = Prefs.bubbleX
        val savedY = Prefs.bubbleY
        bubbleLp.x = if (savedX >= 0) savedX else m.widthPixels - bubbleSize
        bubbleLp.y = if (savedY >= 0) savedY else m.heightPixels / 3
        clampBubble()
        try {
            wm.addView(bubble, bubbleLp)
            bubbleShown = true
        } catch (e: Exception) {
            Session.add(Role.ERROR, "No pude mostrar el botón flotante: ${e.message}")
        }
    }

    /** Empieza a observar el estado compartido para reflejarlo en pantalla. */
    fun bind() {
        scope.launch { Session.running.collect { refresh() } }
        scope.launch { Session.listening.collect { refresh() } }
        scope.launch { Session.pending.collect { refresh() } }
        scope.launch { Session.status.collect { refresh() } }
        scope.launch { Session.messages.collect { renderMessages(it) } }
    }

    fun destroy() {
        job.cancel()
        ui.removeCallbacksAndMessages(null)
        hideKeyboard()
        try {
            if (panelShown) wm.removeView(panelRoot)
        } catch (e: Exception) {
            // ya no estaba
        }
        try {
            if (pillShown) wm.removeView(pill)
        } catch (e: Exception) {
            // ya no estaba
        }
        try {
            if (bubbleShown) wm.removeView(bubble)
        } catch (e: Exception) {
            // ya no estaba
        }
        panelShown = false
        pillShown = false
        bubbleShown = false
    }

    fun onConfigChanged() {
        clampBubble()
        updateBubble()
        if (panelShown) {
            val m = svc.resources.displayMetrics
            panelLp.width = minOf(m.widthPixels - dp(24), dp(520))
            try {
                wm.updateViewLayout(panelRoot, panelLp)
            } catch (e: Exception) {
                // nada que hacer
            }
        }
    }

    // ───────────────────────── comportamiento ─────────────────────────

    private fun onTap() {
        if (Session.running.value) {
            Session.cancel()
        } else {
            panelOpen = !panelOpen
            refresh()
        }
    }

    private fun closePanel() {
        panelOpen = false
        refresh()
    }

    private fun openApp() {
        panelOpen = false
        refresh()
        val i = Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            svc.startActivity(i)
        } catch (e: Exception) {
            Session.add(Role.ERROR, "No pude abrir la app: ${e.message}")
        }
    }

    private fun startVoice() {
        if (Session.running.value) return
        val i = Intent(svc, VoiceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            svc.startActivity(i)
        } catch (e: Exception) {
            Session.add(Role.ERROR, "No pude abrir el micrófono: ${e.message}")
        }
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        hideKeyboard()
        setPanelFocusable(false)
        Session.submit(t)
    }

    private fun handleBack() {
        if (panelFocusable) {
            hideKeyboard()
            setPanelFocusable(false)
        } else {
            closePanel()
        }
    }

    private fun hideKeyboard() {
        try {
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (e: Exception) {
            // nada que hacer
        }
    }

    private fun setPanelFocusable(on: Boolean) {
        panelFocusable = on
        val f = panelLp.flags
        panelLp.flags = if (on) {
            f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (panelShown) {
            try {
                wm.updateViewLayout(panelRoot, panelLp)
            } catch (e: Exception) {
                // nada que hacer
            }
        }
    }

    // ───────────────────────── dibujo del estado ─────────────────────────

    private fun refresh() {
        val running = Session.running.value
        val listening = Session.listening.value
        val pending = Session.pending.value

        when {
            running -> {
                bubbleBg.setColor(cRun)
                bubbleIcon.setImageResource(R.drawable.ic_stop)
            }
            listening -> {
                bubbleBg.setColor(cListen)
                bubbleIcon.setImageResource(R.drawable.ic_mic)
            }
            else -> {
                bubbleBg.setColor(cIdle)
                bubbleIcon.setImageResource(R.drawable.ic_chat)
            }
        }

        val showConfirm = pending != null
        val showPanel = showConfirm || (panelOpen && !running)
        if (pending != null) confirmText.text = pending.text
        confirmCard.visibility = if (showConfirm) View.VISIBLE else View.GONE
        val chatVis = if (showConfirm) View.GONE else View.VISIBLE
        header.visibility = chatVis
        scroll.visibility = chatVis
        inputRow.visibility = chatVis
        setPanelShown(showPanel)

        val label = when {
            showConfirm -> "Esperando tu permiso…"
            running -> Session.status.value.ifBlank { "Trabajando…" }
            listening -> "Escuchando…"
            else -> ""
        }
        setPill(label)
    }

    private fun setPill(text: String) {
        if (text.isEmpty()) {
            if (pillShown) {
                try {
                    wm.removeView(pill)
                } catch (e: Exception) {
                    // ya no estaba
                }
                pillShown = false
            }
            return
        }
        pill.text = text
        if (!pillShown) {
            try {
                wm.addView(pill, pillLp)
                pillShown = true
            } catch (e: Exception) {
                // sin etiqueta
            }
        }
    }

    private fun setPanelShown(show: Boolean) {
        if (show == panelShown) return
        if (show) {
            val m = svc.resources.displayMetrics
            panelLp.width = minOf(m.widthPixels - dp(24), dp(520))
            panelLp.y = dp(12)
            panelLp.flags = panelLp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            panelFocusable = false
            val lp = scroll.layoutParams as LinearLayout.LayoutParams
            lp.height = minOf((m.heightPixels * 0.34f).toInt(), dp(320))
            scroll.layoutParams = lp
            try {
                wm.addView(panelRoot, panelLp)
                panelShown = true
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            } catch (e: Exception) {
                Session.add(Role.ERROR, "No pude mostrar el panel: ${e.message}")
            }
        } else {
            hideKeyboard()
            try {
                wm.removeView(panelRoot)
            } catch (e: Exception) {
                // ya no estaba
            }
            panelShown = false
            panelFocusable = false
        }
    }

    private fun renderMessages(list: List<ChatMsg>) {
        msgList.removeAllViews()
        if (list.isEmpty()) {
            val hint = TextView(svc)
            hint.text = "Escribe una orden, o mantén pulsado el botón para hablar.\nEjemplo: «Abre YouTube y busca recetas de arepas»."
            hint.textSize = 13f
            hint.setTextColor(col(0xFF5F6368, 0xFF9AA0A6))
            hint.setPadding(dp(4), dp(8), dp(4), dp(8))
            msgList.addView(hint)
        } else {
            for (m in list.takeLast(40)) msgList.addView(messageView(m))
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun messageView(m: ChatMsg): View {
        val tv = TextView(svc)
        tv.text = m.text
        val maxW = minOf(svc.resources.displayMetrics.widthPixels - dp(24), dp(520)) - dp(64)
        tv.maxWidth = maxW
        val lp = LinearLayout.LayoutParams(wrap, wrap)
        lp.topMargin = dp(4)
        when (m.role) {
            Role.USER -> {
                tv.textSize = 14f
                tv.setTextColor(Color.WHITE)
                tv.background = round(cIdle, 14)
                tv.setPadding(dp(12), dp(8), dp(12), dp(8))
                lp.gravity = Gravity.END
            }
            Role.ASSISTANT -> {
                tv.textSize = 14f
                tv.setTextColor(col(0xFF1B1B1F, 0xFFE8EAED))
                tv.background = round(col(0xFFEEF0F4, 0xFF2B2D33), 14)
                tv.setPadding(dp(12), dp(8), dp(12), dp(8))
                lp.gravity = Gravity.START
            }
            Role.STATUS -> {
                tv.textSize = 12f
                tv.setTextColor(col(0xFF6B7280, 0xFF9AA0A6))
                tv.setPadding(dp(6), dp(2), dp(6), dp(2))
                lp.gravity = Gravity.START
            }
            Role.ERROR -> {
                tv.textSize = 13f
                tv.setTextColor(0xFFE03131.toInt())
                tv.setPadding(dp(6), dp(4), dp(6), dp(4))
                lp.gravity = Gravity.START
            }
        }
        tv.layoutParams = lp
        return tv
    }

    // ───────────────────────── burbuja: posición y toques ─────────────────────────

    private fun clampBubble() {
        val m = svc.resources.displayMetrics
        bubbleLp.x = bubbleLp.x.coerceIn(0, maxOf(0, m.widthPixels - bubbleSize))
        bubbleLp.y = bubbleLp.y.coerceIn(dp(24), maxOf(dp(24), m.heightPixels - bubbleSize - dp(24)))
    }

    private fun updateBubble() {
        if (!bubbleShown) return
        try {
            wm.updateViewLayout(bubble, bubbleLp)
        } catch (e: Exception) {
            // nada que hacer
        }
    }

    private fun snapToEdge() {
        val m = svc.resources.displayMetrics
        clampBubble()
        val target = if (bubbleLp.x + bubbleSize / 2 < m.widthPixels / 2) 0 else m.widthPixels - bubbleSize
        val anim = ValueAnimator.ofInt(bubbleLp.x, target)
        anim.duration = 180L
        anim.addUpdateListener { a ->
            bubbleLp.x = a.animatedValue as Int
            updateBubble()
        }
        anim.start()
        Prefs.bubbleX = target
        Prefs.bubbleY = bubbleLp.y
    }

    /** Mientras el asistente hace un gesto, la burbuja deja pasar los toques para no taparlo. */
    fun setBubbleTouchable(on: Boolean) {
        if (!bubbleShown) return
        val f = bubbleLp.flags
        bubbleLp.flags = if (on) {
            f and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        updateBubble()
    }
}
