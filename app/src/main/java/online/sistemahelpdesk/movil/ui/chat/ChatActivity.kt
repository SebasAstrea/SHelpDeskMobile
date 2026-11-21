package online.sistemahelpdesk.movil.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.ContactoPrivado
import online.sistemahelpdesk.movil.data.MensajeChat
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.data.Ticket

/**
 * Port de `chat-widget` + `js/chat-global.js`: tres hilos (IA, global y privado)
 * con sondeo periódico y respuesta local de la IA cuando no hay backend.
 */
class ChatActivity : AppCompatActivity() {

    private var modo: String = IA
    private var soloGlobal = false
    private var ticketAdjunto: Ticket? = null
    private var contacto: ContactoPrivado? = null

    private val itemsIa = mutableListOf<ItemChat>()
    private val itemsGlobal = mutableListOf<ItemChat>()
    private val itemsPrivado = mutableListOf<ItemChat>()
    private val historial = mutableListOf<Pair<String, String>>()
    private val contactos = mutableListOf<ContactoPrivado>()
    private var ultimoGlobalId = 0
    private var ultimoPrivadoId = 0

    private lateinit var rvIa: RecyclerView
    private lateinit var rvGlobal: RecyclerView
    private lateinit var rvPrivado: RecyclerView
    private lateinit var rvContactos: RecyclerView
    private lateinit var adapterIa: MsgAdapter
    private lateinit var adapterGlobal: MsgAdapter
    private lateinit var adapterPrivado: MsgAdapter
    private lateinit var contactoAdapter: ContactoAdapter

    private lateinit var input: EditText
    private lateinit var vistaContactos: View
    private lateinit var vistaConversacion: View
    private lateinit var typing: TextView
    private lateinit var subtitulo: TextView
    private lateinit var nombreContacto: TextView
    private lateinit var btnAdjuntar: TextView
    private lateinit var btnEnviar: TextView

