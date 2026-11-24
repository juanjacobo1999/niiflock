package com.niiflock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Muestra la pregunta encima de la app bloqueada.
 * Si se abre sin EXTRA_TARGET_PACKAGE funciona como modo práctica.
 */
class QuizActivity : ComponentActivity() {

    private var targetPackage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()

        // Atrás no sirve para saltarse la pregunta: te devuelve al inicio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leave()
            }
        })

        setContent {
            NiifLockTheme {
                val pkg = targetPackage
                val label = remember(pkg) { if (pkg.isEmpty()) null else labelOf(pkg) }
                QuizScreen(
                    appLabel = label,
                    minutes = Prefs.unlockMinutes(this@QuizActivity),
                    pickQuestion = { QuestionBank.pick(this@QuizActivity) },
                    onUnlock = { unlockAndOpen(pkg) },
                    onLeave = { leave() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun unlockAndOpen(pkg: String) {
        if (pkg.isNotEmpty()) {
            GateState.unlock(pkg, Prefs.unlockMinutes(this))
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }
        finish()
    }

    private fun leave() {
        if (targetPackage.isNotEmpty()) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }
}
