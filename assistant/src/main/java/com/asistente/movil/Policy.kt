package com.asistente.movil

import java.text.Normalizer

/**
 * Reglas de seguridad que se aplican en código, sin depender de lo que decida el modelo.
 * Lo que dice la pantalla, un mensaje o una web nunca puede saltárselas.
 */
object Policy {

    /** Fragmentos de nombre de paquete o de nombre de app que quedan protegidos siempre. */
    private val blockedFragments = listOf(
        // Bancos y billeteras
        "bank", "banco", "bancol", "davivienda", "nequi", "daviplata", "bbva", "santander",
        "scotia", "itau", "nubank", "bradesco", "hsbc", "wellsfargo", "paypal", "venmo",
        "cashapp", "zelle", "revolut", "wallet", "billetera", "mercadopago", "spay", "gpay", "pay",
        // Criptomonedas
        "binance", "coinbase", "kraken", "bitso", "bybit", "okx", "kucoin", "bitget",
        "metamask", "trustwallet", "ledger", "trezor", "exodus", "crypto",
        // Contraseñas y códigos de verificación
        "keepass", "bitwarden", "1password", "lastpass", "dashlane", "password",
        "authenticator", "authy",
        // Instalar APK a mano
        "packageinstaller",
    )

    /** Palabras que, en el texto de un botón, hacen que la acción pida permiso al usuario. */
    private val sensitiveWords = setOf(
        "enviar", "mandar", "send", "publicar", "post", "postear", "tuitear", "tweet",
        "pagar", "pay", "comprar", "buy", "purchase", "transferir", "transfer",
        "donar", "donate", "suscribir", "suscribirse", "subscribe",
        "eliminar", "borrar", "delete", "desinstalar", "uninstall", "instalar", "install",
        "formatear", "restablecer", "reset", "llamar", "call",
        "permitir", "allow", "conceder", "grant", "autorizar", "authorize",
    )

    private val sensitivePhrases = listOf(
        "realizar pedido", "hacer pedido", "confirmar pedido", "confirmar pago", "confirmar compra",
        "place order", "confirm order", "confirm payment", "confirm purchase", "cerrar sesion", "sign out",
        "log out",
    )

    /** Campos donde nunca se escribe. */
    private val secretWords = setOf(
        "contrasena", "password", "clave", "pin", "cvv", "cvc", "passcode", "otp",
    )

    private val secretPhrases = listOf(
        "numero de tarjeta", "card number", "codigo de seguridad", "security code", "numero de cuenta",
    )

    private val messagingFragments = listOf(
        "whatsapp", "telegram", "messag", "mms", "sms", "signal", "gmail", "mail", "outlook",
        "instagram", "slack", "discord", "teams", "viber", "wechat", "skype", "threads", "twitter",
    )

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun words(s: String): Set<String> =
        norm(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()

    /** Devuelve el fragmento que coincide si la app está protegida, o null si no lo está. */
    fun blockedReason(pkg: String?, label: String?): String? {
        val p = norm(pkg ?: "")
        val l = norm(label ?: "")
        if (p.isEmpty() && l.isEmpty()) return null
        val extras = Prefs.extraBlocked
            .split(',', ';', '\n')
            .map { norm(it.trim()) }
            .filter { it.length >= 3 }
        for (f in blockedFragments + extras) {
            if (p.contains(f) || l.contains(f)) return f
        }
        return null
    }

    fun isSensitive(label: String): Boolean {
        if (label.isBlank()) return false
        val n = norm(label)
        val w = words(label)
        return w.any { it in sensitiveWords } || sensitivePhrases.any { n.contains(it) }
    }

    fun isSecretField(label: String): Boolean {
        if (label.isBlank()) return false
        val n = norm(label)
        val w = words(label)
        return w.any { it in secretWords } || secretPhrases.any { n.contains(it) }
    }

    fun isMessagingPackage(pkg: String?): Boolean {
        val p = (pkg ?: "").lowercase()
        return messagingFragments.any { p.contains(it) }
    }
}
