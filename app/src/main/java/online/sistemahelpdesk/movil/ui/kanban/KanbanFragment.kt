package online.sistemahelpdesk.movil.ui.kanban

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Estados
import online.sistemahelpdesk.movil.data.Ticket
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.DialogosTicket

/**
 * Tablero kanban compartido por agente y coordinador.
 * Una sola lista vertical con cabeceras de columna; las tarjetas se arrastran
 * con pulsación larga y, al soltarlas, se confirma el cambio de estado.
 */
class KanbanFragment : Fragment() {

    private lateinit var lista: RecyclerView
    private lateinit var adapter: KanbanAdapter
    private lateinit var loading: LinearLayout
    private lateinit var vacio: LinearLayout
    private lateinit var error: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_kanban, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lista = view.findViewById(R.id.kanbanList)
        loading = view.findViewById(R.id.kanbanLoading)
        vacio = view.findViewById(R.id.kanbanEmpty)
        error = view.findViewById(R.id.kanbanError)

        adapter = KanbanAdapter(mutableListOf()) { t -> abrirDetalle(t) }
        lista.layoutManager = LinearLayoutManager(requireContext())
        lista.adapter = adapter
        ItemTouchHelper(arrastrable()).attachToRecyclerView(lista)

        view.findViewById<TextView>(R.id.kanbanRefresh).setOnClickListener { cargar() }
        cargar()
    }

    private fun arrastrable(): ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {
            override fun getMovementFlags(recycler: RecyclerView, holder: RecyclerView.ViewHolder): Int =
                if (adapter.esCabecera(holder.bindingAdapterPosition)) makeFlag(0, 0)
                else super.getMovementFlags(recycler, holder)

            override fun onMove(
                recycler: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder
            ): Boolean {
                if (adapter.esCabecera(to.bindingAdapterPosition)) return false
                val item = adapter.items.removeAt(from.bindingAdapterPosition)
                adapter.items.add(to.bindingAdapterPosition, item)
                adapter.notifyItemMoved(from.bindingAdapterPosition, to.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recycler: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(recycler, holder)
                comprometer(holder.bindingAdapterPosition)
            }
        }

    // ------------------------------------------------------------------ datos
    fun cargar() {
        if (!isAdded || view == null || !this::loading.isInitialized) return
        loading.visibility = View.VISIBLE
        vacio.visibility = View.GONE
        error.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val tickets = DataProvider.tickets()
                loading.visibility = View.GONE
                construir(tickets)
            } catch (e: Exception) {
                loading.visibility = View.GONE
                error.text = "Error al cargar el tablero: ${e.message}"
                error.visibility = View.VISIBLE
            }
        }
    }

    private fun construir(tickets: List<Ticket>) {
        adapter.items.clear()
        COLUMNAS.forEach { (col, titulo) ->
            val delCol = tickets
                .filter { t -> if (col == "archived") t.estado == Estados.ARCHIVADO else t.columna == col }
                .sortedWith(
                    compareBy<Ticket> { ordenPrioridad(it.prioNivel) }
                        .thenByDescending { it.fechaActualizacion.ifBlank { it.fechaCreacion } }
                )
            adapter.items.add(ItemKanban.Cabecera(col, titulo, delCol.size, colorDe(col)))
            delCol.forEach { adapter.items.add(ItemKanban.Tarjeta(it)) }
        }
        adapter.notifyDataSetChanged()
        vacio.visibility = if (tickets.isEmpty()) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------- cambio de estado
    private fun comprometer(pos: Int) {
        if (pos == RecyclerView.NO_POSITION) return cargar()
        val item = adapter.items.getOrNull(pos) ?: return cargar()
        if (item !is ItemKanban.Tarjeta) return
        val col = adapter.columnaEn(pos) ?: return cargar()
        if (col == item.ticket.columna) return
        aplicar(item.ticket, col)
    }

    private fun aplicar(t: Ticket, col: String) {
        val act = activity ?: return
        when (col) {
            "todo" -> guardar(t.id, if (t.tieneAgente) Estados.ASIGNADO else Estados.NUEVO, null)
            "progress" -> guardar(t.id, Estados.EN_PROCESO, null)
            "review" -> Dialogos.confirmar(
                act,
                "Marcar como resuelto",
                mensaje = "¿El ticket ${Ui.tkId(t.id)} ya está resuelto?",
                nota = "El solicitante podrá ver la solución y cerrarlo.",
                textoAceptar = "Resuelto",
                onAceptar = { guardar(t.id, Estados.RESUELTO, null) }
            )
            "done" -> Dialogos.areaTexto(
                act,
                "Cerrar ticket",
                referencia = Ui.tkId(t.id),
                mensaje = "Para cerrar el ticket debes registrar la solución aplicada.",
                hint = "Describe la solución (máx. 2000 caracteres)...",
                textoAceptar = "Cerrar ticket",
                colorAceptar = 0xFF047857.toInt()
            ) { sol ->
                if (sol.isBlank()) {
                    Ui.toast(act, "La solución no puede estar vacía")
                    cargar()
                } else {
                    guardar(t.id, Estados.CERRADO, sol)
                }
            }
            "archived" -> Dialogos.confirmar(
                act,
                "Archivar ticket",
                mensaje = "¿Archivar ${Ui.tkId(t.id)}? Dejará de aparecer en el tablero activo.",
                textoAceptar = "Archivar",
                peligroso = true,
                onAceptar = { guardar(t.id, Estados.ARCHIVADO, null) }
            )
            else -> cargar()
        }
    }

    private fun guardar(id: Int, estado: String, solucion: String?) {
        lifecycleScope.launch {
            try {
                DataProvider.actualizarTicket(id, estado, solucion)
                Ui.toast(requireContext(), "Ticket ${Ui.tkId(id)} → ${Ui.etiquetaEstado(estado)}")
            } catch (e: Exception) {
                Ui.toast(requireContext(), "No se pudo actualizar: ${e.message}")
            } finally {
                if (isAdded) cargar()
            }
        }
    }

    // ---------------------------------------------------------------- detalle
    private fun abrirDetalle(t: Ticket) {
        val act = activity ?: return
        lifecycleScope.launch {
            try {
                val d = DataProvider.detalleTicket(t.id)
                if (!isAdded) return@launch
                DialogosTicket.detalle(act, d, COLUMNAS) { col -> aplicar(t, col) }
            } catch (e: Exception) {
                Ui.toast(act, "No se pudo abrir el ticket: ${e.message}")
            }
        }
    }

    companion object {
        /** Columnas del tablero en el mismo orden que el kanban web. */
        val COLUMNAS = listOf(
            "todo" to "Por hacer",
            "progress" to "En proceso",
            "review" to "Resueltos",
            "done" to "Cerrados",
            "archived" to "Zona de archivo"
        )

        private fun colorDe(col: String): Int = when (col) {
            "todo" -> 0xFF1D4ED8.toInt()
            "progress" -> 0xFFC2410C.toInt()
            "review" -> 0xFF047857.toInt()
            "done" -> 0xFF475569.toInt()
            else -> 0xFF64748B.toInt()
        }

        private fun ordenPrioridad(nivel: String?): Int = when (Ui.normalizaPrioridad(nivel)) {
            "critical" -> 0
            "high" -> 1
            "medium" -> 2
            else -> 3
        }
    }
}
