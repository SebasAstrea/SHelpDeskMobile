package online.sistemahelpdesk.movil.ui.agente

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.ui.chat.ChatActivity
import online.sistemahelpdesk.movil.ui.common.BarraSuperior
import online.sistemahelpdesk.movil.ui.common.Navegacion
import online.sistemahelpdesk.movil.ui.kanban.KanbanFragment

/** Port de `dashboard-agente.html`: kanban de tickets asignados + chat. */
class AgenteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        if (!Navegacion.exigir(this, Roles.AGENTE)) return

        setContentView(R.layout.activity_agente)
        BarraSuperior.instalar(this, "Tablero Kanban IT", Session.sesion()?.nombre)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.kanbanHolder, KanbanFragment())
                .commit()
        }

        findViewById<TextView>(R.id.chatFab).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        (supportFragmentManager.findFragmentById(R.id.kanbanHolder) as? KanbanFragment)?.cargar()
    }
}
