package online.sistemahelpdesk.movil.core

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import online.sistemahelpdesk.movil.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utilidades visuales compartidas: toasts, pills (relleno vivo + neón blanco),
 * formateo de fechas es-ES y mensajes de estado de formulario.
 */
object Ui {

    private val localeEs: Locale = Locale("es", "ES")

    fun toast(contexto: Context, mensaje: String) {
        Toast.makeText(contexto, mensaje, Toast.LENGTH_SHORT).show()
    }

    /**
     * Bloquea capturas de pantalla y grabación de la ventana (`FLAG_SECURE`).
     * Se usa en las pantallas con credenciales para que no aparezcan en capturas.
     */
    fun protegerCapturas(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Pill tipo `.status-pill`/`.role-pill`/`.tag` del CSS: color sólido, texto blanco, borde claro. */
    fun pill(colorHex: Int? = null): GradientDrawable {
        val color = colorHex ?: 0xFF475569.toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(color)
            setStroke(dp(1), 0x99FFFFFF.toInt())
        }
    }

    fun pill(contexto: Context, @ColorRes colorRes: Int): GradientDrawable =
        pill().apply { setColor(ContextCompat.getColor(contexto, colorRes)) }

    /** Neón suave blanco que usan todas las pills del web. */
    fun aplicarNeonPill(tv: TextView) {
        tv.setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x80FFFFFF.toInt())
    }

    fun dp(valor: Int): Int = (valor * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    /** Texto visible de un estado de ticket ("En proceso", "Resuelto"...). */
    fun etiquetaEstado(estado: String?): String = online.sistemahelpdesk.movil.data.Estados.etiqueta(estado)

    fun dpF(valor: Float): Float = valor * android.content.res.Resources.getSystem().displayMetrics.density

    // ---- fechas -----------------------------------------------------------
    private fun parse(fecha: String?): Date? {
        if (fecha.isNullOrBlank()) return null
        val limpio = fecha.replace("T", " ").substringBefore(".")
        val patrones = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        for (p in patrones) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                return sdf.parse(limpio)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** `18 ago 2026` */
    fun fechaCorta(fecha: String?): String {
        val d = parse(fecha) ?: return "—"
        return SimpleDateFormat("d MMM yyyy", localeEs).format(d)
    }

    /** `18/08/2026` */
    fun fecha(fecha: String?): String {
        val d = parse(fecha) ?: return "—"
        return SimpleDateFormat("dd/MM/yyyy", localeEs).format(d)
    }

    /** `18/08/2026 14:32` */
    fun fechaHora(fecha: String?): String {
        val d = parse(fecha) ?: return "—"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", localeEs).format(d)
    }

    /** `14:32` */
    fun hora(fecha: String?): String {
        val d = parse(fecha) ?: return ""
        return SimpleDateFormat("HH:mm", localeEs).format(d)
    }

    fun ahoraIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

    fun haceDias(dias: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -dias)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(c.time)
    }

    /** `#TK-0001` */
    fun tkId(id: Int): String = "#TK-" + id.toString().padStart(4, '0')

    /** Formato de tamaño de archivo del formulario. */
    fun tamano(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(localeEs, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(localeEs, "%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(localeEs, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun mb(bytes: Long): String = String.format(localeEs, "%.1f MB", bytes / 1_000_000.0)

    /** Iniciales de 2 letras para avatares. */
    fun iniciales(nombre: String?): String {
        val partes = nombre?.trim()?.split(Regex("\\s+")) ?: listOf("?")
        val a = partes.getOrNull(0)?.firstOrNull()?.uppercaseChar() ?: '?'
        val b = partes.getOrNull(1)?.firstOrNull()?.uppercaseChar() ?: ' '
        return "$a$b"
    }

    /** Color estable por nombre (avatar hsl del web). */
    fun colorDeNombre(nombre: String?): Int {
        val n = nombre ?: "sin"
        var h = 0
        for (c in n) h = (h * 31 + c.code) % 360
        return Color.HSVToColor(floatArrayOf(h.toFloat(), 0.6f, 0.55f))
    }

    fun color(tipo: String?): Int = when (tipo) {
        "nuevo" -> 0xFF1D4ED8.toInt()
        "asignado" -> 0xFF0369A1.toInt()
        "en_proceso", "escalado" -> 0xFFC2410C.toInt()
        "resuelto" -> 0xFF047857.toInt()
        "cerrado", "archivado" -> 0xFF475569.toInt()
        else -> 0xFF475569.toInt()
    }

    fun prioridadColor(nivel: String?): Int = when (normalizaPrioridad(nivel)) {
        "critical" -> 0xFFFF4444.toInt()
        "high" -> 0xFFFF8800.toInt()
        "medium" -> 0xFFFFCC00.toInt()
        else -> 0xFF44BB44.toInt()
    }

    fun prioridadPill(nivel: String?): Int = when (normalizaPrioridad(nivel)) {
        "critical" -> 0xFFB91C1C.toInt()
        "high" -> 0xFFC2410C.toInt()
        "medium" -> 0xFFA16207.toInt()
        else -> 0xFF15803D.toInt()
    }

    fun normalizaPrioridad(nivel: String?): String = when (nivel?.lowercase()) {
        "crítica", "critica", "critical" -> "critical"
        "alta", "high" -> "high"
        "media", "medium" -> "medium"
        else -> "low"
    }

    fun categoriaClase(cat: String?): String = when {
        cat == null -> "other"
        cat.contains("hardware", true) -> "hardware"
        cat.contains("software", true) -> "software"
        cat.contains("red", true) -> "network"
        cat.contains("cuentas", true) || cat.contains("accesos", true) -> "access"
        else -> "other"
    }

    fun categoriaColor(cat: String?): Int = when (categoriaClase(cat)) {
        "hardware" -> 0xFFC2410C.toInt()
        "software" -> 0xFF7C3AED.toInt()
        "network" -> 0xFF1D4ED8.toInt()
        "access" -> 0xFFBE123C.toInt()
        else -> 0xFF047857.toInt()
    }

    fun rolColor(rol: String?): Int = when (rol) {
        "administrador" -> 0xFFB45309.toInt()
        "coordinador" -> 0xFF7C3AED.toInt()
        "agente" -> 0xFF1D4ED8.toInt()
        else -> 0xFF047857.toInt()
    }

    fun estadoUsuarioColor(estado: String?): Int =
        if (estado == "activo") 0xFF047857.toInt() else 0xFF475569.toInt()

    // ---- vistas de estado -------------------------------------------------
    fun mostrarError(view: View?, texto: String?) {
        view ?: return
        val tv = view as? TextView ?: return
        if (texto.isNullOrBlank()) {
            view.visibility = View.GONE
        } else {
            tv.text = texto
            view.visibility = View.VISIBLE
        }
    }

    fun centrar(view: TextView) {
        view.gravity = Gravity.CENTER
        view.typeface = Typeface.DEFAULT_BOLD
    }
}