    private lateinit var tabIa: TextView
    private lateinit var tabGlobal: TextView
    private lateinit var tabPrivado: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val sondeo = object : Runnable {
        override fun run() {
            refrescar()
            handler.postDelayed(this, 4000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        Session.init(this)

        rvIa = findViewById(R.id.msgIa)
        rvGlobal = findViewById(R.id.msgGlobal)
        rvPrivado = findViewById(R.id.msgPrivado)
        rvContactos = findViewById(R.id.listaContactos)
        input = findViewById(R.id.inputMensaje)
        vistaContactos = findViewById(R.id.vistaContactos)
        vistaConversacion = findViewById(R.id.vistaConversacion)
        typing = findViewById(R.id.typingIndicator)
        subtitulo = findViewById(R.id.chatSubtitle)
        nombreContacto = findViewById(R.id.nombreContacto)
        btnAdjuntar = findViewById(R.id.btnAdjuntarTicket)
        btnEnviar = findViewById(R.id.btnEnviar)
        tabIa = findViewById(R.id.tabIa)
        tabGlobal = findViewById(R.id.tabGlobal)
        tabPrivado = findViewById(R.id.tabPrivado)

        adapterIa = MsgAdapter(itemsIa)
        adapterGlobal = MsgAdapter(itemsGlobal)
        adapterPrivado = MsgAdapter(itemsPrivado)
        contactoAdapter = ContactoAdapter(contactos) { c -> abrirConversacion(c) }

        prepararHilo(rvIa, adapterIa)
        prepararHilo(rvGlobal, adapterGlobal)
        prepararHilo(rvPrivado, adapterPrivado)
        rvContactos.layoutManager = LinearLayoutManager(this)
        rvContactos.adapter = contactoAdapter

        findViewById<TextView>(R.id.btnCerrar).setOnClickListener { finish() }
        btnEnviar.setOnClickListener { enviar() }
        btnAdjuntar.setOnClickListener { adjuntarTicket() }
        findViewById<TextView>(R.id.btnVolverContactos).setOnClickListener { cerrarConversacion() }

        soloGlobal = intent.getStringExtra(EXTRA_MODO) == SOLO_GLOBAL
        if (soloGlobal) {
            tabIa.visibility = View.GONE
            tabPrivado.visibility = View.GONE
        }
        tabIa.setOnClickListener { setModo(IA) }
        tabGlobal.setOnClickListener { setModo(GLOBAL) }
        tabPrivado.setOnClickListener { setModo(PRIVADO) }

        setModo(if (soloGlobal) GLOBAL else (intent.getStringExtra(EXTRA_MODO) ?: IA))

        intent.getStringExtra(EXTRA_PROMPT)?.takeIf { it.isNotBlank() }?.let { prompt ->
            setModo(IA)
            input.setText(prompt)
            enviar()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(sondeo, 4000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(sondeo)
    }

    private fun prepararHilo(rv: RecyclerView, adapter: MsgAdapter) {
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun setModo(nuevo: String) {
        modo = nuevo
        pintarPestanas()
        rvIa.visibility = if (modo == IA) View.VISIBLE else View.GONE
        rvGlobal.visibility = if (modo == GLOBAL) View.VISIBLE else View.GONE
        val enPrivado = modo == PRIVADO
        vistaContactos.visibility = if (enPrivado && contacto == null) View.VISIBLE else View.GONE
        vistaConversacion.visibility = if (enPrivado && contacto != null) View.VISIBLE else View.GONE
        btnAdjuntar.visibility = if (modo == IA) View.VISIBLE else View.GONE

        input.hint = when {
            modo == IA -> "Pregúntale a la IA..."
            modo == GLOBAL -> "Escribe tu mensaje..."
            contacto != null -> "Mensaje a ${contacto?.nombre ?: ""}"
            else -> "Selecciona un contacto"
        }

        when (modo) {
            IA -> {
                subtitulo.text = if (ticketAdjunto != null) "Asistente • contexto ${Ui.tkId(ticketAdjunto!!.id)}"
                    else "Asistente de mesa de ayuda"
                if (itemsIa.isEmpty()) {
                    adapterIa.agregar(
                        ItemChat(
                            "¡Hola! Soy el asistente de SHelpDesk. Puedo ayudarte a redactar, priorizar o resolver tickets.",
                            "Asistente • demo local", ItemChat.SISTEMA
                        )
                    )
                }
            }
            GLOBAL -> {
                subtitulo.text = "Sala general"
                if (itemsGlobal.isEmpty()) cargarGlobal()
            }
            else -> {
                subtitulo.text = contacto?.nombre ?: "Mensajes privados"
                if (contactos.isEmpty()) cargarContactos()
            }
        }
        bajarAlFinal(rvIa, adapterIa, modo == IA)
        bajarAlFinal(rvGlobal, adapterGlobal, modo == GLOBAL)
        bajarAlFinal(rvPrivado, adapterPrivado, modo == PRIVADO && contacto != null)
    }

    private fun pintarPestanas() {
        data class Tab(val v: TextView, val activo: Boolean)
        listOf(Tab(tabIa, modo == IA), Tab(tabGlobal, modo == GLOBAL), Tab(tabPrivado, modo == PRIVADO))
            .forEach { (v, activo) ->
                if (v.visibility == View.GONE) return@forEach
                if (activo) {
                    v.background = getDrawable(R.drawable.bg_btn_primary)
                    v.setTypeface(null, android.graphics.Typeface.BOLD)
                    v.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    v.background = null
                    v.setTypeface(null, android.graphics.Typeface.NORMAL)
                    v.setTextColor(0xB3FFFFFF.toInt())
                }
            }
    }

    // ------------------------------------------------------------------ envío
    private fun enviar() {
        val texto = input.text.toString().trim()
        if (texto.isBlank()) return
        input.setText("")
        when (modo) {
            IA -> enviarIa(texto)
            GLOBAL -> enviarGlobal(texto)
            PRIVADO -> enviarPrivado(texto)
        }
    }

    private fun enviarIa(texto: String) {
        adapterIa.agregar(ItemChat(texto, "Tú • ${Ui.hora(Ui.ahoraIso())}", ItemChat.PROPIO))
        bajarAlFinal(rvIa, adapterIa, true)
        typing.visibility = View.VISIBLE
        val contexto = ticketAdjunto
        lifecycleScope.launch {
            try {
                val (respuesta, meta) = DataProvider.chatIa(texto, historial.toList(), contexto?.id)
                historial.add(texto to respuesta)
                adapterIa.agregar(ItemChat(respuesta, "Asistente • $meta", ItemChat.AJENO))
            } catch (e: Exception) {
                adapterIa.agregar(
                    ItemChat("No pude responder: ${e.message}", "Error", ItemChat.SISTEMA)
                )
            } finally {
                typing.visibility = View.GONE
                bajarAlFinal(rvIa, adapterIa, true)
            }
        }
    }

    private fun enviarGlobal(texto: String) {
        lifecycleScope.launch {
            try {
                val m = DataProvider.enviarGlobal(texto)
                adapterGlobal.agregar(
                    ItemChat(m.mensaje, "Tú • ${Ui.hora(m.fecha.ifBlank { Ui.ahoraIso() })}", ItemChat.PROPIO)
                )
                if (m.id > ultimoGlobalId) ultimoGlobalId = m.id
                bajarAlFinal(rvGlobal, adapterGlobal, true)
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "No se pudo enviar: ${e.message}")
            }
        }
    }

    private fun enviarPrivado(texto: String) {
        val c = contacto
        if (c == null) {
            Ui.toast(this, "Selecciona primero un contacto")
            return
        }
        lifecycleScope.launch {
            try {
                val m = DataProvider.enviarPrivada(c.id, texto)
                adapterPrivado.agregar(
                    ItemChat(m.mensaje, "Tú • ${Ui.hora(m.fecha.ifBlank { Ui.ahoraIso() })}", ItemChat.PROPIO)
                )
                if (m.id > ultimoPrivadoId) ultimoPrivadoId = m.id
                bajarAlFinal(rvPrivado, adapterPrivado, true)
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "No se pudo enviar: ${e.message}")
            }
        }
    }

    private fun bajarAlFinal(rv: RecyclerView, adapter: RecyclerView.Adapter<*>, activo: Boolean) {
        if (!activo || adapter.itemCount == 0) return
        rv.post { rv.scrollToPosition(adapter.itemCount - 1) }
    }

    // ------------------------------------------------------------------ carga
    private fun cargarGlobal() {
        lifecycleScope.launch {
            try {
                val mensajes = DataProvider.chatGlobal(0)
                val items = mensajes.map { aItem(it) }
                ultimoGlobalId = mensajes.maxOfOrNull { it.id } ?: 0
                adapterGlobal.reemplazar(items)
                bajarAlFinal(rvGlobal, adapterGlobal, modo == GLOBAL)
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "Error al cargar el chat: ${e.message}")
            }
        }
    }

    private fun aItem(m: MensajeChat): ItemChat {
        val mio = m.userId == (Session.sesion()?.userId ?: -1)
        val meta = if (mio) "Tú • ${Ui.hora(m.fecha)}"
        else "${m.nombre} • ${Roles.etiqueta(m.rol)} • ${Ui.hora(m.fecha)}"
        return ItemChat(m.mensaje, meta, if (mio) ItemChat.PROPIO else ItemChat.AJENO)
    }

    private fun cargarContactos() {
        lifecycleScope.launch {
            try {
                contactoAdapter.reemplazar(DataProvider.contactos())
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "Error al cargar contactos: ${e.message}")
            }
        }
    }

    private fun abrirConversacion(c: ContactoPrivado) {
        contacto = c
        vistaContactos.visibility = View.GONE
        vistaConversacion.visibility = View.VISIBLE
        nombreContacto.text = c.nombre
        subtitulo.text = c.nombre
        input.hint = "Mensaje a ${c.nombre}"
        adapterPrivado.reemplazar(emptyList())
        ultimoPrivadoId = 0
        lifecycleScope.launch {
            try {
                val ms = DataProvider.conversacion(c.id, 0)
                ultimoPrivadoId = ms.maxOfOrNull { it.id } ?: 0
                adapterPrivado.reemplazar(ms.map { m ->
                    val mio = m.idEmisor == (Session.sesion()?.userId ?: -1)
                    ItemChat(
                        m.mensaje,
                        (if (mio) "Tú" else c.nombre) + " • " + Ui.hora(m.fecha),
                        if (mio) ItemChat.PROPIO else ItemChat.AJENO
                    )
                })
                bajarAlFinal(rvPrivado, adapterPrivado, modo == PRIVADO)
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "Error al abrir la conversación: ${e.message}")
            }
        }
    }

