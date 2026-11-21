package online.sistemahelpdesk.movil.ui.common

import android.app.Activity
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.TicketDetalle

/** Modal de detalle de ticket (usa los estilos de `Dialogos`). */
object DialogosTicket {

    private fun fila(contenedor: LinearLayout, etiqueta: String, valor: String) {
        val tv = Dialogos.texto(contenedor.context, "${etiqueta}: ${valor}", 14f, Typeface.NORMAL, 0)
        tv.setPadding(0, Ui.dp(5), 0, 0)
        contenedor.addView(tv)
    }

    fun detalle(
        activity: Activity,
        d: TicketDetalle,
        columnas: List<Pair<String, String>>,
        onMover: (String) -> Unit
    ) {
        val t = d.ticket
        val cont = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(20), 0, Ui.dp(20), 0)
        }

        val pil = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        pil.addView(Dialogos.texto(activity, Ui.tkId(t.id), 14f, Typeface.BOLD, 0xFFB45309.toInt()))
        val pillEstado = Dialogos.texto(activity, " " + Ui.etiquetaEstado(t.estado) + " ", 12f, Typeface.BOLD, 0xFFFFFFFF.toInt()).apply {
            background = Ui.pill(Ui.color(t.estado))
            Ui.aplicarNeonPill(this)
        }
        val pillPrio = Dialogos.texto(activity, " " + t.prioNivel + " ", 12f, Typeface.BOLD, 0xFFFFFFFF.toInt()).apply {
            background = Ui.pill(Ui.prioridadPill(t.prioNivel))
            Ui.aplicarNeonPill(this)
        }
        pil.addView(pillEstado)
        pil.addView(pillPrio)
        cont.addView(pil)

        cont.addView(
            Dialogos.texto(activity, t.asunto, 17f, Typeface.BOLD, 0).apply {
                setPadding(0, Ui.dp(8), 0, 0)
            })
        cont.addView(
            Dialogos.texto(activity, t.descripcion, 14f, Typeface.NORMAL, 0xFF334155.toInt()).apply {
                setPadding(0, Ui.dp(4), 0, Ui.dp(8))
            })

        fila(cont, "Categoría", t.catNombre.ifBlank { "—" })
        fila(cont, "Solicitante", d.solicitante?.nombre ?: "—")
        fila(cont, "Agente", d.agente?.nombre ?: t.agente.ifBlank { "Sin asignar" })
        fila(cont, "Creado", Ui.fechaHora(t.fechaCreacion))
        fila(cont, "Actualizado", Ui.fechaHora(t.fechaActualizacion))

        t.solucion?.takeIf { it.isNotBlank() }?.let {
            fila(cont, "Solución", it)
        }

        if (d.adjuntos.isNotEmpty()) {
            cont.addView(
                Dialogos.texto(activity, "Adjuntos", 13f, Typeface.BOLD, 0xFF64748B.toInt())
                    .apply { setPadding(0, Ui.dp(10), 0, 0) })
            d.adjuntos.forEach { a ->
                cont.addView(
                    Dialogos.texto(
                        activity,
                        "📎 ${a.nombre} • ${Ui.tamano(a.tamano)} • ${Ui.fechaHora(a.fecha)}",
                        12f, Typeface.NORMAL, 0xFF3B7491.toInt()
                    ).apply { setPadding(0, Ui.dp(3), 0, 0) })
            }
        }

        d.analisis?.let { ia ->
            cont.addView(
                Dialogos.texto(
                    activity,
                    "🤖 Análisis IA: prioridad ${ia.prioridadIa} • categoría ${ia.categoriaIa} • " +
                        "confianza ${"%.0f".format(ia.confianza * 100)}% • ${ia.modelo}",
                    13f, Typeface.ITALIC, 0xFF7C3AED.toInt()
                ).apply { setPadding(0, Ui.dp(8), 0, 0) })
        }

        if (d.historial.isNotEmpty()) {
            cont.addView(
                Dialogos.texto(activity, "Historial de estados", 13f, Typeface.BOLD, 0xFF64748B.toInt())
                    .apply { setPadding(0, Ui.dp(10), 0, 0) })
            d.historial.takeLast(6).forEach { h ->
                cont.addView(
                    Dialogos.texto(
                        activity,
                        "• ${Ui.etiquetaEstado(h.anterior)} → ${Ui.etiquetaEstado(h.nuevo)} (${Ui.fechaHora(h.fecha)})",
                        12f, Typeface.NORMAL, 0xFF64748B.toInt()
                    ).apply { setPadding(0, Ui.dp(3), 0, 0) })
            }
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(cont, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Detalle del ticket")
            .setView(scroll)
            .setNegativeButton("Cerrar", null)
            .setNeutralButton("Mover estado") { _, _ ->
                Dialogos.lista(
                    activity,
                    "Mover a...",
                    columnas.map { it.second }.toTypedArray()
                ) { i -> onMover(columnas[i].first) }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.let {
                it.setTextColor(0xFF1D4ED8.toInt())
                it.setTypeface(null, Typeface.BOLD)
            }
        }
        dialog.show()
    }
}
