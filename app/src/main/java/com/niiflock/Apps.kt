package com.niiflock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

data class AppEntry(val packageName: String, val label: String)

/**
 * Apps que nunca se pueden bloquear, para que no te quedes sin ajustes,
 * sin teléfono ni llamadas de emergencia.
 */
object Protected {
    private val exact = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
    )

    fun isProtected(ctx: Context, pkg: String): Boolean =
        pkg == ctx.packageName ||
            pkg in exact ||
            pkg.startsWith("com.android.settings") ||
            pkg.contains("dialer") ||
            pkg.contains("incallui") ||
            pkg.contains("emergency") ||
            pkg.contains("telecom")
}

object Apps {
    @Suppress("DEPRECATION")
    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    /** Apps con ícono de lanzador que se pueden elegir para bloquear. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackages = query(pm, home).map { it.activityInfo.packageName }.toSet()

        return query(pm, launcher)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName !in homePackages && !Protected.isProtected(ctx, it.packageName) }
            .sortedBy { it.label.lowercase() }
    }
}
