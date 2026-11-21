package online.sistemahelpdesk.movil.ui.kanban

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Ticket

/** Elemento del tablero: cabecera de columna o tarjeta de ticket. */
sealed class ItemKanban {
    data class Cabecera(val col: String, val titulo: String, val contador: Int, val color: Int) : ItemKanban()
    data class Tarjeta(val ticket: Ticket) : ItemKanban()
}

/**
 * Lista única con cabeceras fijas y tarjetas arrastrables (en lugar de cinco
 * columnas horizontales, que en móvil apenas se pueden usar).
 */
class KanbanAdapter(
    val items: MutableList<ItemKanban>,
    private val onClick: (Ticket) -> Unit
) : RecyclerView.Adapter<KanbanAdapter.Holder>() {

    sealed class Holder(v: View) : RecyclerView.ViewHolder(v) {
        class CabeceraHolder(v: View) : Holder(v) {
            val barra: View = v.findViewById(R.id.colBar)
            val titulo: TextView = v.findViewById(R.id.colTitulo)
            val contador: TextView = v.findViewById(R.id.colContador)
        }

        class TarjetaHolder(v: View) : Holder(v) {
            val raiz: LinearLayout = v as LinearLayout
            val prioBar: View = v.findViewById(R.id.prioBar)
            val categoria: TextView = v.findViewById(R.id.cardCategoria)
            val info: TextView = v.findViewById(R.id.cardInfo)
            val titulo: TextView = v.findViewById(R.id.cardTitulo)
            val id: TextView = v.findViewById(R.id.cardId)
            val estado: TextView = v.findViewById(R.id.cardEstado)
            val avatar: TextView = v.findViewById(R.id.cardAvatar)
            val agente: TextView = v.findViewById(R.id.cardAgente)
            val fecha: TextView = v.findViewById(R.id.cardFecha)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is ItemKanban.Cabecera) TYPE_CABECERA else TYPE_TARJETA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        if (viewType == TYPE_CABECERA) {
            Holder.CabeceraHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_kanban_header, parent, false)
            )
        } else {
            Holder.TarjetaHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_kanban_card, parent, false)
            )
        }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        when (val item = items[position]) {
            is ItemKanban.Cabecera -> {
                val h = holder as Holder.CabeceraHolder
                h.barra.setBackgroundColor(item.color)
                h.titulo.text = item.titulo
                h.contador.text = item.contador.toString()
                h.contador.background = Ui.pill(item.color)
                Ui.aplicarNeonPill(h.contador)
            }
            is ItemKanban.Tarjeta -> {
                val h = holder as Holder.TarjetaHolder
                val t = item.ticket
                h.prioBar.setBackgroundColor(Ui.prioridadColor(t.prioNivel))
                h.categoria.text = "${t.catNombre.ifBlank { "Sin categoría" }} • ${t.prioNivel}"
                h.titulo.text = t.asunto
                h.id.text = Ui.tkId(t.id)
                h.estado.text = Ui.etiquetaEstado(t.estado)
                h.estado.background = Ui.pill(Ui.color(t.estado))
                Ui.aplicarNeonPill(h.estado)
                if (t.tieneAgente) {
                    h.avatar.text = Ui.iniciales(t.agente)
                    h.avatar.background = Ui.pill(Ui.colorDeNombre(t.agente))
                    h.agente.text = t.agente
                } else {
                    h.avatar.text = "—"
                    h.avatar.background = Ui.pill(0xFF64748B.toInt())
                    h.agente.text = "Sin asignar"
                }
                h.fecha.text = Ui.fecha(t.fechaActualizacion.ifBlank { t.fechaCreacion })
                h.raiz.setOnClickListener { onClick(t) }
                h.info.setOnClickListener { onClick(t) }
                h.raiz.setOnLongClickListener {
                    it.performClick()
                    false
                }
            }
        }
    }

    /** Índice de la cabecera de la columna en la que cae `position`. */
    fun columnaEn(position: Int): String? {
        var i = position.coerceIn(0, items.size - 1)
        while (i >= 0) {
            val cab = items[i]
            if (cab is ItemKanban.Cabecera) return cab.col
            i--
        }
        return null
    }

    fun esCabecera(position: Int): Boolean =
        position in items.indices && items[position] is ItemKanban.Cabecera

    companion object {
        const val TYPE_CABECERA = 0
        const val TYPE_TARJETA = 1
    }
}
