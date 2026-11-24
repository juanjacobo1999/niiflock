package com.niiflock

/**
 * Desbloqueos temporales, guardados solo en memoria.
 * Si el proceso se reinicia, todo vuelve a quedar bloqueado (falla del lado seguro).
 */
object GateState {
    private val unlockedUntil = HashMap<String, Long>()

    @Synchronized
    fun isUnlocked(pkg: String): Boolean {
        val until = unlockedUntil[pkg] ?: return false
        return System.currentTimeMillis() < until
    }

    @Synchronized
    fun unlock(pkg: String, minutes: Int) {
        unlockedUntil[pkg] = System.currentTimeMillis() + minutes * 60_000L
    }

    @Synchronized
    fun relockAll() {
        unlockedUntil.clear()
    }
}
