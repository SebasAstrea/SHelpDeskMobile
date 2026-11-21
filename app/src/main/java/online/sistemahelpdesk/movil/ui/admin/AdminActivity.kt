package online.sistemahelpdesk.movil.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.ui.chat.ChatActivity
import online.sistemahelpdesk.movil.ui.common.BarraSuperior
import online.sistemahelpdesk.movil.ui.common.Navegacion

/** Port de `dashboard-admin.html`: 8 secciones de administración. */
class AdminActivity : AppCompatActivity() {

    private lateinit var tabRow: LinearLayout
    private lateinit var scrollSeccion: ScrollView
    private lateinit var contSeccion: LinearLayout
    private var actual: String = "resumen"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        if (!Navegacion.exigir(this, Roles.ADMINISTRADOR)) return

        setContentView(R.layout.activity_admin)
        BarraSuperior.instalar(this, "Administración HelpDesk", Session.sesion()?.nombre)

        tabRow = findViewById(R.id.tabRow)
        scrollSeccion = findViewById(R.id.scrollSeccion)
        contSeccion = findViewById(R.id.contSeccion)

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
            if (tv.tag == clave) {
                tv.background = getDrawable(R.drawable.bg_btn_primary)
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tv.background = null
                tv.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
        contSeccion.removeAllViews()
        SeccionesAdmin.render(this, contSeccion, clave)
        scrollSeccion.post { scrollSeccion.fullScroll(android.view.View.FOCUS_UP) }
    }

    fun refrescar() = mostrar(actual)

    companion object {
        private const val CLAVE = "seccion"

        val SECCIONES = listOf(
            "resumen" to "Resumen",
            "usuarios" to "Usuarios",
            "bd" to "BD",
            "respaldos" to "Respaldos",
            "logs" to "Logs",
            "n8n" to "N8N",
            "ia" to "IA",
            "seguridad" to "Seguridad"
        )
    }
}
