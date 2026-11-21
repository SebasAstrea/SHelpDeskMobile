package online.sistemahelpdesk.movil.ui.ticket

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.ApiClient
import online.sistemahelpdesk.movil.core.ApiException
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.Navegacion
import online.sistemahelpdesk.movil.ui.common.ThemeSwitch

private data class AdjuntoSel(val uri: Uri, val nombre: String, val bytes: Long, val mime: String)

/** Port de `formulario-ticket.html` + `js/formulario-ticket.js`. */
class FormularioTicketActivity : AppCompatActivity() {

    private lateinit var asunto: TextView
    private lateinit var descripcion: TextView
    private lateinit var categoria: Spinner
    private lateinit var prioridad: Spinner
    private lateinit var adjuntosContainer: LinearLayout
    private lateinit var error: TextView
    private lateinit var exito: TextView
    private lateinit var loading: LinearLayout
    private lateinit var aiIndicator: LinearLayout
    private lateinit var btnEnviar: TextView
    private lateinit var btnAdjuntar: TextView

    private val adjuntos = mutableListOf<AdjuntoSel>()
    private var rolActual: String = Roles.SOLICITANTE

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { agregarAdjunto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario_ticket)
        Session.init(this)

        asunto = findViewById(R.id.asunto)
        descripcion = findViewById(R.id.descripcion)
        categoria = findViewById(R.id.categoria)
        prioridad = findViewById(R.id.prioridad)
        adjuntosContainer = findViewById(R.id.adjuntosContainer)
        error = findViewById(R.id.errorMessage)
        exito = findViewById(R.id.successMessage)
        loading = findViewById(R.id.loadingMessage)
        aiIndicator = findViewById(R.id.aiIndicator)
        btnEnviar = findViewById(R.id.btnEnviar)
        btnAdjuntar = findViewById(R.id.btnAdjuntar)

        ThemeSwitch.instalar(this, findViewById<SwitchCompat>(R.id.themeCheck))

        categoria.adapter = ArrayAdapter.createFromResource(
            this, R.array.categorias, android.R.layout.simple_spinner_dropdown_item
        )
        prioridad.adapter = ArrayAdapter.createFromResource(
            this, R.array.prioridades, android.R.layout.simple_spinner_dropdown_item
        )

        btnAdjuntar.setOnClickListener { picker.launch(arrayOf("image/png", "image/jpeg")) }
        btnEnviar.setOnClickListener { confirmarEnvio() }
        findViewById<TextView>(R.id.btnVolver).setOnClickListener { finish() }

        findViewById<TextView>(R.id.badge1).setOnClickListener {
            Dialogos.informacion(this, "Nuevo Ticket", "Describe el incidente con el mayor detalle posible: el asunto, la categoría y la prioridad aceleran la asignación.")
        }
        findViewById<TextView>(R.id.badge2).setOnClickListener {
            Dialogos.informacion(this, "Datos Protegidos", "Las capturas se almacenan cifradas y solo las ve el agente asignado a tu ticket.")
        }
        findViewById<TextView>(R.id.badge3).setOnClickListener {
            Dialogos.informacion(this, "Respuesta Rápida", "La IA local clasifica el ticket y lo envía al área correcta apenas lo envías.")
        }

