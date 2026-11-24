package com.niiflock

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados en el teléfono. */
object Prefs {
    private const val FILE = "niiflock_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_MINUTES = "unlock_minutes"
    private const val KEY_DISABLED_NORMS = "disabled_norms"

    const val DEFAULT_MINUTES = 15

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun blockedPackages(ctx: Context): Set<String> =
        HashSet<String>(sp(ctx).getStringSet(KEY_BLOCKED, emptySet<String>()) ?: emptySet<String>())

    fun setBlockedPackages(ctx: Context, value: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_BLOCKED, HashSet<String>(value)).apply()
    }

    fun unlockMinutes(ctx: Context): Int = sp(ctx).getInt(KEY_MINUTES, DEFAULT_MINUTES)

    fun setUnlockMinutes(ctx: Context, value: Int) {
        sp(ctx).edit().putInt(KEY_MINUTES, value).apply()
    }

    /** Normas que el usuario desmarcó. Vacío = todas activas. */
    fun disabledNorms(ctx: Context): Set<String> =
        HashSet<String>(sp(ctx).getStringSet(KEY_DISABLED_NORMS, emptySet<String>()) ?: emptySet<String>())

    fun setDisabledNorms(ctx: Context, value: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_DISABLED_NORMS, HashSet<String>(value)).apply()
    }
}
