package online.sistemahelpdesk.movil.ui.admin

import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Respaldo
import online.sistemahelpdesk.movil.data.Roles
import online.sistemahelpdesk.movil.data.Sesion
import online.sistemahelpdesk.movil.data.TablaBD
import online.sistemahelpdesk.movil.data.Usuario
import online.sistemahelpdesk.movil.data.Workflow
import online.sistemahelpdesk.movil.ui.agente.AgenteActivity
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.Vistas
import online.sistemahelpdesk.movil.ui.coordinador.CoordinadorActivity

/** Secciones del panel de administración (renderizadas en código). */
object SeccionesAdmin {

    private data class Seccion(val titulo: String, val sub: String)

    private val SECCIONES = mapOf(
        "resumen" to Seccion(
            "Centro de Administración",
            "Potestad total sobre HelpDesk: usuarios, base de datos, respaldos, logs, n8n e IA."
        ),
        "usuarios" to Seccion(
            "Usuarios y Roles",
            "Crear, promover (incluido administrador temporal), desactivar y eliminar usuarios."
        ),
        "bd" to Seccion("Base de Datos", "Tablas y filas en vivo (pg_stat_user_tables)."),
        "respaldos" to Seccion(
            "Respaldos de la Base de Datos",
            "pg_dump -Fc en el volumen db_backups del contenedor helpdesk-db."
        ),
        "logs" to Seccion(
            "Logs de Contenedores",
            "Backend, base de datos, Streamlit, n8n y Ollama en vivo."
        ),
        "n8n" to Seccion("Workflows n8n", "Activa y desactiva las automatizaciones del servidor."),
        "ia" to Seccion(
            "Inteligencia Artificial (Ollama)",
            "Modelo activo del chat, descarga de modelos y parámetros de generación."
        ),
        "seguridad" to Seccion(
            "Seguridad · Doble factor (2FA)",
            "Añade un segundo factor TOTP a tu cuenta. Muy recomendado para administradores."
        )
    )

    fun render(act: AppCompatActivity, cont: LinearLayout, clave: String) {
        val s = SECCIONES[clave] ?: return
        cont.addView(Vistas.banner(act, s.titulo, s.sub))
        act.lifecycleScope.launch {
            when (clave) {
                "resumen" -> resumen(act, cont)
                "usuarios" -> usuarios(act, cont)
                "bd" -> bd(act, cont)
                "respaldos" -> respaldos(act, cont)
                "logs" -> logs(act, cont)
                "n8n" -> n8n(act, cont)
                "ia" -> ia(act, cont)
                "seguridad" -> seguridad(act, cont)
            }
        }
    }

    private fun error(cont: LinearLayout, e: Exception) {
        cont.addView(
            Vistas.texto(
                cont.context,
                "Error: ${e.message ?: "operación no disponible"}",
                13f,
                ContextCompat.getColor(cont.context, R.color.danger),
                negrita = true
            )
        )
    }

    private fun recargar(act: AppCompatActivity) = (act as? AdminActivity)?.refrescar()

