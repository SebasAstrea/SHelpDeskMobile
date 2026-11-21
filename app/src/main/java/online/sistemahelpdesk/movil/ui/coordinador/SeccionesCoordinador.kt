package online.sistemahelpdesk.movil.ui.coordinador

import android.content.Intent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.EficienciaAgente
import online.sistemahelpdesk.movil.data.Sla
import online.sistemahelpdesk.movil.data.Stat
import online.sistemahelpdesk.movil.data.Ticket
import online.sistemahelpdesk.movil.data.Usuario
import online.sistemahelpdesk.movil.ui.chat.ChatActivity
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.Vistas
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Secciones no kanban del panel de coordinación (renderizadas en código). */
object SeccionesCoordinador {

    private data class Seccion(val titulo: String, val sub: String)

    private val SECCIONES = mapOf(
        "estadisticas" to Seccion(
            "Dashboard de Analítica & Estadísticas TI",
            "Visualización avanzada de métricas, rendimiento de agentes y tendencias."
        ),
        "reportes" to Seccion(
            "Generación & Exportación de Reportes",
            "Filtra incidentes, analiza tiempos de atención y genera reportes ejecutivos en CSV."
        ),
        "asignar" to Seccion(
            "Asignación y Balanceo de Carga de Tickets",
            "Monitorea la carga activa de cada agente y asigna tickets manualmente o con recomendación IA."
        ),
        "supervisar" to Seccion(
            "Supervisión & Control de Permisos de Agentes",
            "Administra niveles de acceso y permisos de supervisión del equipo de soporte."
        ),
        "chat-ia" to Seccion(
            "Asistente de IA para Coordinadores",
            "Consulta análisis automáticos de métricas, distribución de agentes y cuellos de botella."
        ),
        "rag" to Seccion(
            "Búsqueda Semántica RAG (Retrieval-Augmented Generation)",
            "Consulta soluciones históricas con embeddings vectoriales (pgvector)."
        ),
        "mi-perfil" to Seccion("Perfil de Coordinación", "Consulta tus credenciales y tu rol."),
        "sla" to Seccion(
            "Configuración de Service Level Agreements (SLAs)",
            "Define tiempos límite de primera respuesta y resolución según la criticidad."
        )
    )

    fun render(act: AppCompatActivity, cont: LinearLayout, clave: String, irA: (String) -> Unit) {
        val s = SECCIONES[clave] ?: return
        cont.addView(Vistas.banner(act, s.titulo, s.sub))
        act.lifecycleScope.launch {
            when (clave) {
                "estadisticas" -> estadisticas(act, cont)
                "reportes" -> reportes(act, cont)
                "asignar" -> asignar(act, cont)
                "supervisar" -> supervisar(act, cont)
                "chat-ia" -> chatIa(act, cont, irA)
                "rag" -> rag(act, cont)
                "mi-perfil" -> perfil(act, cont)
                "sla" -> sla(act, cont)
            }
        }
    }

    private fun error(cont: LinearLayout, e: Exception) {
        cont.addView(
            Vistas.texto(
                cont.context,
                "Error: ${e.message ?: "no se pudo cargar la información"}",
                13f,
                ContextCompat.getColor(cont.context, R.color.danger),
                negrita = true
            )
        )
    }

