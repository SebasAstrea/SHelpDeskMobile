package online.sistemahelpdesk.movil.core

import android.content.Context
import android.content.SharedPreferences
import online.sistemahelpdesk.movil.data.Sesion

/**
 * Persistencia de sesión (equivalente a localStorage/sessionStorage del web:
 * clave `token`, `user`, `remembered_email` y `theme`).
 */
object Session {
    private const val FILE = "shd_session"
    private const val MODO_PRIVATE = "private"

    /** Prefijo de los tokens de sesión demo (chips genéricos); DataProvider sirve datos locales. */
    const val PREFIJO_TOKEN_DEMO = "demo-token-"

    private lateinit var prefs: SharedPreferences
    private var privatePrefs: SharedPreferences? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun clear(context: Context) {
        init(context)
        prefs.edit().remove("token").remove("user_id").remove("nombre")
            .remove("email").remove("rol").remove("area").remove("mfa").apply()
        privatePrefs?.edit()?.clear()?.apply()
        privatePrefs = null
    }

    // ---- token ------------------------------------------------------------
    /**
     * Guarda el token cifrado con [TokenCrypto] (Android Keystore).
     * `recordar=false` lo mantiene solo en memoria (prefs privadas).
     */
    fun guardarToken(token: String, recordar: Boolean) {
        val cifrado = TokenCrypto.cifrar(token)
        if (recordar) {
            prefs.edit().putString("token", cifrado).apply()
            guardarSesionMemoria(cifrado)
        } else {
            prefs.edit().remove("token").apply()
            guardarSesionMemoria(cifrado)
        }
    }

    private fun guardarSesionMemoria(cifrado: String) {
        val ctx = App.contexto ?: return
        privatePrefs = ctx.getSharedPreferences(MODO_PRIVATE, Context.MODE_PRIVATE)
        privatePrefs?.edit()?.putString("token", cifrado)?.apply()
    }

    /** Devuelve el token descifrado o `null` si ya no es legible (la sesión se repite). */
    fun token(): String? {
        val bruto = prefs.getString("token", null) ?: privatePrefs?.getString("token", null) ?: return null
        val limpio = TokenCrypto.descifrar(bruto) ?: return null
        if (!bruto.startsWith(TokenCrypto.PREFIJO)) {
            // Migración de tokens guardados en claro por versiones anteriores.
            guardarToken(limpio, recordar = prefs.contains("token"))
        }
        return limpio
    }

    // ---- usuario ----------------------------------------------------------
    fun guardarUsuario(s: Sesion, recordar: Boolean) {
        val target = if (recordar) prefs else (privatePrefs ?: prefs)
        target.edit()
            .putInt("user_id", s.userId)
            .putString("nombre", s.nombre)
            .putString("email", s.email)
            .putString("rol", s.rol)
            .putString("area", s.area ?: "")
            .putBoolean("mfa", s.mfaEnabled)
            .apply()
        if (!recordar) prefs.edit().remove("user_id").remove("nombre").remove("email").remove("rol").apply()
    }

    fun sesion(): Sesion? {
        val source = if (prefs.contains("user_id")) prefs else (privatePrefs ?: prefs)
        if (!source.contains("user_id")) return null
        return Sesion(
            userId = source.getInt("user_id", 0),
            nombre = source.getString("nombre", "") ?: "",
            email = source.getString("email", "") ?: "",
            rol = source.getString("rol", "") ?: "",
            area = source.getString("area", "").orEmpty().ifEmpty { null },
            mfaEnabled = source.getBoolean("mfa", false)
        )
    }

    // ---- email recordado --------------------------------------------------
    var emailRecordado: String?
        get() = prefs.getString("remembered_email", null)
        set(v) {
            prefs.edit().putString("remembered_email", v).apply()
        }

    // ---- tema -------------------------------------------------------------
    var temaNoche: Boolean
        get() = prefs.getString("theme", "day") == "night"
        set(v) {
            prefs.edit().putString("theme", if (v) "night" else "day").apply()
        }
}