        cargarUsuario()
    }

    private fun cargarUsuario() {
        val guardada = Session.sesion()
        findViewById<TextView>(R.id.userName).text = guardada?.nombre?.ifBlank { guardada.email } ?: "Cargando..."
        findViewById<TextView>(R.id.userArea).text = guardada?.area ?: ""
        lifecycleScope.launch {
            try {
                val me = DataProvider.authMe()
                rolActual = me.rol
                Session.guardarUsuario(me, true)
                findViewById<TextView>(R.id.userName).text = me.nombre.ifBlank { me.email }
                findViewById<TextView>(R.id.userArea).text = me.area ?: ""
                if (me.rol != Roles.SOLICITANTE) {
                    mostrarError("Este formulario es solo para solicitantes")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing && !isDestroyed) finish()
                    }, 2000)
                }
            } catch (e: Exception) {
                rolActual = guardada?.rol ?: Roles.SOLICITANTE
            }
        }
    }

    // -------------------------------------------------------------- adjuntos
    private fun agregarAdjunto(uri: Uri) {
        val (nombre, bytes) = nombreYTamano(uri)
        val mime = contentResolver.getType(uri) ?: ""
        when {
            adjuntos.size >= 5 ->
                acumularError("Solo puedes adjuntar hasta 5 imágenes por ticket.")
            !(mime == "image/png" || mime == "image/jpeg" ||
                nombre.endsWith(".png", true) || nombre.endsWith(".jpg", true) ||
                nombre.endsWith(".jpeg", true)) ->
                acumularError("«$nombre» no es PNG o JPG. Usa ese formato.")
            bytes > 20_000_000L ->
                acumularError("«$nombre» pesa ${Ui.mb(bytes)} y supera el máximo de 20 MB.")
            adjuntos.any { it.nombre == nombre && it.bytes == bytes } ->
                acumularError("«$nombre» ya está seleccionada.")
            else -> {
                adjuntos += AdjuntoSel(uri, nombre, bytes, mime.ifBlank { "image/jpeg" })
                pintarAdjuntos()
            }
        }
        btnAdjuntar.isEnabled = adjuntos.size < 5
        btnAdjuntar.alpha = if (adjuntos.size >= 5) 0.65f else 1f
    }

    private fun nombreYTamano(uri: Uri): Pair<String, Long> {
        var nombre = uri.lastPathSegment ?: "imagen.jpg"
        var tam = 0L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val iN = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val iT = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (iN >= 0) c.getString(iN)?.let { nombre = it }
                if (iT >= 0) tam = c.getLong(iT)
            }
        }
        return Pair(nombre, tam)
    }

    private fun pintarAdjuntos() {
        adjuntosContainer.removeAllViews()
        adjuntos.forEachIndexed { indice, adj ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6))
                setBackgroundResource(R.drawable.bg_input)
            }
            val mini = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(adj.uri)
            }
            val textos = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(Ui.dp(8), 0, Ui.dp(8), 0)
            }
            textos.addView(TextView(this).apply {
                text = adj.nombre
                textSize = 13f
                setTextColor(getColor(R.color.text_dark))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            textos.addView(TextView(this).apply {
                text = Ui.tamano(adj.bytes)
                textSize = 11f
                setTextColor(getColor(R.color.text_soft))
            })
            val quitar = TextView(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(getColor(R.color.danger))
                setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(4), Ui.dp(4))
                setOnClickListener {
                    adjuntos.removeAt(indice)
                    pintarAdjuntos()
                    btnAdjuntar.isEnabled = adjuntos.size < 5
                    btnAdjuntar.alpha = if (adjuntos.size >= 5) 0.65f else 1f
                }
            }
            fila.addView(mini)
            fila.addView(textos)
            fila.addView(quitar)
            adjuntosContainer.addView(fila)
        }
    }

    // ----------------------------------------------------------------- envío
    private fun confirmarEnvio() {
        ocultarMensajes()
        val textoAsunto = asunto.text.toString().trim()
        val de = descripcion.text.toString().trim()
        val catPos = categoria.selectedItemPosition
        val priPos = prioridad.selectedItemPosition
        if (textoAsunto.isEmpty() || de.isEmpty() || catPos == 0 || priPos == 0) {
            mostrarError("Por favor completa todos los campos")
            return
        }
        val cats = resources.getStringArray(R.array.categorias)
        val prios = resources.getStringArray(R.array.prioridades)
        val mensaje = "¿Estás seguro de que deseas enviar este ticket?\n\n" +
            "Asunto: $textoAsunto\n" +
            "Categoría: ${cats[catPos]}\n" +
            "Prioridad: ${prios[priPos]}" +
            if (adjuntos.isNotEmpty()) "\nImágenes adjuntas: ${adjuntos.size} (${Ui.tamano(adjuntos.sumOf { it.bytes })})" else ""

        Dialogos.confirmar(
            this,
            "Confirmar Envío",
            mensaje = mensaje,
            textoAceptar = "Sí, Enviar",
            onAceptar = { enviar(catPos, priPos) }
        )
    }

    private fun enviar(catPos: Int, priPos: Int) {
        val textoAsunto = asunto.text.toString().trim()
        val de = descripcion.text.toString().trim()
        btnEnviar.isEnabled = false
        loading.visibility = View.VISIBLE
        aiIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            var fallos = 0
            try {
                val id = DataProvider.crearTicket(textoAsunto, de, catPos, priPos)
                if (adjuntos.isNotEmpty()) {
                    var i = 0
                    adjuntos.forEach { adj ->
                        i++
                        loading.findViewWithTag<TextView>("progreso")?.text = " Subiendo imagen $i de ${adjuntos.size}..."
                        val bytes = contentResolver.openInputStream(adj.uri)?.use { it.readBytes() }
                        val ok = bytes?.let {
                            try {
                                DataProvider.subirAdjunto(id, adj.nombre, it, adj.mime)
                            } catch (e: Exception) {
                                false
                            }
                        } ?: false
                        if (!ok) fallos++
                    }
                }
                val tk = Ui.tkId(id)
                val mensaje = when {
                    adjuntos.isEmpty() -> "$tk creado con éxito. ¡Ticket creado exitosamente!"
                    fallos == 0 -> "$tk creado con éxito. Tus imágenes fueron adjuntadas. El análisis de IA se ejecutará en segundo plano."
                    else -> "$tk creado, pero $fallos imagen(es) no pudieron subirse. Puedes adjuntarlas desde tu ticket."
                }
                exito.text = mensaje
                exito.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        Navegacion.irADashboard(this@FormularioTicketActivity, Roles.SOLICITANTE)
                    }
                }, 2500)
            } catch (e: ApiException) {
                if (e.code == 401) Navegacion.salir(this@FormularioTicketActivity)
                else mostrarError(e.detalle)
            } catch (e: Exception) {
                mostrarError(e.message ?: "Error al crear el ticket")
            } finally {
                btnEnviar.isEnabled = true
                loading.visibility = View.GONE
                aiIndicator.visibility = View.GONE
            }
        }
    }

    private fun acumularError(texto: String) {
        val actual = error.text?.toString().orEmpty()
        mostrarError(if (actual.isBlank()) texto else "$actual $texto")
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) error.visibility = View.GONE
        }, 5000)
    }

    private fun mostrarError(texto: String) {
        error.text = texto
        error.visibility = View.VISIBLE
        error.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun ocultarMensajes() {
        error.visibility = View.GONE
        exito.visibility = View.GONE
    }
}
