package com.niiflock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Detecta qué app pasa al frente. Si es una de las bloqueadas y no está desbloqueada,
 * abre la pantalla con la pregunta (QuizActivity) por encima.
 *
 * No lee el contenido de la pantalla: solo el nombre del paquete de la ventana activa.
 */
class GateAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var lastLaunchAt = 0L

    // Revisa cada tanto si venció el desbloqueo de la app que sigue abierta.
    private val recheck = object : Runnable {
        override fun run() {
            val pkg = currentPackage
            val pm = getSystemService(PowerManager::class.java)
            if (pkg != null && (pm == null || pm.isInteractive)) {
                gateIfNeeded(pkg)
            }
            handler.postDelayed(this, RECHECK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, RECHECK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return

        currentPackage = pkg
        if (pkg == packageName) return
        gateIfNeeded(pkg)
    }

    private fun gateIfNeeded(pkg: String) {
        if (!Prefs.isEnabled(this)) return
        if (pkg !in Prefs.blockedPackages(this)) return
        if (Protected.isProtected(this, pkg)) return
        if (GateState.isUnlocked(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < DEBOUNCE_MS) return
        lastLaunchAt = now

        val intent = Intent(this, QuizActivity::class.java)
            .putExtra(QuizActivity.EXTRA_TARGET_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Si no se pudo mostrar la pregunta, al menos sácalo de la app bloqueada.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val DEBOUNCE_MS = 700L
        const val RECHECK_MS = 15_000L
    }
}
