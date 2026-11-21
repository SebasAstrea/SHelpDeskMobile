package online.sistemahelpdesk.movil.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/** Error HTTP con el `detail` del backend (o el cuerpo completo). */
class ApiException(val code: Int, val detalle: String) : Exception(detalle)

/** El backend no responde: la app pasa a modo datos locales (mock). */
class SinConexion(mensaje: String = "Sin conexión con el backend") : Exception(mensaje)

/**
 * Cliente HTTP equivalente a `js/config.js`:
 * - misma URL base (Cloudflare Tunnel),
 * - hasta 8 reintentos ante 5xx / errores de red con backoff `min(250 * intento, 800)` ms,
 * - los 4xx no se reintentan (los gestiona cada pantalla).
 *
 * Diferencia: si la red está caída (DNS/conexión) no se reintentan 8 veces para no
 * bloquear la UI; se marca `SIN_CONEXION` y las pantallas sirven datos locales.
 */
object ApiClient {

    const val BASE = "https://api.sistemahelpdesk.online/api"
    private const val MAX_INTENTOS = 8
    private const val TIMEOUT_CONEXION = 4000
    private const val TIMEOUT_LECTURA = 8000

    enum class Modo { DESCONOCIDO, EN_LINEA, SIN_CONEXION }

    @Volatile
    var modo: Modo = Modo.DESCONOCIDO

    val enLinea: Boolean get() = modo == Modo.EN_LINEA

    // ---- operaciones ------------------------------------------------------
    suspend fun get(path: String, token: String? = null): String =
        request("GET", path, null, token)

    suspend fun post(path: String, body: String?, token: String? = null): String =
        request("POST", path, body, token)

    suspend fun patch(path: String, body: String?, token: String? = null): String =
        request("PATCH", path, body, token)

    suspend fun delete(path: String, token: String? = null): String =
        request("DELETE", path, null, token)

    /** Comprobación de salud (`GET /health`) con timeout corto. Devuelve true si responde 200. */
    suspend fun salud(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = abrir("$BASE/health", "GET", null, null, 2500, 2500)
            val code = conn.responseCode
            modo = if (code == 200) Modo.EN_LINEA else Modo.SIN_CONEXION
            code == 200
        } catch (e: Exception) {
            modo = Modo.SIN_CONEXION
            false
        } finally {
            conn?.disconnect()
        }
    }

    // ---- núcleo -----------------------------------------------------------
    private suspend fun request(
        metodo: String,
        path: String,
        body: String?,
        token: String?
    ): String = withContext(Dispatchers.IO) {
        var intento = 0
        var ultimoError: Exception? = null
        while (intento < MAX_INTENTOS) {
            intento++
            var conn: HttpURLConnection? = null
            try {
                conn = abrir("$BASE$path", metodo, body, token, TIMEOUT_CONEXION, TIMEOUT_LECTURA)
                val code = conn.responseCode
                val texto = leer(conn, code)
                if (code in 200..299) {
                    modo = Modo.EN_LINEA
                    return@withContext texto
                }
                if (code in 300..399) {
                    // Redirecciones desactivadas a proposito: evitan degradar https -> http.
                    throw ApiException(code, "Redirección no permitida por la app.")
                }
                if (esOrigenCaido(code)) {
                    // Cloudflare/Tunnel sin origen (530 - Argotunnel, 502/503/504, 520-530):
                    // el edge responde pero el backend no; se pasa a datos locales sin reintentar.
                    modo = Modo.SIN_CONEXION
                    throw SinConexion("El servidor no está disponible en este momento.")
                }
                modo = Modo.EN_LINEA
                if (code >= 500) {
                    ultimoError = ApiException(code, extraerDetalle(texto, code))
                    delay(minOf(250L * intento, 800L))
                    continue
                }
                throw ApiException(code, extraerDetalle(texto, code))
            } catch (e: SinConexion) {
                throw e
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                // DNS / sin ruta / timeout: la red está caída, no tiene sentido reintentar 8 veces.
                modo = Modo.SIN_CONEXION
                ultimoError = SinConexion(mensajeDeRed(e))
                if (e is UnknownHostException) throw ultimoError
                if (intento >= 2) throw ultimoError
                delay(250L * intento)
            } finally {
                conn?.disconnect()
            }
        }
        throw ultimoError ?: SinConexion()
    }

    private fun abrir(
        url: String,
        metodo: String,
        body: String?,
        token: String?,
        connectTimeout: Int,
        readTimeout: Int
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = metodo
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        // Sin seguimiento de redirecciones: impide un salto https -> http no controlado.
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return conn
    }

    private fun leer(conn: HttpURLConnection, code: Int): String {
        val stream = try {
            if (code >= 400) conn.errorStream else conn.inputStream
        } catch (_: Exception) {
            null
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun extraerDetalle(texto: String, code: Int): String {
        if (texto.isBlank()) return "Error $code"
        return try {
            val o = JSONObject(texto)
            when {
                o.has("detail") -> when (val d = o.get("detail")) {
                    is String -> d
                    else -> d.toString()
                }
                o.has("message") -> o.optString("message")
                else -> texto.take(300)
            }
        } catch (_: Exception) {
            texto.take(300)
        }
    }

    /** Errores del proxy de borde (Cloudflare/Tunnel) cuando el origen no responde. */
    private fun esOrigenCaido(code: Int): Boolean =
        code in 502..504 || code in 520..530

    private fun mensajeDeRed(e: IOException): String =
        "Error de conexión. Por favor, verifica si el servidor está en funcionamiento."

    fun json(pares: Map<String, Any?>): String {
        val o = JSONObject()
        pares.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        return o.toString()
    }
}