    // ----------------------------------------------------------------- resumen
    private suspend fun resumen(act: AppCompatActivity, cont: LinearLayout) {
        val atajos = Vistas.fila(act)
        atajos.addView(Vistas.boton(act, "Tablero Coordinación") {
            act.startActivity(Intent(act, CoordinadorActivity::class.java))
        })
        atajos.addView(Vistas.boton(act, "Tablero Agente") {
            act.startActivity(Intent(act, AgenteActivity::class.java))
        })
        cont.addView(atajos)

        val est = try {
            DataProvider.estadisticas()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        val usuarios = try {
            DataProvider.usuarios()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }

        val kpis = listOf(
            Triple("Tickets Totales Mes", est.kpis.totalTicketsMes.toString(), R.color.kpi_blue),
            Triple(
                "Cumplimiento de SLA",
                est.kpis.cumplimientoSla?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "—",
                R.color.kpi_green
            ),
            Triple("Usuarios Totales", usuarios.size.toString(), R.color.kpi_yellow),
            Triple(
                "Usuarios Activos",
                usuarios.count { it.activo }.toString(),
                R.color.kpi_purple
            )
        )
        var filaKpi: LinearLayout? = null
        kpis.forEachIndexed { i, (titulo, valor, colorRes) ->
            if (i % 2 == 0) {
                filaKpi = Vistas.fila(act).also { cont.addView(it) }
            }
            val card = Vistas.tarjeta(act, padding = 12).apply {
                addView(View(act).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(3))
                    setBackgroundColor(ContextCompat.getColor(act, colorRes))
                })
                addView(Vistas.texto(act, titulo, 12f).apply { alpha = 0.8f })
                addView(Vistas.texto(act, valor, 22f, negrita = true))
            }
            card.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = Ui.dp(6) }
            filaKpi!!.addView(card)
        }

        val card = Vistas.tarjeta(act)
        card.addView(Vistas.texto(act, "Distribución de roles", 15f, negrita = true))
        card.addView(Vistas.cabeceraTabla(act, listOf(
            Vistas.Col("Rol", 1.6f),
            Vistas.Col("Total", 0.6f, negrita = true),
            Vistas.Col("Activos", 0.7f, negrita = true),
            Vistas.Col("Inactivos", 0.9f, negrita = true)
        )))
        val porRol = usuarios.groupBy { it.rol }
        if (porRol.isEmpty()) {
            card.addView(Vistas.texto(act, "Sin usuarios registrados", 13f))
        } else porRol.forEach { (rol, lista) ->
            card.addView(
                Vistas.filaTabla(act, listOf(
                    Vistas.Col(Roles.etiqueta(rol), 1.6f, color = Ui.rolColor(rol), negrita = true),
                    Vistas.Col(lista.size.toString(), 0.6f),
                    Vistas.Col(lista.count { it.activo }.toString(), 0.7f),
                    Vistas.Col(lista.count { !it.activo }.toString(), 0.9f)
                ))
            )
        }
        cont.addView(card)
    }

    // --------------------------------------------------------------- usuarios
    private suspend fun usuarios(act: AppCompatActivity, cont: LinearLayout) {
        val acciones = Vistas.fila(act)
        val form = Vistas.tarjeta(act).apply { visibility = View.GONE }
        val lista = Vistas.columna(act)

        acciones.addView(Vistas.boton(act, "Nuevo usuario") {
            form.visibility = if (form.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        acciones.addView(Vistas.boton(act, "Refrescar") { recargar(act) })
        cont.addView(acciones)

        // ---- formulario de alta
        form.addView(Vistas.texto(act, "Crear usuario", 15f, negrita = true))
        val nombre = campo(act, "Nombre completo", "Ana Pérez")
        val correo = campo(act, "Correo", "ana.perez@empresa.com").apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val area = campo(act, "Área", "TI, Producción...")
        val pass = campo(act, "Contraseña inicial (mín. 8)", "••••••••").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val rolesArr = act.resources.getStringArray(R.array.roles_usuario)
        val spRol = Vistas.spinner(act, rolesArr.toList())
        form.addView(etiqueta(act, "Nombre completo"))
        form.addView(nombre)
        form.addView(etiqueta(act, "Correo"))
        form.addView(correo)
        form.addView(etiqueta(act, "Área"))
        form.addView(area)
        form.addView(etiqueta(act, "Rol"))
        form.addView(spRol)
        form.addView(etiqueta(act, "Contraseña inicial (mín. 8)"))
        form.addView(pass)
        form.addView(Vistas.boton(act, "Crear") {
            val n = nombre.text.toString().trim()
            val c = correo.text.toString().trim()
            val p = pass.text.toString()
            if (n.isEmpty() || c.isEmpty() || p.isEmpty()) {
                Ui.toast(act, "Nombre, correo y contraseña son obligatorios")
                return@boton
            }
            act.lifecycleScope.launch {
                try {
                    DataProvider.crearUsuario(n, c, area.text.toString().trim(), rolesArr[spRol.selectedItemPosition], p)
                    Ui.toast(act, "Usuario $c creado con rol ${rolesArr[spRol.selectedItemPosition]}")
                    recargar(act)
                } catch (e: Exception) {
                    Ui.toast(act, "No se pudo crear: ${e.message}")
                }
            }
        })
        cont.addView(form)

        // ---- listado
        val datos = try {
            DataProvider.usuarios()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        val cab = Vistas.fila(act)
        cab.addView(
            Vistas.texto(act, "Usuarios registrados (${datos.size})", 15f, negrita = true)
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        )
        cont.addView(cab)

        if (datos.isEmpty()) {
            cont.addView(Vistas.texto(act, "No hay usuarios registrados", 13f))
            return
        }
        datos.forEach { u -> cont.addView(filaUsuario(act, u)) }
    }

    private fun filaUsuario(act: AppCompatActivity, u: Usuario): LinearLayout {
        val card = Vistas.item(act)
        val cab = Vistas.fila(act)
        cab.addView(
            Vistas.texto(act, u.nombre, 14f, negrita = true)
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        )
        cab.addView(
            Vistas.pill(
                act, Roles.etiqueta(u.rol),
                Ui.rolColor(u.rol)
            )
        )
        card.addView(cab)
        card.addView(Vistas.texto(act, u.email, 12f).apply { alpha = 0.85f })
        card.addView(Vistas.texto(act, "Área: ${u.area ?: "—"}", 12f).apply { alpha = 0.85f })
        if (!u.rolAnterior.isNullOrBlank()) {
            card.addView(
                Vistas.texto(
                    act,
                    "Admin temporal hasta ${u.adminTemporalHasta ?: "—"} (vuelve a ${u.rolAnterior})",
                    11f
                )
            )
        }
        card.addView(
            Vistas.pill(
                act, u.estado,
                ContextCompat.getColor(act, if (u.activo) R.color.estado_activo else R.color.estado_inactivo)
            )
        )

        val filaAcciones = Vistas.fila(act)
        filaAcciones.addView(Vistas.boton(act, "Rol") { cambiarRol(act, u) })
        filaAcciones.addView(
            Vistas.boton(act, if (u.activo) "Desactivar" else "Activar") { cambiarEstado(act, u) }
        )
        filaAcciones.addView(Vistas.boton(act, "🔑") { restablecer(act, u) })
        filaAcciones.addView(Vistas.boton(act, "🗑") { eliminar(act, u) })
        card.addView(filaAcciones)
        return card
    }

    private fun cambiarRol(act: AppCompatActivity, u: Usuario) {
        val rolesArr = act.resources.getStringArray(R.array.roles_usuario)
        val actual = rolesArr.indexOf(u.rol).coerceAtLeast(0)
        Dialogos.lista(act, "Cambiar rol — ${u.nombre}", rolesArr, seleccion = actual) { i ->
            val nuevo = rolesArr[i]
            if (nuevo == u.rol) return@lista
            if (nuevo == Roles.ADMINISTRADOR) {
                Dialogos.entrada(
                    act,
                    "Administrador temporal",
                    hint = "Horas (1-720) o vacío para permanente",
                    numerico = true,
                    maxLength = 3
                ) { horas ->
                    aplicarRol(act, u, nuevo, horas.toIntOrNull()?.takeIf { it in 1..720 })
                }
            } else {
                aplicarRol(act, u, nuevo, null)
            }
        }
    }

    private fun aplicarRol(act: AppCompatActivity, u: Usuario, rol: String, horas: Int?) {
        act.lifecycleScope.launch {
            try {
                DataProvider.cambiarRol(u.id, rol, horas)
                Ui.toast(
                    act,
                    if (horas != null) "${u.nombre} es administrador por $horas h (volverá a ${u.rol})"
                    else "Rol de ${u.nombre}: $rol"
                )
                recargar(act)
            } catch (e: Exception) {
                Ui.toast(act, "No se pudo cambiar el rol: ${e.message}")
            }
        }
    }

    private fun cambiarEstado(act: AppCompatActivity, u: Usuario) {
        val nuevo = if (u.activo) "inactivo" else "activo"
        Dialogos.confirmar(
            act,
            if (u.activo) "Desactivar usuario" else "Activar usuario",
            mensaje = "¿Marcar a ${u.nombre} (${u.email}) como $nuevo?",
            textoAceptar = "Sí, aplicar",
            peligroso = u.activo,
            onAceptar = {
                act.lifecycleScope.launch {
                    try {
                        DataProvider.cambiarEstado(u.id, nuevo)
                        Ui.toast(act, "${u.nombre} ahora está $nuevo")
                        recargar(act)
                    } catch (e: Exception) {
                        Ui.toast(act, "No se pudo actualizar: ${e.message}")
                    }
                }
            }
        )
    }

    private fun restablecer(act: AppCompatActivity, u: Usuario) {
        Dialogos.entrada(
            act,
            "Restablecer contraseña — ${u.nombre}",
            hint = "Nueva contraseña (mín. 8)",
            maxLength = 64
        ) { pass ->
            if (pass.length < 8) {
                Ui.toast(act, "La contraseña debe tener al menos 8 caracteres")
                return@entrada
            }
            act.lifecycleScope.launch {
                try {
                    DataProvider.restablecerPassword(u.id, pass)
                    Ui.toast(act, "Contraseña de ${u.nombre} restablecida")
                } catch (e: Exception) {
                    Ui.toast(act, "No se pudo restablecer: ${e.message}")
                }
            }
        }
    }

    private fun eliminar(act: AppCompatActivity, u: Usuario) {
        Dialogos.confirmar(
            act,
            "Eliminar usuario",
            mensaje = "¿Eliminar a ${u.nombre} (${u.email})? Esta acción borra el registro de la BD.",
            textoAceptar = "Eliminar",
            peligroso = true,
            onAceptar = {
                act.lifecycleScope.launch {
                    try {
                        DataProvider.eliminarUsuario(u.id)
                        Ui.toast(act, "${u.nombre} eliminado")
                        recargar(act)
                    } catch (e: Exception) {
                        Ui.toast(act, "No se pudo eliminar: ${e.message}")
                    }
                }
            }
        )
    }

    // --------------------------------------------------------------------- BD
    private suspend fun bd(act: AppCompatActivity, cont: LinearLayout) {
        cont.addView(Vistas.boton(act, "Refrescar") { recargar(act) })
        val tablas = try {
            DataProvider.tablasBD()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        val card = Vistas.tarjeta(act)
        card.addView(Vistas.texto(act, "Tablas (PostgreSQL + pgvector)", 15f, negrita = true))
        card.addView(
            Vistas.cabeceraTabla(
                act,
                listOf(Vistas.Col("Tabla", 2f), Vistas.Col("Filas (estimado)", 1f, negrita = true))
            )
        )
        if (tablas.isEmpty()) {
            card.addView(Vistas.texto(act, "No se encontraron tablas", 13f))
        } else tablas.forEach { t: TablaBD ->
            card.addView(
                Vistas.filaTabla(
                    act,
                    listOf(
                        Vistas.Col(t.tabla, 2f),
                        Vistas.Col(t.filas?.toString() ?: "—", 1f)
                    )
                )
            )
        }
        cont.addView(card)
    }

    // -------------------------------------------------------------- respaldos
    private suspend fun respaldos(act: AppCompatActivity, cont: LinearLayout) {
        cont.addView(Vistas.boton(act, "Crear respaldo ahora") {
            Ui.toast(act, "Ejecutando pg_dump...")
            act.lifecycleScope.launch {
                try {
                    DataProvider.crearRespaldo()
                    Ui.toast(act, "Respaldo creado correctamente")
                    recargar(act)
                } catch (e: Exception) {
                    Ui.toast(act, "No se pudo crear el respaldo: ${e.message}")
                }
            }
        })

        val datos = try {
            DataProvider.respaldos()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        cont.addView(Vistas.texto(act, "Respaldos disponibles (${datos.size})", 15f, negrita = true).apply {
            setPadding(0, Ui.dp(8), 0, Ui.dp(6))
        })
        if (datos.isEmpty()) {
            cont.addView(Vistas.texto(act, "No hay respaldos guardados", 13f))
            return
        }
        datos.forEach { r: Respaldo -> cont.addView(filaRespaldo(act, r)) }
    }

    private fun filaRespaldo(act: AppCompatActivity, r: Respaldo): LinearLayout {
        val card = Vistas.item(act)
        card.addView(Vistas.texto(act, r.nombre, 13f, negrita = true))
        card.addView(
            Vistas.texto(act, "${Ui.mb(r.bytes)} • ${Ui.fechaHora(r.fecha)}", 12f).apply { alpha = 0.8f }
        )
        val fila = Vistas.fila(act)
        fila.addView(Vistas.boton(act, "Restaurar") {
            Dialogos.confirmar(
                act,
                "Restaurar base de datos",
                mensaje = "¿Restaurar ${r.nombre}? Se ejecuta pg_restore --clean.",
                nota = "LA BD ACTUAL SERÁ REEMPLAZADA",
                textoAceptar = "Restaurar ahora",
                peligroso = true,
                onAceptar = {
                    act.lifecycleScope.launch {
                        Ui.toast(act, "Restaurando (puede tardar un minuto)...")
                        try {
                            DataProvider.restaurarRespaldo(r.nombre)
                            Ui.toast(act, "Base de datos restaurada")
                        } catch (e: Exception) {
                            Ui.toast(act, "No se pudo restaurar: ${e.message}")
                        }
                    }
                }
            )
        })
        fila.addView(Vistas.boton(act, "🗑") {
            Dialogos.confirmar(
                act,
                "Eliminar respaldo",
                mensaje = "¿Eliminar ${r.nombre}?",
                textoAceptar = "Eliminar",
                peligroso = true,
                onAceptar = {
                    act.lifecycleScope.launch {
                        try {
                            DataProvider.eliminarRespaldo(r.nombre)
                            Ui.toast(act, "Respaldo eliminado")
                            recargar(act)
                        } catch (e: Exception) {
                            Ui.toast(act, "No se pudo eliminar: ${e.message}")
                        }
                    }
                }
            )
        })
        card.addView(fila)
        return card
    }

    // ------------------------------------------------------------------ logs
    private suspend fun logs(act: AppCompatActivity, cont: LinearLayout) {
        val contenedores = act.resources.getStringArray(R.array.contenedores).toList()
        val lineas = act.resources.getStringArray(R.array.log_lineas).toList()
        val spCont = Vistas.spinner(act, contenedores)
        val spLin = Vistas.spinner(act, lineas)
        cont.addView(Vistas.fila(act).also { it.addView(spCont); it.addView(spLin) })

        val titulo = Vistas.texto(act, "Selecciona un contenedor", 14f, negrita = true).apply {
            setPadding(0, Ui.dp(10), 0, Ui.dp(4))
        }
        val salida = Vistas.texto(act, "Los logs aparecerán aquí...", 11f, 0xFFD8F0FF.toInt()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input_soft)
            setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10))
            setTextIsSelectable(true)
        }
        cont.addView(Vistas.boton(act, "Ver logs") {
            val c = contenedores[spCont.selectedItemPosition]
            val n = lineas[spLin.selectedItemPosition]
            titulo.text = "Logs: $c (últimas $n líneas)"
            salida.text = "Cargando..."
            act.lifecycleScope.launch {
                try {
                    salida.text = DataProvider.logs(c, n.toInt())
                } catch (e: Exception) {
                    salida.text = "Error: ${e.message}"
                }
            }
        })
        cont.addView(titulo)
        cont.addView(salida)
    }

    // ------------------------------------------------------------------- n8n
    private suspend fun n8n(act: AppCompatActivity, cont: LinearLayout) {
        cont.addView(Vistas.boton(act, "Refrescar") { recargar(act) })
        val datos = try {
            DataProvider.workflows()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        if (datos.isEmpty()) {
            cont.addView(Vistas.texto(act, "No hay workflows en esta instancia", 13f))
            return
        }
        datos.forEach { w: Workflow ->
            val card = Vistas.item(act)
            card.addView(Vistas.texto(act, w.nombre, 14f, negrita = true))
            val estadoTxt = if (w.activo) "Activo" else "Inactivo"
            card.addView(
                Vistas.fila(act).also { fila ->
                    fila.addView(Vistas.texto(act, "ID: ${w.id} · $estadoTxt", 12f).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    fila.addView(
                        Vistas.boton(act, if (w.activo) "Desactivar" else "Activar") {
                            act.lifecycleScope.launch {
                                try {
                                    DataProvider.toggleWorkflow(w.id, !w.activo)
                                    Ui.toast(act, "Workflow '${w.nombre}' ${if (w.activo) "desactivado" else "activado"}")
                                    recargar(act)
                                } catch (e: Exception) {
                                    Ui.toast(act, "No se pudo actualizar: ${e.message}")
                                }
                            }
                        }
                    )
                }
            )
            cont.addView(card)
        }
    }

    // -------------------------------------------------------------------- IA
    private suspend fun ia(act: AppCompatActivity, cont: LinearLayout) {
        val (modelos, cfg) = try {
            DataProvider.ia()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }

        val cardModelos = Vistas.tarjeta(act)
        cardModelos.addView(Vistas.texto(act, "Modelos instalados", 15f, negrita = true))
        if (modelos.isEmpty()) {
            cardModelos.addView(Vistas.texto(act, "No hay modelos descargados", 13f))
        } else modelos.forEach { m ->
            val fila = Vistas.fila(act)
            fila.addView(
                Vistas.texto(
                    act,
                    "${m.nombre}${if (m.activo) " (activo)" else ""} • ${String.format(java.util.Locale.US, "%.1f GB", m.tamano / 1_000_000_000.0)}",
                    13f,
                    negrita = m.activo
                ).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            )
            if (!m.activo) {
                fila.addView(Vistas.boton(act, "Usar") {
                    act.lifecycleScope.launch {
                        try {
                            DataProvider.usarModelo(m.nombre)
                            Ui.toast(act, "Modelo activo del chat: ${m.nombre}")
                            recargar(act)
                        } catch (e: Exception) {
                            Ui.toast(act, "No se pudo activar: ${e.message}")
                        }
                    }
                })
            }
            fila.addView(Vistas.boton(act, "Probar") {
                act.lifecycleScope.launch {
                    try {
                        val r = DataProvider.probarModelo(m.nombre)
                        Dialogos.informacion(act, "Respuesta de ${m.nombre}", r, scroll = true)
                    } catch (e: Exception) {
                        Ui.toast(act, "No se pudo probar: ${e.message}")
                    }
                }
            })
            cardModelos.addView(fila)
        }
        cont.addView(cardModelos)

        val pull = campo(act, "Descargar modelo nuevo (ollama pull)", "ej: qwen2.5:3b, mistral:7b")
        cont.addView(pull)
        cont.addView(Vistas.boton(act, "Descargar") {
            val nombre = pull.text.toString().trim()
            if (nombre.isEmpty()) {
                Ui.toast(act, "Escribe el nombre del modelo")
                return@boton
            }
            Ui.toast(act, "Descargando...")
            act.lifecycleScope.launch {
                try {
                    DataProvider.pullModelo(nombre)
                    Ui.toast(act, "Solicitud enviada: $nombre")
                    recargar(act)
                } catch (e: Exception) {
                    Ui.toast(act, "No se pudo descargar: ${e.message}")
                }
            }
        })

        val cardParams = Vistas.tarjeta(act)
        cardParams.addView(Vistas.texto(act, "Parámetros de generación (chat)", 15f, negrita = true))
        val temp = campo(act, "Temperatura (0-2)", cfg.temperatura.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val tokens = campo(act, "Máximo de tokens", cfg.numPredict.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val topP = campo(act, "Top-P (0-1)", cfg.topP.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        cardParams.addView(temp)
        cardParams.addView(tokens)
        cardParams.addView(topP)
        cardParams.addView(
            Vistas.texto(
                act,
                "Se guardan en configuracion_ia y el backend los envía a n8n/Ollama en cada mensaje del chat.",
                11f
            ).apply { alpha = 0.8f }
        )
        cardParams.addView(Vistas.boton(act, "Guardar parámetros") {
            val t = temp.text.toString().toDoubleOrNull()
            val n = tokens.text.toString().toIntOrNull()
            val p = topP.text.toString().toDoubleOrNull()
            if (t == null || n == null || p == null) {
                Ui.toast(act, "Revisa los valores numéricos")
                return@boton
            }
            act.lifecycleScope.launch {
                try {
                    DataProvider.guardarParamsIa(t, n, p)
                    Ui.toast(act, "Parámetros guardados. Se aplican en el próximo mensaje del chat.")
                } catch (e: Exception) {
                    Ui.toast(act, "No se pudieron guardar: ${e.message}")
                }
            }
        })
        cont.addView(cardParams)
    }

    // ------------------------------------------------------------- seguridad
    private suspend fun seguridad(act: AppCompatActivity, cont: LinearLayout) {
        val sesion: Sesion? = Session.sesion()
        val activo = try {
            DataProvider.authMe().mfaEnabled
        } catch (e: Exception) {
            sesion?.mfaEnabled ?: false
        }

        val card = Vistas.tarjeta(act)
        card.addView(Vistas.texto(act, "Estado de tu 2FA", 15f, negrita = true))
        card.addView(
            Vistas.pill(
                act,
                if (activo) "2FA activado" else "2FA desactivado",
                ContextCompat.getColor(act, if (activo) R.color.estado_activo else R.color.estado_inactivo)
            )
        )
        if (activo) {
            card.addView(
                Vistas.texto(
                    act,
                    "Introduce un código válido de tu app para desactivar el 2FA:",
                    13f
                ).apply { setPadding(0, Ui.dp(8), 0, 0) }
            )
            card.addView(Vistas.boton(act, "Desactivar 2FA") {
                Dialogos.entrada(act, "Desactivar 2FA", hint = "Código de 6 dígitos", numerico = true, maxLength = 6) { codigo ->
                    if (!Regex("^\\d{6}$").matches(codigo)) {
                        Ui.toast(act, "Introduce el código de 6 dígitos")
                        return@entrada
                    }
                    act.lifecycleScope.launch {
                        try {
                            DataProvider.mfaDisable(codigo)
                            Ui.toast(act, "2FA desactivado")
                            recargar(act)
                        } catch (e: Exception) {
                            Ui.toast(act, "No se pudo desactivar: ${e.message}")
                        }
                    }
                }
            })
        } else {
            card.addView(Vistas.boton(act, "Activar 2FA") {
                act.lifecycleScope.launch {
                    try {
                        val (secret, _, _) = DataProvider.mfaSetup()
                        Dialogos.confirmar(
                            act,
                            "Configurar 2FA",
                            mensaje = "1. Escanea el QR desde el panel web o introduce esta clave manualmente " +
                                "en Google Authenticator / Authy / Aegis:",
                            nota = secret,
                            textoAceptar = "Introducir código",
                            onAceptar = {
                                Dialogos.entrada(act, "Código de la app", hint = "000000", numerico = true, maxLength = 6) { codigo ->
                                    if (!Regex("^\\d{6}$").matches(codigo)) {
                                        Ui.toast(act, "Introduce el código de 6 dígitos")
                                        return@entrada
                                    }
                                    act.lifecycleScope.launch {
                                        try {
                                            DataProvider.mfaEnable(codigo)
                                            Ui.toast(act, "2FA activado correctamente")
                                            recargar(act)
                                        } catch (e: Exception) {
                                            Ui.toast(act, "No se pudo activar: ${e.message}")
                                        }
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Ui.toast(act, "No se pudo iniciar la configuración: ${e.message}")
                    }
                }
            })
        }
        cont.addView(card)
    }

    // -----------------------------------------------------------------extras
    private fun etiqueta(act: AppCompatActivity, texto: String): TextView =
        Vistas.texto(act, texto, 12f, negrita = true).apply {
            setPadding(0, Ui.dp(8), 0, Ui.dp(2))
        }

    private fun campo(act: AppCompatActivity, hint: String, valor: String): EditText =
        EditText(act).apply {
            setText(valor)
            this.hint = hint
            setTextColor(Vistas.colorTexto(act))
            setHintTextColor(ContextCompat.getColor(act, R.color.text_placeholder))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8))
        }
}
