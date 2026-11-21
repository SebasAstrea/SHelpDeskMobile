package online.sistemahelpdesk.movil.ui.common

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import online.sistemahelpdesk.movil.core.Session

/**
 * Switch de tema día/noche compartido (theme-toggle.js): persiste en
 * SharedPreferences la clave `theme` y alterna el modo DayNight.
 */
object ThemeSwitch {

    fun instalar(activity: Activity, sw: SwitchCompat?) {
        sw ?: return
        sw.isChecked = Session.temaNoche
        sw.setOnCheckedChangeListener { _, activo ->
            if (Session.temaNoche != activo) {
                Session.temaNoche = activo
                AppCompatDelegate.setDefaultNightMode(
                    if (activo) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        }
        actualizarEtiqueta(sw)
    }

    fun actualizarEtiqueta(sw: SwitchCompat?) {
        sw ?: return
        sw.text = if (Session.temaNoche) "Modo Diurno" else "Modo Nocturno"
    }
}