    // ------------------------------------------------------------ estadísticas
    private suspend fun estadisticas(act: AppCompatActivity, cont: LinearLayout) {
        val e = try {
            DataProvider.estadisticas()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        val k = e.kpis
        val kpis = listOf(
            Triple("Tickets Totales Mes", k.totalTicketsMes.toString(), "${k.ticketsActivos} activos ahora"),
            Triple(
                "Cumplimiento de SLA",
                k.cumplimientoSla?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—",
                "Resueltos dentro del plazo"
            ),
            Triple("Tiempo Medio Solución", "${k.tiempoMedioSolucion} min", "Tickets resueltos"),
            Triple("Agentes Activos", "${k.agentesActivos} / ${k.agentesTotales}", "Personal de soporte")
        )
        val colores = listOf(R.color.kpi_blue, R.color.kpi_green, R.color.kpi_yellow, R.color.kpi_purple)
        var fila: LinearLayout? = null
        kpis.forEachIndexed { i, (titulo, valor, sub) ->
            if (i % 2 == 0) {
                fila = Vistas.fila(act).also { cont.addView(it) }
            }
            val tarjeta = kpi(act, titulo, valor, sub, ContextCompat.getColor(act, colores[i]))
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = Ui.dp(6)
            tarjeta.layoutParams = lp
            fila!!.addView(tarjeta)
        }

        val cats = Vistas.tarjeta(act)
        cats.addView(Vistas.texto(act, "Volumen por Categoría", 15f, negrita = true))
        cats.addView(Vistas.texto(act, "Últimos 30 días", 11f).apply { alpha = 0.75f })
        if (e.categorias.isEmpty()) {
            cats.addView(Vistas.texto(act, "Sin tickets en el último mes", 13f))
        } else {
            val max = (e.categorias.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
            e.categorias.sortedByDescending { it.total }.forEach { s: Stat ->
                val filaCat = Vistas.fila(act)
                val lbl = Vistas.texto(act, s.nombre, 12f).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
                }
                val barra = Vistas.barra(act, s.total.toFloat() / max, Ui.categoriaColor(s.nombre)).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val num = Vistas.texto(act, s.total.toString(), 12f, negrita = true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.25f)
                    gravity = android.view.Gravity.END
                }
                filaCat.addView(lbl)
                filaCat.addView(barra)
                filaCat.addView(num)
                cats.addView(filaCat)
            }
        }
        cont.addView(cats)

        val agents = Vistas.tarjeta(act)
        agents.addView(Vistas.texto(act, "Carga y Eficiencia de Agentes", 15f, negrita = true))
        agents.addView(Vistas.texto(act, "En tiempo real", 11f).apply { alpha = 0.75f })
        if (e.agentes.isEmpty()) {
            agents.addView(Vistas.texto(act, "Sin personal de soporte", 13f))
        } else {
            e.agentes.forEach { a: EficienciaAgente -> agents.addView(filaAgente(act, a)) }
        }
        cont.addView(agents)
    }

