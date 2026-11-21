package online.sistemahelpdesk.movil.ui.solicitante

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Ticket
import online.sistemahelpdesk.movil.ui.chat.ChatActivity
import online.sistemahelpdesk.movil.ui.common.BarraSuperior
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.ticket.FormularioTicketActivity

/** Port de `dashboard-solicitante.html` + `js/dashboard-solicitante.js`. */
class SolicitanteActivity : AppCompatActivity() {

    private lateinit var lista: LinearLayout
    private lateinit var loading: LinearLayout
    private lateinit var vacio: LinearLayout
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solicitante)
        Session.init(this)

        BarraSuperior.instalar(this, "MI DASHBOARD")

        lista = findViewById(R.id.ticketsList)
        loading = findViewById(R.id.loadingTickets)
        vacio = findViewById(R.id.emptyState)
        error = findViewById(R.id.errorTickets)

        val sesion = Session.sesion()
        findViewById<TextView>(R.id.welcomeName).text =
            "¡Hola, ${sesion?.nombre?.ifBlank { "usuario" } ?: "usuario"}! 👋"

        findViewById<android.view.View>(R.id.cardCrear).setOnClickListener { abrirFormulario() }
        findViewById<android.view.View>(R.id.emptyAction).setOnClickListener { abrirFormulario() }
        findViewById<android.view.View>(R.id.cardHistorial).setOnClickListener {
            val sv = findViewById<android.widget.ScrollView>(R.id.scrollMain)
            val seccion = findViewById<View>(R.id.historySection)
            sv.post { sv.smoothScrollTo(0, seccion.top) }
        }
        findViewById<TextView>(R.id.refreshBtn).setOnClickListener { cargar() }
        findViewById<TextView>(R.id.chatFab).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_MODO, ChatActivity.SOLO_GLOBAL))
        }

        cargar()
    }

    private fun abrirFormulario() {
        startActivity(Intent(this, FormularioTicketActivity::class.java))
    }

    private fun cargar() {
        loading.visibility = View.VISIBLE
        vacio.visibility = View.GONE
        error.visibility = View.GONE
        lista.removeAllViews()

        lifecycleScope.launch {
            try {
                val mios = DataProvider.tickets()
                    .filter { it.idSolicitante == (Session.sesion()?.userId ?: it.idSolicitante) }
                    .sortedByDescending { it.fechaCreacion }
                pintar(mios)
            } catch (e: Exception) {
                error.text = "Error al cargar tus tickets: ${e.message}"
                error.visibility = View.VISIBLE
            } finally {
                loading.visibility = View.GONE
            }
        }
    }

    private fun pintar(tickets: List<Ticket>) {
        lista.removeAllViews()
        if (tickets.isEmpty()) {
            vacio.visibility = View.VISIBLE
            return
        }
        vacio.visibility = View.GONE
        tickets.forEach { t ->
            val fila = layoutInflater.inflate(R.layout.item_ticket_solicitante, lista, false)
            fila.findViewById<TextView>(R.id.ticketId).text = Ui.tkId(t.id)
            fila.findViewById<TextView>(R.id.ticketSubject).text = t.asunto
            fila.findViewById<TextView>(R.id.ticketDate).text =
                "📅 ${Ui.fechaCorta(t.fechaCreacion)} • ${t.catNombre.ifBlank { "Sin categoría" }}"
            val pill = fila.findViewById<TextView>(R.id.ticketStatus)
            pill.text = Ui.etiquetaEstado(t.estado)
            pill.background = Ui.pill(Ui.color(t.estado))
            Ui.aplicarNeonPill(pill)
            val boton = fila.findViewById<TextView>(R.id.btnVerSolucion)
            if (!t.solucion.isNullOrBlank()) {
                boton.visibility = View.VISIBLE
                boton.setOnClickListener { verSolucion(t) }
            } else {
                boton.visibility = View.GONE
            }
            lista.addView(fila)
        }
    }

    private fun verSolucion(t: Ticket) {
        Dialogos.informacion(
            this,
            "Solución del Ticket ${Ui.tkId(t.id)}",
            t.solucion?.takeIf { it.isNotBlank() }
                ?: "Este ticket aún no tiene una solución registrada.",
            scroll = true
        )
    }
}