    private fun cerrarConversacion() {
        contacto = null
        vistaConversacion.visibility = View.GONE
        vistaContactos.visibility = View.VISIBLE
        subtitulo.text = "Mensajes privados"
        input.hint = "Selecciona un contacto"
        cargarContactos()
    }

    private fun adjuntarTicket() {
        lifecycleScope.launch {
            val tickets = try {
                DataProvider.tickets()
            } catch (e: Exception) {
                Ui.toast(this@ChatActivity, "No se pudieron cargar tus tickets")
                return@launch
            }
            if (tickets.isEmpty()) {
                Ui.toast(this@ChatActivity, "Todavía no tienes tickets")
                return@launch
            }
            val etiquetas = tickets.map { "${Ui.tkId(it.id)} · ${it.asunto}" }.toTypedArray()
            online.sistemahelpdesk.movil.ui.common.Dialogos.lista(
                this@ChatActivity, "Adjuntar ticket como contexto", etiquetas
            ) { i ->
                ticketAdjunto = tickets[i]
                subtitulo.text = "Asistente • contexto ${Ui.tkId(tickets[i].id)}"
            }
        }
    }

    // ---------------------------------------------------------------- sondeo
    private fun refrescar() {
        if (isFinishing || isDestroyed) return
        when {
            modo == GLOBAL -> lifecycleScope.launch {
                val nuevos = try {
                    DataProvider.chatGlobal(ultimoGlobalId)
                } catch (e: Exception) {
                    return@launch
                }.filter { it.id > ultimoGlobalId }
                if (nuevos.isEmpty()) return@launch
                ultimoGlobalId = maxOf(ultimoGlobalId, nuevos.maxOf { it.id })
                nuevos.forEach { adapterGlobal.agregar(aItem(it)) }
                bajarAlFinal(rvGlobal, adapterGlobal, true)
            }
            modo == PRIVADO && contacto != null -> lifecycleScope.launch {
                val c = contacto ?: return@launch
                val nuevos = try {
                    DataProvider.conversacion(c.id, ultimoPrivadoId)
                } catch (e: Exception) {
                    return@launch
                }.filter { it.id > ultimoPrivadoId }
                if (nuevos.isEmpty()) return@launch
                ultimoPrivadoId = maxOf(ultimoPrivadoId, nuevos.maxOf { it.id })
                nuevos.forEach { m ->
                    val mio = m.idEmisor == (Session.sesion()?.userId ?: -1)
                    adapterPrivado.agregar(
                        ItemChat(
                            m.mensaje,
                            (if (mio) "Tú" else c.nombre) + " • " + Ui.hora(m.fecha),
                            if (mio) ItemChat.PROPIO else ItemChat.AJENO
                        )
                    )
                }
                bajarAlFinal(rvPrivado, adapterPrivado, true)
            }
        }
    }

    companion object {
        const val EXTRA_MODO = "modo"
        const val EXTRA_PROMPT = "prompt"
        const val IA = "ia"
        const val GLOBAL = "global"
        const val PRIVADO = "privado"
        const val SOLO_GLOBAL = "solo_global"
    }
}