    private fun kpi(act: AppCompatActivity, titulo: String, valor: String, sub: String, color: Int): LinearLayout =
        Vistas.tarjeta(act, padding = 12).apply {
            addView(View(act).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(3))
                setBackgroundColor(color)
            })
            addView(Vistas.texto(act, titulo, 12f).apply { alpha = 0.8f })
            addView(Vistas.texto(act, valor, 22f, negrita = true))
            addView(Vistas.texto(act, sub, 11f).apply { alpha = 0.7f })
        }

    private fun filaAgente(act: AppCompatActivity, a: EficienciaAgente): LinearLayout {
        val ctx = act
        val card = Vistas.item(ctx)
        card.addView(
            Vistas.texto(ctx, "${Ui.iniciales(a.nombre)}  ${a.nombre} (${a.especialidad})", 13f, negrita = true)
        )
        card.addView(Vistas.texto(ctx, "${a.resueltos} resueltos • ${a.enProceso} en proceso", 11f).apply { alpha = 0.75f })
        val ratio = a.carga.toFloat() / 10f
        val color = when {
            ratio < 0.45f -> ContextCompat.getColor(ctx, R.color.success)
            ratio < 0.7f -> ContextCompat.getColor(ctx, R.color.warning)
            else -> ContextCompat.getColor(ctx, R.color.danger)
        }
        card.addView(Vistas.barra(ctx, ratio, color))
        return card
    }

    // --------------------------------------------------------------- reportes
    private suspend fun reportes(act: AppCompatActivity, cont: LinearLayout) {
        val cats = act.resources.getStringArray(R.array.categorias)
        val prios = act.resources.getStringArray(R.array.prioridades)
        val estadosClave = act.resources.getStringArray(R.array.estados_reporte)
        val estadosTxt = listOf(
            "Todos los Estados", "Nuevo", "Asignado", "En Proceso", "Resuelto", "Cerrado"
        )
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val desde = Ui.haceDias(30).substring(0, 10)

        val filtros = Vistas.tarjeta(act)
        filtros.addView(Vistas.texto(act, "Filtros de Reporte", 15f, negrita = true))

        val desdeEdt = campoFecha(act, "Fecha Desde", desde)
        val hastaEdt = campoFecha(act, "Fecha Hasta", hoy.format(Date()))
        filtros.addView(Vistas.fila(act).also { fila ->
            desdeEdt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Ui.dp(6) }
            hastaEdt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            fila.addView(desdeEdt)
            fila.addView(hastaEdt)
        })

        val spCat = Vistas.spinner(act, listOf("Todas las Categorías") + cats.drop(1))
        val spPrio = Vistas.spinner(act, listOf("Todas las Prioridades") + prios.drop(1))
        val spEstado = Vistas.spinner(act, estadosTxt)
        filtros.addView(etiqueta(act, "Categoría"))
        filtros.addView(spCat)
        filtros.addView(etiqueta(act, "Prioridad"))
        filtros.addView(spPrio)
        filtros.addView(etiqueta(act, "Estado"))
        filtros.addView(spEstado)
        cont.addView(filtros)

        val acciones = Vistas.fila(act)
        val resultado = Vistas.tarjeta(act)
        val contador = Vistas.texto(act, "Vista Previa de Registros", 14f, negrita = true)
        val lista = Vistas.columna(act)

        var datos: List<Ticket> = emptyList()

        fun cargar() {
            resultado.visibility = View.VISIBLE
            lista.removeAllViews()
            contador.text = "Cargando..."
            act.lifecycleScope.launch {
                try {
                    val f = DataProvider.FiltrosReporte(
                        desde = desdeEdt.text.toString().ifBlank { desde },
                        hasta = hastaEdt.text.toString().ifBlank { hoy.format(Date()) },
                        categoria = if (spCat.selectedItemPosition == 0) "todas" else cats[spCat.selectedItemPosition],
                        prioridad = if (spPrio.selectedItemPosition == 0) "todas" else prios[spPrio.selectedItemPosition],
                        estado = estadosClave[spEstado.selectedItemPosition]
                    )
                    datos = DataProvider.reportes(f)
                    contador.text = "Vista Previa de Registros (${datos.size} tickets)"
                    if (datos.isEmpty()) {
                        lista.addView(Vistas.texto(act, "No hay tickets que coincidan con los filtros", 13f))
                    } else {
                        datos.sortedByDescending { it.fechaCreacion }.forEach { t ->
                            val card = Vistas.item(act)
                            card.addView(
                                Vistas.fila(act).also { r ->
                                    r.addView(Vistas.texto(act, Ui.tkId(t.id), 12f, negrita = true))
                                    r.addView(
                                        Vistas.texto(act, t.asunto, 13f, negrita = true).apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                            ).apply { marginStart = Ui.dp(6) }
                                        }
                                    )
                                }
                            )
                            card.addView(
                                Vistas.texto(
                                    act,
                                    "${t.catNombre} • ${t.prioNivel} • ${Ui.etiquetaEstado(t.estado)}",
                                    11f
                                )
                            )
                            card.addView(
                                Vistas.texto(
                                    act,
                                    "${t.agente.ifBlank { "Sin asignar" }} • ${Ui.fecha(t.fechaCreacion)}",
                                    11f
                                ).apply { alpha = 0.75f }
                            )
                            lista.addView(card)
                        }
                    }
                } catch (ex: Exception) {
                    contador.text = "Vista Previa de Registros"
                    lista.removeAllViews()
                    error(lista, ex)
                }
            }
        }

        acciones.addView(Vistas.boton(act, "Aplicar filtros") { cargar() })
        acciones.addView(Vistas.boton(act, "Exportar CSV") { exportarCsv(act, datos) })
        cont.addView(acciones)
        cont.addView(resultado)
        resultado.addView(contador)
        resultado.addView(lista)
        cargar()
    }

    private fun etiqueta(act: AppCompatActivity, texto: String): TextView =
        Vistas.texto(act, texto, 12f, negrita = true).apply {
            setPadding(0, Ui.dp(8), 0, Ui.dp(2))
        }

    private fun campoFecha(act: AppCompatActivity, etiqueta: String, valor: String): EditText =
        EditText(act).apply {
            setText(valor)
            hint = etiqueta
            setTextColor(Vistas.colorTexto(act))
            setHintTextColor(ContextCompat.getColor(act, R.color.text_placeholder))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8))
            inputType = android.text.InputType.TYPE_CLASS_DATETIME
        }

    private fun exportarCsv(act: AppCompatActivity, datos: List<Ticket>) {
        if (datos.isEmpty()) {
            Ui.toast(act, "No hay tickets que coincidan con los filtros")
            return
        }
        val csv = buildString {
            append("ID;Asunto;Categoria;Prioridad;Estado;Agente;Fecha Creacion\n")
            datos.forEach {
                append("${it.id};${it.asunto};${it.catNombre};${it.prioNivel};")
                append("${Ui.etiquetaEstado(it.estado)};${it.agente};${it.fechaCreacion}\n")
            }
        }
        try {
            val nombre = "reporte_tickets_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv"
            val f = File(act.cacheDir, nombre)
            f.writeText("\uFEFF" + csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, nombre)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            act.startActivity(Intent.createChooser(i, "Exportar reporte"))
            Ui.toast(act, "Reporte CSV exportado (${datos.size} tickets)")
        } catch (e: Exception) {
            Ui.toast(act, "No se pudo exportar: ${e.message}")
        }
    }

    // -------------------------------------------------------------- asignación
    private suspend fun asignar(act: AppCompatActivity, cont: LinearLayout) {
        cont.addView(
            Vistas.fila(act).also { fila ->
                fila.addView(Vistas.boton(act, "Balanceo Automático IA") { balanceo(act, cont) })
            }
        )

        val datos = try {
            DataProvider.asignacion()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }

        val grid = Vistas.columna(act)
        cont.addView(grid)
        datos.agentes.forEach { a ->
            val card = Vistas.item(act)
            card.addView(
                Vistas.fila(act).also { r ->
                    r.addView(
                        Vistas.texto(act, "${Ui.iniciales(a.nombre)}  ${a.nombre}", 14f, negrita = true)
                            .apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                )
                            }
                    )
                    val ocupado = a.cargaTrabajo >= 4
                    r.addView(
                        Vistas.pill(
                            act,
                            if (ocupado) "Ocupado" else "Disponible",
                            ContextCompat.getColor(act, if (ocupado) R.color.warning else R.color.success)
                        )
                    )
                }
            )
            card.addView(Vistas.texto(act, a.especialidad ?: "Soporte general", 12f).apply { alpha = 0.8f })
            card.addView(Vistas.texto(act, "Carga de trabajo: ${a.cargaTrabajo} ticket(s)", 12f))
            val ratio = a.cargaTrabajo.toFloat() / 10f
            card.addView(
                Vistas.barra(
                    act, ratio,
                    when {
                        ratio < 0.45f -> ContextCompat.getColor(act, R.color.success)
                        ratio < 0.7f -> ContextCompat.getColor(act, R.color.warning)
                        else -> ContextCompat.getColor(act, R.color.danger)
                    }
                )
            )
            card.addView(
                Vistas.texto(
                    act,
                    "Asignados hoy: ${a.asignadosHoy}/${datos.maxDiario}" +
                        if (a.asignadosHoy >= datos.maxDiario) " — cupo diario completo" else "",
                    12f
                )
            )
            grid.addView(card)
        }

        cont.addView(Vistas.texto(act, "Tickets Pendientes de Asignación", 15f, negrita = true).apply {
            setPadding(0, Ui.dp(6), 0, Ui.dp(6))
        })

        if (datos.pendientes.isEmpty()) {
            cont.addView(Vistas.texto(act, "No hay tickets pendientes de asignación", 13f))
            return
        }

        datos.pendientes.sortedByDescending { it.prioridad }.forEach { p ->
            val card = Vistas.item(act)
            card.addView(Vistas.texto(act, "${Ui.tkId(p.id)} · ${p.asunto}", 14f, negrita = true))
            card.addView(
                Vistas.texto(act, "${p.categoria} • ${p.prioridad} • ${Ui.etiquetaEstado(p.estado)}", 11f)
            )
            card.addView(
                Vistas.texto(
                    act,
                    p.recomendacion?.let { "🤖 ${it.nombre} (${it.afinidad}% afinidad)" } ?: "Sin sugerencia",
                    12f
                )
            )
            val filaSel = Vistas.fila(act)
            val sp = Vistas.spinner(
                act,
                datos.agentes.map { "${it.nombre} (${it.asignadosHoy}/${datos.maxDiario} hoy)" })
            sp.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val btn = Vistas.boton(act, "✓ Asignar") {
                if (sp.selectedItemPosition < 0) {
                    Ui.toast(act, "Selecciona un agente para asignar el ticket")
                } else {
                    asignarA(act, p.id, datos.agentes[sp.selectedItemPosition].id)
                }
            }
            filaSel.addView(sp)
            filaSel.addView(btn)
            card.addView(filaSel)
            cont.addView(card)
        }
    }

    private fun asignarA(act: AppCompatActivity, ticketId: Int, agenteId: Int) {
        act.lifecycleScope.launch {
            try {
                DataProvider.asignarTicket(ticketId, agenteId)
                Ui.toast(act, "Ticket ${Ui.tkId(ticketId)} asignado correctamente")
            } catch (e: Exception) {
                Ui.toast(act, "No se pudo asignar: ${e.message}")
            } finally {
                (act as? CoordinadorActivity)?.refrescar()
            }
        }
    }

    private fun balanceo(act: AppCompatActivity, cont: LinearLayout) {
        act.lifecycleScope.launch {
            try {
                val datos = DataProvider.asignacion()
                if (datos.pendientes.isEmpty()) {
                    Ui.toast(act, "No hay tickets pendientes por asignar")
                    return@launch
                }
                DataProvider.balanceoIa()
                Ui.toast(act, "IA asignó ${datos.pendientes.size} ticket(s) automáticamente")
            } catch (e: Exception) {
                Ui.toast(act, "Error en balanceo automático: ${e.message}")
            } finally {
                (act as? CoordinadorActivity)?.refrescar()
            }
        }
    }

    // ------------------------------------------------------------- supervisión
    private suspend fun supervisar(act: AppCompatActivity, cont: LinearLayout) {
        cont.addView(Vistas.boton(act, "Guardar Cambios") { guardarPermisos(act, cont) })
        val agentes = try {
            DataProvider.agentes()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        if (agentes.isEmpty()) {
            cont.addView(Vistas.texto(act, "Sin personal de soporte", 13f))
            return
        }
        val cambios = mutableListOf<Pair<Usuario, Pair<Boolean, Boolean>>>()
        agentes.forEachIndexed { idx, a ->
            val card = Vistas.item(act)
            card.addView(Vistas.texto(act, a.nombre, 14f, negrita = true))
            card.addView(Vistas.texto(act, a.email, 12f).apply { alpha = 0.8f })
            card.addView(Vistas.texto(act, "Especialidad: ${a.especialidad ?: "—"} • ${a.nivelJerarquia}", 12f))
            cambios.add(a to Pair(a.permisosSupervision, a.permisosEspeciales))
            val filaPermisos = Vistas.fila(act)
            val cbSup = CheckBox(act).apply {
                text = "Supervisión"
                isChecked = a.permisosSupervision
                setOnCheckedChangeListener { _, v ->
                    cambios[idx] = cambios[idx].first to Pair(v, cambios[idx].second.second)
                }
            }
            val cbEsp = CheckBox(act).apply {
                text = "Especiales"
                isChecked = a.permisosEspeciales
                setOnCheckedChangeListener { _, v ->
                    cambios[idx] = cambios[idx].first to Pair(cambios[idx].second.first, v)
                }
            }
            filaPermisos.addView(cbSup)
            filaPermisos.addView(cbEsp)
            card.addView(filaPermisos)
            card.addView(
                Vistas.pill(
                    act, a.estado,
                    ContextCompat.getColor(act, if (a.activo) R.color.estado_activo else R.color.estado_inactivo)
                )
            )
            cont.addView(card)
        }
        cont.tag = cambios
    }

    private fun guardarPermisos(act: AppCompatActivity, cont: LinearLayout) {
        @Suppress("UNCHECKED_CAST")
        val cambios = cont.tag as? List<Pair<Usuario, Pair<Boolean, Boolean>>> ?: emptyList()
        if (cambios.isEmpty()) {
            Ui.toast(act, "No hay agentes para guardar")
            return
        }
        act.lifecycleScope.launch {
            var ok = 0
            cambios.forEach { (u, permisos) ->
                try {
                    DataProvider.guardarPermisos(u.id, permisos.first, permisos.second)
                    ok++
                } catch (_: Exception) {
                }
            }
            Ui.toast(act, "Permisos de $ok agente(s) guardados correctamente")
        }
    }

    // ---------------------------------------------------------------- chat IA
    private fun chatIa(act: AppCompatActivity, cont: LinearLayout, irA: (String) -> Unit) {
        val prompts = listOf(
            Triple(
                "Análisis de Carga de Agentes",
                "Detecta agentes con sobrecupo y sugiere redistribución óptima.",
                "¿Qué agentes tienen mayor sobrecarga de tickets hoy y cómo puedo rebalancear?"
            ),
            Triple(
                "Resumen de Tickets Críticos",
                "Identifica tickets urgentes sin resolver o próximos a vencer su SLA.",
                "Genera un resumen ejecutivo de los tickets críticos con riesgo de incumplir SLA."
            ),
            Triple(
                "Detección de Cuellos de Botella",
                "Analiza patrones recurrentes de incidentes en redes, hardware o software.",
                "¿Cuáles son las categorías más reportadas esta semana y sus causas raíz?"
            )
        )
        prompts.forEach { (titulo, desc, prompt) ->
            val card = Vistas.tarjeta(act)
            card.addView(Vistas.texto(act, titulo, 14f, negrita = true))
            card.addView(Vistas.texto(act, desc, 12f).apply { alpha = 0.8f })
            card.addView(
                Vistas.boton(act, "Preguntar a la IA") {
                    act.startActivity(
                        Intent(act, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_PROMPT, prompt)
                    )
                }
            )
            cont.addView(card)
        }
        cont.addView(Vistas.boton(act, "Abrir Chat Soporte") {
            act.startActivity(Intent(act, ChatActivity::class.java))
        })
    }

    // -------------------------------------------------------------------- RAG
    private suspend fun rag(act: AppCompatActivity, cont: LinearLayout) {
        val entrada = EditText(act).apply {
            hint = "Escribe el problema o síntoma técnico (ej: pantalla azul error driver wifi)..."
            setTextColor(Vistas.colorTexto(act))
            setHintTextColor(ContextCompat.getColor(act, R.color.text_placeholder))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10))
        }
        cont.addView(entrada)

        val resultados = Vistas.columna(act)
        val estado = Vistas.texto(act, "", 13f, negrita = true)

        fun buscar() {
            val q = entrada.text.toString().trim()
            if (q.isEmpty()) {
                Ui.toast(act, "Escribe una consulta para buscar en RAG")
                return
            }
            estado.text = "Buscando..."
            act.lifecycleScope.launch {
                resultados.removeAllViews()
                try {
                    val rs = DataProvider.rag(q)
                    estado.text = "Soluciones Similares Encontradas • Similitud Coseno HNSW"
                    if (rs.isEmpty()) {
                        resultados.addView(Vistas.texto(act, "No se encontraron soluciones similares", 13f))
                    } else rs.forEach { r ->
                        val card = Vistas.item(act)
                        card.addView(
                            Vistas.fila(act).also { fila ->
                                fila.addView(
                                    Vistas.texto(act, "Solución para: ${r.asunto}", 13f, negrita = true)
                                        .apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                                            )
                                        }
                                )
                                fila.addView(
                                    Vistas.pill(
                                        act,
                                        String.format(Locale.US, "%.1f%% Similitud", r.similitud * 100),
                                        ContextCompat.getColor(act, R.color.info)
                                    )
                                )
                            }
                        )
                        card.addView(Vistas.texto(act, r.descripcion.take(180), 12f).apply { alpha = 0.85f })
                        card.addView(
                            Vistas.texto(
                                act,
                                "Estado: ${Ui.etiquetaEstado(r.estado)} • Categoría: ${r.categoria}",
                                11f
                            )
                        )
                        card.addView(
                            Vistas.texto(
                                act,
                                "Ticket fuente: ${Ui.tkId(r.id)} • Atención: ${r.agente.ifBlank { "—" }}",
                                11f
                            ).apply { alpha = 0.7f }
                        )
                        resultados.addView(card)
                    }
                } catch (ex: Exception) {
                    estado.text = ""
                    error(resultados, ex)
                }
            }
        }

        fun indexar() {
            estado.text = "Indexando..."
            act.lifecycleScope.launch {
                try {
                    DataProvider.indexarRag()
                    Ui.toast(act, "Tickets indexados correctamente")
                    estado.text = "Índice RAG actualizado"
                } catch (ex: Exception) {
                    estado.text = ""
                    Ui.toast(act, "Error al indexar: ${ex.message}")
                }
            }
        }

        val acciones = Vistas.fila(act)
        acciones.addView(Vistas.boton(act, "Buscar con RAG") { buscar() })
        acciones.addView(Vistas.boton(act, "Indexar tickets") { indexar() })
        cont.addView(acciones)
        cont.addView(estado)
        cont.addView(resultados)
    }

    // -------------------------------------------------------------- mi perfil
    private suspend fun perfil(act: AppCompatActivity, cont: LinearLayout) {
        val s = Session.sesion()
        val card = Vistas.tarjeta(act)
        card.addView(Vistas.texto(act, "👑", 34f))
        card.addView(Vistas.texto(act, s?.nombre ?: "Coordinador TI", 17f, negrita = true))
        card.addView(Vistas.texto(act, s?.email ?: "coordinador@empresa.com", 13f).apply { alpha = 0.8f })
        card.addView(
            Vistas.texto(act, "Rol: ${s?.rolEtiqueta ?: "Coordinador"} / Supervisor", 13f).apply { alpha = 0.9f }
        )
        card.addView(Vistas.texto(act, "Área: ${s?.area ?: "TI"}", 13f).apply { alpha = 0.9f })
        cont.addView(card)
    }

    // -------------------------------------------------------------------- SLA
    private suspend fun sla(act: AppCompatActivity, cont: LinearLayout) {
        val actuales = try {
            DataProvider.sla()
        } catch (ex: Exception) {
            error(cont, ex)
            return
        }
        val niveles = listOf("Prioridad Crítica", "Prioridad Alta", "Prioridad Media", "Prioridad Baja")
        val filas = mutableListOf<Triple<Int, EditText, EditText>>()

        actuales.forEachIndexed { i, s: Sla ->
            val card = Vistas.item(act)
            card.addView(
                Vistas.fila(act).also { r ->
                    r.addView(
                        Vistas.texto(act, niveles.getOrElse(i) { s.nivel }, 14f, negrita = true)
                            .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                    )
                    r.addView(Vistas.pill(act, "SLA Nivel ${i + 1}", Ui.prioridadPill(s.nivel)))
                }
            )
            val resp = EditText(act).apply {
                setText(s.respuestaMin.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(Vistas.colorTexto(act))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6))
            }
            val sol = EditText(act).apply {
                setText(s.solucionMin.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setTextColor(Vistas.colorTexto(act))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6))
            }
            card.addView(Vistas.texto(act, "Tiempo Máx. Respuesta (min)", 12f, negrita = true))
            card.addView(resp)
            card.addView(Vistas.texto(act, "Tiempo Máx. Solución (min)", 12f, negrita = true))
            card.addView(sol)
            filas.add(Triple(s.id, resp, sol))
            cont.addView(card)
        }

        cont.addView(Vistas.boton(act, "Guardar Políticas SLA") {
            if (filas.isEmpty()) {
                Ui.toast(act, "No hay políticas SLA para guardar")
                return@boton
            }
            val nueva = filas.map { (id, resp, sol) ->
                Sla(
                    id = id,
                    nivel = "",
                    color = "",
                    respuestaMin = resp.text.toString().toIntOrNull() ?: 0,
                    solucionMin = sol.text.toString().toIntOrNull() ?: 0,
                    activo = true
                )
            }
            act.lifecycleScope.launch {
                try {
                    DataProvider.guardarSla(nueva)
                    Ui.toast(act, "⏱️ Políticas SLA guardadas correctamente")
                } catch (e: Exception) {
                    Ui.toast(act, "Error al guardar SLA: ${e.message}")
                }
            }
        })
    }
}
