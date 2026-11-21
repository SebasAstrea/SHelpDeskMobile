package online.sistemahelpdesk.movil.ui.common

import android.content.Intent
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.ui.admin.AdminActivity
import online.sistemahelpdesk.movil.ui.agente.AgenteActivity
import online.sistemahelpdesk.movil.ui.coordinador.CoordinadorActivity
import online.sistemahelpdesk.movil.ui.login.LoginActivity
import online.sistemahelpdesk.movil.ui.solicitante.SolicitanteActivity

/** Navegación por rol (equivalente a las redirecciones de los HTML). */
object Navegacion {

    fun destinoPorRol(rol: String?): Class<*> = when (rol) {
        Roles.SOLICITANTE -> SolicitanteActivity::class.java
        Roles.AGENTE -> AgenteActivity::class.java
        Roles.COORDINADOR -> CoordinadorActivity::class.java
        Roles.ADMINISTRADOR -> AdminActivity::class.java
        else -> LoginActivity::class.java
    }

    fun irADashboard(activity: android.app.Activity, rol: String?) {
        activity.startActivity(Intent(activity, destinoPorRol(rol)))
        activity.finish()
    }

    /**
     * Guard de rol (equivalente al gate de `/auth/me` de cada dashboard HTML):
     * devuelve `false` y redirige si no hay sesión o el rol no está permitido.
     */
    fun exigir(activity: android.app.Activity, vararg roles: String): Boolean {
        val s = Session.sesion()
        if (s != null && s.rol in roles) return true
        irADashboard(activity, s?.rol)
        return false
    }

    fun salir(activity: android.app.Activity) {
        Session.clear(activity)
        val i = Intent(activity, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(i)
        activity.finish()
    }
}
