package com.asistente.movil

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad: es lo que le permite al asistente mostrar el botón flotante por encima de otras apps
 * y, solo mientras ejecuta una orden tuya, leer la pantalla y tocar o escribir por ti.
 */
class AssistantService : AccessibilityService() {

    lateinit var ops: ScreenOps
        private set

    @Volatile
    var overlay: Overlay? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        ops = ScreenOps(this)
        instance = this
        refreshOverlay()
    }

    /** Muestra u oculta el botón flotante según el ajuste del usuario. */
    fun refreshOverlay() {
        if (Prefs.showBubble) {
            if (overlay == null) {
                val o = Overlay(this)
                o.show()
                o.bind()
                overlay = o
            }
        } else {
            overlay?.destroy()
            overlay = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlay?.destroy()
        overlay = null
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlay?.destroy()
        overlay = null
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AssistantService? = null
    }
}
