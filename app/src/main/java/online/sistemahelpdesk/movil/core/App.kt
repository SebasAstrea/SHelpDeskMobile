package online.sistemahelpdesk.movil.core

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Aplica el tema guardado (equivalente a body.night-mode + localStorage['theme']). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instancia = this
        Session.init(this)
        Theme.aplicar()
    }

    companion object {
        var instancia: App? = null
            private set
        val contexto: android.content.Context? get() = instancia
    }
}

object Theme {
    fun aplicar() {
        AppCompatDelegate.setDefaultNightMode(
            if (Session.temaNoche) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun alternar(activo: Boolean) {
        Session.temaNoche = activo
        aplicar()
    }

    val esNoche: Boolean get() = Session.temaNoche
}
