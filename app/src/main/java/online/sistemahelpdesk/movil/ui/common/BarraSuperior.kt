package online.sistemahelpdesk.movil.ui.common

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import online.sistemahelpdesk.movil.R

/**
 * Configura la cabecera compartida (`view_topbar.xml`): título, switch de tema,
 * botón de salida y (opcional) botón "Volver a Admin".
 */
object BarraSuperior {

    fun instalar(
        activity: Activity,
        titulo: String,
        subtitulo: String? = null,
        onSecundario: (() -> Unit)? = null
    ) {
        val title = activity.findViewById<TextView>(R.id.topTitle)
        title?.text = titulo
        val subtitle = activity.findViewById<TextView>(R.id.topSubtitle)
        if (subtitle != null) {
            if (subtitulo.isNullOrBlank()) subtitle.visibility = View.GONE
            else {
                subtitle.text = subtitulo
                subtitle.visibility = View.VISIBLE
            }
        }
        ThemeSwitch.instalar(activity, activity.findViewById<SwitchCompat>(R.id.topTheme))
        ThemeSwitch.actualizarEtiqueta(activity.findViewById(R.id.topTheme))

        activity.findViewById<TextView>(R.id.topLogout)?.setOnClickListener {
            Dialogos.confirmar(
                activity,
                "Cerrar sesión",
                mensaje = "¿Deseas salir de tu cuenta?",
                textoAceptar = "Salir",
                peligroso = true,
                onAceptar = { Navegacion.salir(activity) }
            )
        }

        val secundario = activity.findViewById<TextView>(R.id.topSecondary)
        if (secundario != null) {
            if (onSecundario == null) {
                secundario.visibility = View.GONE
            } else {
                secundario.visibility = View.VISIBLE
                secundario.setOnClickListener { onSecundario() }
            }
        }
    }
}
