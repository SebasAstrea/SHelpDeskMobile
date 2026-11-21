package online.sistemahelpdesk.movil.ui.common

import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import online.sistemahelpdesk.movil.core.Ui

/**
 * Diálogos compartidos: replican los modales del frontend
 * (`abrirModal`, modal de archivo, modal de solución, modal de detalle...).
 */
object Dialogos {

    /** Modal genérico con referencia, mensaje, nota y botones (Cancelar / Aceptar). */
    fun confirmar(
        activity: Activity,
        titulo: String,
        referencia: String? = null,
        mensaje: String,
        nota: String? = null,
        textoAceptar: String = "Aceptar",
        peligroso: Boolean = false,
        onAceptar: (() -> Unit)? = null
    ) {
        val padding = Ui.dp(20)
        val contenido = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        referencia?.let {
            contenido.addView(texto(activity, it, 14f, android.graphics.Typeface.BOLD, 0xFFB45309.toInt()))
        }
        contenido.addView(texto(activity, mensaje, 15f, android.graphics.Typeface.NORMAL, 0))
        if (nota != null) {
            contenido.addView(texto(activity, nota, 13f, android.graphics.Typeface.ITALIC, 0xFF64748B.toInt()).apply {
                setPadding(0, Ui.dp(10), 0, 0)
            })
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setView(contenido)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(textoAceptar) { _, _ -> onAceptar?.invoke() }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { b ->
                if (peligroso) b.setTextColor(0xFFB91C1C.toInt())
                b.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        dialog.show()
    }

    /** Modal con un único campo de texto (contraseña, código, etc.). */
    fun entrada(
        activity: Activity,
        titulo: String,
        hint: String,
        numerico: Boolean = false,
        maxLength: Int = 0,
        valor: String? = null,
        textoAceptar: String = "Aceptar",
        onAceptar: (String) -> Unit
    ) {
        val campo = EditText(activity).apply {
            this.hint = hint
            setText(valor ?: "")
            setSelection(text?.length ?: 0)
            if (numerico) inputType = InputType.TYPE_CLASS_NUMBER
            if (maxLength > 0) filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
            val p = Ui.dp(16)
            setPadding(p, p, p, p)
        }
        val wrap = LinearLayout(activity).apply {
            setPadding(Ui.dp(20), 0, Ui.dp(20), 0)
            addView(campo, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setView(wrap)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(textoAceptar) { _, _ -> onAceptar(campo.text.toString().trim()) }
            .show()
    }

    /** Modal de lista simple (selector de rol, contenedor, etc.). */
    fun lista(
        activity: Activity,
        titulo: String,
        items: Array<String>,
        seleccion: Int = -1,
        textoAceptar: String = "Aceptar",
        onAceptar: (Int) -> Unit
    ) {
        var elegido = seleccion
        AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setSingleChoiceItems(items, seleccion) { _, i -> elegido = i }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(textoAceptar) { _, _ -> if (elegido >= 0) onAceptar(elegido) }
            .show()
    }

    /** Modal informativo (respuesta de la IA, aviso de archivo no permitido...). */
    fun informacion(
        activity: Activity,
        titulo: String,
        cuerpo: String,
        textoAceptar: String = "Cerrar",
        scroll: Boolean = false
    ) {
        val padding = Ui.dp(20)
        val vista: ViewGroup = if (scroll) {
            ScrollView(activity).apply {
                addView(texto(activity, cuerpo, 15f, android.graphics.Typeface.NORMAL, 0).apply {
                    setPadding(padding, 0, padding, 0)
                })
            }
        } else {
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, 0, padding, 0)
                addView(texto(activity, cuerpo, 15f, android.graphics.Typeface.NORMAL, 0))
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setView(vista)
            .setPositiveButton(textoAceptar, null)
            .show()
    }

    /** Confirmación con campo textarea largo (solución del ticket, max 2000). */
    fun areaTexto(
        activity: Activity,
        titulo: String,
        referencia: String,
        mensaje: String,
        hint: String,
        textoAceptar: String,
        colorAceptar: Int,
        onAceptar: (String) -> Unit
    ) {
        val campo = EditText(activity).apply {
            this.hint = hint
            minLines = 4
            gravity = Gravity.TOP
            maxLines = 8
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
            val p = Ui.dp(14)
            setPadding(p, p, p, p)
        }
        val cont = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(20), 0, Ui.dp(20), 0)
            addView(texto(activity, referencia, 14f, android.graphics.Typeface.BOLD, 0xFF047857.toInt()))
            addView(texto(activity, mensaje, 15f, android.graphics.Typeface.NORMAL, 0).apply {
                setPadding(0, Ui.dp(8), 0, Ui.dp(8))
            })
            addView(campo, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(titulo)
            .setView(cont)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(textoAceptar) { _, _ -> onAceptar(campo.text.toString().trim()) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { b ->
                b.setTextColor(colorAceptar)
                b.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        dialog.show()
    }

    fun texto(
        contexto: android.content.Context,
        valor: String,
        size: Float,
        style: Int,
        color: Int
    ): TextView = TextView(contexto).apply {
        text = valor
        textSize = size
        if (style != 0) setTypeface(if (style == android.graphics.Typeface.ITALIC) typeface else null, style)
        if (color != 0) setTextColor(color)
        maxLines = if (TextUtils.isEmpty(valor)) 1 else Int.MAX_VALUE
    }
}
