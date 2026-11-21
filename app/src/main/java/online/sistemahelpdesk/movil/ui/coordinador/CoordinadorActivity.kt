package online.sistemahelpdesk.movil.ui.coordinador

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.ui.common.BarraSuperior
import online.sistemahelpdesk.movil.ui.common.Navegacion
import online.sistemahelpdesk.movil.ui.kanban.KanbanFragment
import online.sistemahelpdesk.movil.ui.chat.ChatActivity
import android.content.Intent

/**
 * Port de `dashboard-coordinador.html`: 9 secciones accesibles desde pestañas,
 * con el kanban reutilizando el mismo fragmento que el panel de agente.
 */
class CoordinadorActivity : AppCompatActivity() {

    private lateinit var tabRow: LinearLayout
    private lateinit var kanbanHolder: FrameLayout
    private lateinit var scrollSeccion: ScrollView
    private lateinit var contSeccion: LinearLayout
    private var actual: String = "kanban"
    private var primeraKanban = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        if (!Navegacion.exigir(this, Roles.COORDINADOR, Roles.ADMINISTRADOR)) return

        setContentView(R.layout.activity_coordinador)
        BarraSuperior.instalar(this, "Coordinación HelpDesk", Session.sesion()?.nombre)

        tabRow = findViewById(R.id.tabRow)
        kanbanHolder = findViewById(R.id.kanbanHolder)
        scrollSeccion = findViewById(R.id.scrollSeccion)
        contSeccion = findViewById(R.id.contSeccion)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.kanbanHolder, KanbanFragment())
                .commit()
        }

        findViewById<TextView>(R.id.chatFab).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        SECCIONES.forEach { (clave, etiqueta) ->
            val tv = TextView(this).apply {
                text = etiqueta
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(12), Ui.dp(7))
                setOnClickListener { mostrar(clave) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = Ui.dp(6)
            tv.layoutParams = lp
            tv.tag = clave
            tabRow.addView(tv)
        }

        mostrar(savedInstanceState?.getString(CLAVE) ?: actual)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CLAVE, actual)
    }

    private fun mostrar(clave: String) {
        actual = clave
        (0 until tabRow.childCount).forEach { i ->
            val tv = tabRow.getChildAt(i) as TextView
            val activo = tv.tag == clave
            if (activo) {
                tv.background = getDrawable(R.drawable.bg_btn_primary)
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.background = null
                tv.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }

        val esKanban = clave == "kanban"
        kanbanHolder.visibility = if (esKanban) View.VISIBLE else View.GONE
        scrollSeccion.visibility = if (esKanban) View.GONE else View.VISIBLE
        if (esKanban) {
            if (primeraKanban) {
                primeraKanban = false
            } else {
                (supportFragmentManager.findFragmentById(R.id.kanbanHolder) as? KanbanFragment)?.cargar()
            }
        } else {
            contSeccion.removeAllViews()
            SeccionesCoordinador.render(this, contSeccion, clave) { otra -> mostrar(otra) }
            scrollSeccion.post { scrollSeccion.fullScroll(View.FOCUS_UP) }
        }
    }

    /** Recarga la sección visible (usada por los botones "Actualizar"). */
    fun refrescar() = mostrar(actual)

    companion object {
        private const val CLAVE = "seccion"

        val SECCIONES = listOf(
            "kanban" to "Tablero",
            "estadisticas" to "Estadísticas",
            "reportes" to "Reportes",
            "asignar" to "Asignación",
            "supervisar" to "Supervisión",
            "chat-ia" to "Chat IA",
            "rag" to "RAG",
            "mi-perfil" to "Mi Perfil",
            "sla" to "SLA"
        )
    }
}
