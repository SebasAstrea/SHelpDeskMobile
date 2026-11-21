package online.sistemahelpdesk.movil.core

import online.sistemahelpdesk.movil.data.*

/**
 * Datos locales de demostración. Se usan cuando el backend no responde, para que
 * todas las pantallas sirvan contenido con usuarios genéricos (offline-first).
 *
 * El estado se mantiene en memoria durante la sesión: crear tickets, mover el
 * kanban, asignar o chatear funciona igual que con el backend real.
 */
object MockData {

    // ---------------------------------------------------------------- usuarios
    val usuarios: MutableList<Usuario> = mutableListOf(
        Usuario(1, "Laura Méndez", "laura.mendez@empresa.com", Roles.SOLICITANTE, "Producción", "activo"),
        Usuario(2, "Carlos Rodríguez", "carlos.rodriguez@empresa.com", Roles.AGENTE, "TI", "activo",
            cargaTrabajo = 3, especialidad = "Hardware y Redes"),
        Usuario(3, "Ana Martínez", "ana.martinez@empresa.com", Roles.AGENTE, "TI", "activo",
            cargaTrabajo = 2, especialidad = "Administración de Sistemas"),
        Usuario(4, "Diego Torres", "diego.torres@empresa.com", Roles.AGENTE, "TI", "activo",
            cargaTrabajo = 4, especialidad = "Gestión de Equipos"),
        Usuario(5, "María González", "maria.gonzalez@empresa.com", Roles.COORDINADOR, "TI", "activo",
            especialidad = "Coordinación de Soporte"),
        Usuario(6, "Roberto Salas", "roberto.salas@empresa.com", Roles.ADMINISTRADOR, "TI", "activo"),
        Usuario(7, "Solicitante Demo", "solicitante@demo.helpdesk", Roles.SOLICITANTE, "RRHH", "activo"),
        Usuario(8, "Agente Demo", "agente@demo.helpdesk", Roles.AGENTE, "TI", "activo",
            cargaTrabajo = 3, especialidad = "Soporte General"),
        Usuario(9, "Coordinador Demo", "coordinador@demo.helpdesk", Roles.COORDINADOR, "TI", "activo",
            especialidad = "Coordinación de Soporte"),
        Usuario(10, "Admin Demo", "admin@demo.helpdesk", Roles.ADMINISTRADOR, "TI", "activo"),
        Usuario(11, "Pedro Núñez", "pedro.nunez@empresa.com", Roles.SOLICITANTE, "Ventas", "inactivo"),
        Usuario(12, "Sofía Vargas", "sofia.vargas@empresa.com", Roles.AGENTE, "TI", "activo",
            cargaTrabajo = 1, especialidad = "Redes")
    )

    private val porEmail = mapOf(
        "solicitante@demo.helpdesk" to 7,
        "agente@demo.helpdesk" to 8,
        "coordinador@demo.helpdesk" to 9,
        "admin@demo.helpdesk" to 10
    )

    /** ¿Es uno de los correos de los chips genéricos? */
    fun esEmailDemo(email: String): Boolean =
        porEmail.containsKey(email.trim().lowercase())

    /** Resuelve el usuario genérico a partir del email escrito (o de un email cualquiera). */
    fun usuarioPorEmail(email: String): Usuario {
        val limpio = email.trim().lowercase()
        porEmail[limpio]?.let { id -> usuarios.firstOrNull { it.id == id } }?.let { return it }
        usuarios.firstOrNull { it.email.equals(limpio, true) }?.let { return it }
        val rol = when {
            limpio.contains("admin") -> Roles.ADMINISTRADOR
            limpio.contains("coord") -> Roles.COORDINADOR
            limpio.contains("agent") || limpio.contains("tecnic") -> Roles.AGENTE
            else -> Roles.SOLICITANTE
        }
        val base = limpio.substringBefore('@').ifBlank { "usuario" }
        return Usuario(
            id = 900 + rol.hashCode().and(0xFF),
            nombre = base.replace('.', ' ').replaceFirstChar { it.uppercase() },
            email = email.trim(),
            rol = rol,
            area = if (rol == Roles.SOLICITANTE) "Operaciones" else "TI",
            estado = "activo",
            especialidad = if (rol == Roles.AGENTE) "Soporte General" else null
        )
    }

    fun sesionDe(u: Usuario): Sesion =
        Sesion(u.id, u.nombre, u.email, u.rol, u.area, mfaEnabled = false)

    fun usuario(id: Int): Usuario? = usuarios.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- tickets
    val tickets: MutableList<Ticket> = mutableListOf()

    private fun t(
        id: Int, sol: Int, asunto: String, desc: String, estado: String,
        prio: String, cat: String, catId: Int, prioId: Int, agente: Int?,
        dias: Int, solucion: String? = null
    ) = Ticket(
        id = id, idSolicitante = sol, asunto = asunto, descripcion = desc, estado = estado,
        prioridad = prio, categoria = catId.toString(), fechaCreacion = Ui.haceDias(dias),
        fechaActualizacion = Ui.haceDias(dias / 2), idAgente = agente,
        agente = agente?.let { usuario(it)?.nombre ?: "Sin asignar" } ?: "Sin asignar",
        catNombre = cat, prioNivel = prio, prioColor = "",
        solucion = solucion
    )

    init {
        if (tickets.isEmpty()) {
            tickets += t(1, 7, "No enciende la PC de recepción",
                "Al presionar el botón de encendido no hay respuesta ni led de la placa.",
                Estados.ASIGNADO, Prioridades.ALTA, "Hardware", 1, 2, 8, 1)
            tickets += t(2, 7, "Error de acceso al sistema de nómina",
                "Mensaje 'credenciales inválidas' al iniciar sesión en el portal de nómina.",
                Estados.EN_PROCESO, Prioridades.CRITICA, "Cuentas/Accesos", 4, 1, 8, 0)
            tickets += t(3, 7, "Impresora del área de logística no responde",
                "La impresora HP del almacén queda en cola y no imprime documentos.",
                Estados.RESUELTO, Prioridades.MEDIA, "Hardware", 1, 3, 8, 3,
                "Se reinstaló el driver y se limpió la cola de trabajos; prueba de impresión correcta.")
            tickets += t(4, 7, "Corte intermitente de internet en piso 2",
                "La conexión se cae cada 10 minutos en las workstations del piso 2.",
                Estados.CERRADO, Prioridades.ALTA, "Red/Internet", 3, 2, 8, 6,
                "Se reemplazó el cable de uplink defectuoso y se actualizó el firmware del switch.")
            tickets += t(5, 7, "Solicitud de licencia de Office",
                "Se necesita la licencia para la nueva analista del área de Finanzas.",
                Estados.NUEVO, Prioridades.BAJA, "Software", 2, 4, null, 0)
            tickets += t(6, 7, "Lentitud general en el equipo contable",
                "El equipo tarda más de 5 minutos en iniciar sesión por las mañanas.",
                Estados.EN_PROCESO, Prioridades.MEDIA, "Software", 2, 3, 8, 2)
            tickets += t(7, 1, "Monitor con líneas horizontales",
                "Se ven rayas en la pantalla desde la actualización de ayer.",
                Estados.ASIGNADO, Prioridades.MEDIA, "Hardware", 1, 3, 2, 1)
            tickets += t(8, 1, "No recibo correos de proveedores",
                "Los correos externos llegan a la carpeta de correo no deseado.",
                Estados.RESUELTO, Prioridades.ALTA, "Red/Internet", 3, 2, 3, 4,
                "Se ajustaron las reglas SPF/DKIM y se revisó la lista de remitentes bloqueados.")
            tickets += t(9, 11, "Alta de usuario en Active Directory",
                "Solicitud de cuenta para personal temporal de calidad.",
                Estados.ESCALADO, Prioridades.ALTA, "Cuentas/Accesos", 4, 2, 4, 1)
            tickets += t(10, 1, "Sistema de inventario marca error 500",
                "Al abrir el módulo de inventario aparece error interno del servidor.",
                Estados.NUEVO, Prioridades.CRITICA, "Software", 2, 1, null, 0)
            tickets += t(11, 1, "WiFi no conecta en sala de juntas",
                "Los invitados no pueden unirse a la red corporativa en la sala.",
                Estados.EN_PROCESO, Prioridades.MEDIA, "Red/Internet", 3, 3, 12, 2)
            tickets += t(12, 1, "Teclado con teclas atascadas",
                "Faltan respuesta en varias teclas del teclado mecánico.",
                Estados.CERRADO, Prioridades.BAJA, "Hardware", 1, 4, 4, 7,
                "Se entregó teclado de repuesto y se retiró el defectuoso.")
            tickets += t(13, 7, "Restablecimiento de contraseña VPN",
                "El usuario no recuerda la contraseña del cliente VPN corporativo.",
                Estados.CERRADO, Prioridades.BAJA, "Cuentas/Accesos", 4, 4, 8, 5,
                "Se restableció la contraseña y se verificó el acceso desde el equipo del usuario.")
            tickets += t(14, 1, "Instalación de software de diseño",
                "Requiero instalar la suite de diseño aprobada para la campaña.",
                Estados.RESUELTO, Prioridades.MEDIA, "Software", 2, 3, 8, 2,
                "Se instaló la versión aprobada y se validó la licencia con Finanzas.")
        }
    }

    fun ticketsVisibles(rol: String, userId: Int): List<Ticket> = when (rol) {
        Roles.SOLICITANTE -> tickets.filter { it.idSolicitante == userId }
        Roles.AGENTE -> tickets.filter { it.idAgente == userId && it.estado != Estados.NUEVO }
        else -> tickets.filter { it.estado != Estados.ARCHIVADO }
    }

    fun ticket(id: Int): Ticket? = tickets.firstOrNull { it.id == id }

    fun detalle(id: Int): TicketDetalle? {
        val t = ticket(id) ?: return null
        return TicketDetalle(
            ticket = t,
            solicitante = usuario(t.idSolicitante),
            agente = t.idAgente?.let { usuario(it) },
            analisis = if (t.confianzaIa != null || t.id % 2 == 0)
                AnalisisIa(
                    prioridadIa = t.prioNivel, categoriaIa = t.catNombre,
                    confianza = 0.94, modelo = "llama3.2:3b", tokens = 128,
                    tiempoMs = 842, fecha = t.fechaCreacion,
                    revisionManual = false, comentario = null
                ) else null,
            adjuntos = emptyList(),
            historial = listOf(
                ItemHistorial("nuevo", t.estado, t.fechaActualizacion)
            )
        )
    }

    fun actualizar(id: Int, estado: String?, solucion: String?): Boolean {
        val i = tickets.indexOfFirst { it.id == id }
        if (i < 0) return false
        val actual = tickets[i]
        tickets[i] = actual.copy(
            estado = estado ?: actual.estado,
            solucion = solucion ?: actual.solucion,
            fechaActualizacion = Ui.ahoraIso()
        )
        return true
    }

    fun crear(asunto: String, descripcion: String, idCategoria: Int, idPrioridad: Int, solicitante: Int): Int {
        val id = (tickets.maxOfOrNull { it.id } ?: 0) + 1
        val cats = listOf("", "Hardware", "Software", "Red/Internet", "Cuentas/Accesos", "Otros")
        val prios = listOf("", Prioridades.CRITICA, Prioridades.ALTA, Prioridades.MEDIA, Prioridades.BAJA)
        tickets += Ticket(
            id = id, idSolicitante = solicitante, asunto = asunto, descripcion = descripcion,
            estado = Estados.NUEVO, prioridad = prios.getOrElse(idPrioridad) { Prioridades.MEDIA },
            categoria = idCategoria.toString(), fechaCreacion = Ui.ahoraIso(),
            fechaActualizacion = Ui.ahoraIso(), idAgente = null, agente = "Sin asignar",
            catNombre = cats.getOrElse(idCategoria) { "Otros" },
            prioNivel = prios.getOrElse(idPrioridad) { Prioridades.MEDIA }, prioColor = ""
        )
        return id
    }

    // ------------------------------------------------------------ estadísticas
    fun estadisticas(): Estadisticas {
        val abiertos = tickets.count { it.estado !in listOf(Estados.CERRADO, Estados.ARCHIVADO) }
        val resueltos = tickets.count { it.estado in listOf(Estados.RESUELTO, Estados.CERRADO) }
        val cumplimiento = if (tickets.isEmpty()) null else resueltos * 100.0 / tickets.size
        val porCat = tickets.groupBy { it.catNombre }.map { Stat(it.key, it.value.size) }
            .sortedByDescending { it.total }
        val agentes = usuarios.filter { it.rol == Roles.AGENTE && it.activo }.map { a ->
            val suyos = tickets.filter { it.idAgente == a.id }
            EficienciaAgente(
                id = a.id, nombre = a.nombre, especialidad = a.especialidad ?: "Soporte",
                carga = a.cargaTrabajo, asignados = suyos.size,
                resueltos = suyos.count { it.estado in listOf(Estados.RESUELTO, Estados.CERRADO) },
                enProceso = suyos.count { it.estado == Estados.EN_PROCESO }
            )
        }
        return Estadisticas(
            kpis = Kpis(
                totalTicketsMes = tickets.size,
                cumplimientoSla = cumplimiento,
                tiempoMedioSolucion = 42,
                agentesActivos = agentes.size,
                agentesTotales = usuarios.count { it.rol == Roles.AGENTE },
                ticketsActivos = abiertos
            ),
            categorias = porCat,
            agentes = agentes
        )
    }

    fun rolesDistribucion(): List<Triple<String, Int, Int>> =
        listOf(
            Roles.SOLICITANTE, Roles.AGENTE, Roles.COORDINADOR, Roles.ADMINISTRADOR
        ).map { rol ->
            val total = usuarios.count { it.rol == rol }
            val activos = usuarios.count { it.rol == rol && it.activo }
            Triple(rol, total, activos)
        }

    // ------------------------------------------------------------- asignación
    fun asignacion(): AsignacionData {
        val agentes = usuarios.filter { it.rol == Roles.AGENTE && it.activo }
        val pendientes = tickets.filter { it.estado in listOf(Estados.NUEVO, Estados.ASIGNADO) && !it.tieneAgente }
            .map { tk ->
                val mejor = agentes.minByOrNull { it.cargaTrabajo }
                TicketPendiente(
                    id = tk.id, asunto = tk.asunto, estado = tk.estado, categoria = tk.catNombre,
                    prioridad = tk.prioNivel,
                    recomendacion = mejor?.let { RecomendacionIa(it.id, it.nombre, 88 - (it.cargaTrabajo * 3)) }
                )
            }
        return AsignacionData(agentes, pendientes, 3)
    }

    fun asignar(ticketId: Int, agenteId: Int): Boolean {
        val i = tickets.indexOfFirst { it.id == ticketId }
        if (i < 0) return false
        val t = tickets[i]
        tickets[i] = t.copy(
            idAgente = agenteId,
            agente = usuario(agenteId)?.nombre ?: "Sin asignar",
            estado = if (t.estado == Estados.NUEVO) Estados.ASIGNADO else t.estado,
            fechaActualizacion = Ui.ahoraIso()
        )
        val a = usuario(agenteId)
        if (a != null) {
            val idx = usuarios.indexOfFirst { it.id == agenteId }
            usuarios[idx] = a.copy(cargaTrabajo = a.cargaTrabajo + 1, asignadosHoy = a.asignadosHoy + 1)
        }
        return true
    }

    fun guardarPermisos(agenteId: Int, supervision: Boolean, especiales: Boolean): Boolean {
        val idx = usuarios.indexOfFirst { it.id == agenteId }
        if (idx < 0) return false
        usuarios[idx] = usuarios[idx].copy(
            permisosSupervision = supervision, permisosEspeciales = especiales
        )
        return true
    }

    // ------------------------------------------------------------------- SLA
    val slas: MutableList<Sla> = mutableListOf(
        Sla(1, Prioridades.CRITICA, "#FF0000", 15, 60, true),
        Sla(2, Prioridades.ALTA, "#FFA500", 30, 240, true),
        Sla(3, Prioridades.MEDIA, "#FFFF00", 60, 480, true),
        Sla(4, Prioridades.BAJA, "#00FF00", 120, 1440, false)
    )

    fun guardarSla(nueva: List<Sla>) {
        slas.clear()
        slas += nueva
    }

    // ------------------------------------------------------------------- RAG
    fun rag(consulta: String): List<RagResultado> {
        if (consulta.isBlank()) return emptyList()
        val q = consulta.lowercase()
        return tickets
            .filter {
                it.estado in listOf(Estados.RESUELTO, Estados.CERRADO) &&
                    (it.asunto.lowercase().contains(q) || it.descripcion.lowercase().contains(q) ||
                        it.catNombre.lowercase().contains(q) || q.split(" ").any { p -> p.length > 3 && it.asunto.lowercase().contains(p) })
            }
            .take(5)
            .mapIndexed { i, t ->
                RagResultado(
                    id = t.id, asunto = t.asunto, descripcion = t.descripcion, estado = t.estado,
                    categoria = t.catNombre, agente = t.agente,
                    similitud = 0.94 - i * 0.07
                )
            }
    }

    // ------------------------------------------------------- administración
    val tablasBD: List<TablaBD> = listOf(
        TablaBD("usuarios", 12), TablaBD("solicitudes", tickets.size),
        TablaBD("categorias", 5), TablaBD("prioridades", 4),
        TablaBD("adjuntos", 7), TablaBD("historial_estados", 34),
        TablaBD("chat_global_mensajes", 58), TablaBD("chat_privado_mensajes", 21),
        TablaBD("configuracion_ia", 8), TablaBD("embeddings_ticket", 14)
    )

    val respaldos: MutableList<Respaldo> = mutableListOf(
        Respaldo("helpdesk_20261001_030000.dump", 4_820_000, "2026-10-01 03:00"),
        Respaldo("helpdesk_20261004_030000.dump", 4_910_000, "2026-10-04 03:00"),
        Respaldo("helpdesk_20261007_030000.dump", 5_040_000, "2026-10-07 03:00")
    )

    val workflows: MutableList<Workflow> = mutableListOf(
        Workflow("1", "Clasificación de tickets con IA", true),
        Workflow("2", "Notificación al agente asignado", true),
        Workflow("3", "Alerta de incumplimiento de SLA", false),
        Workflow("4", "Indexación RAG al cerrar ticket", true),
        Workflow("5", "Resumen diario de métricas", false)
    )

    val modelosIa: MutableList<ModeloIa> = mutableListOf(
        ModeloIa("llama3.2:3b", 2_000_000_000, "chat", true),
        ModeloIa("qwen2.5:3b", 1_900_000_000, "chat", false),
        ModeloIa("bge-m3", 2_200_000_000, "embeddings", false)
    )

    var configIa: ConfigIa = ConfigIa()

    fun logs(contenedor: String, lineas: Int): String = buildString {
        appendLine("== $contenedor (demo local) ==")
        repeat(minOf(lineas, 24)) { i ->
            appendLine("${String.format("%02d", 8 + i / 6)}:${String.format("%02d", (i * 7) % 60)}:$contenedor[${i % 4}]  INFO  evento ${i + 1} procesado correctamente")
        }
        appendLine("-- $lineas líneas solicitadas (datos de demostración) --")
    }

    // ------------------------------------------------------------------ chat
    val chatGlobal: MutableList<MensajeChat> = mutableListOf(
        MensajeChat(1, 5, "María González", Roles.COORDINADOR, "TI",
            "Canal común de todo el equipo HelpDesk. Los mensajes son visibles para todos los roles.", Ui.haceDias(1)),
        MensajeChat(2, 8, "Agente Demo", Roles.AGENTE, "TI",
            "Buenos días, ya revisé los tickets de hardware pendientes.", Ui.haceDias(0)),
        MensajeChat(3, 2, "Carlos Rodríguez", Roles.AGENTE, "TI",
            "Recuerden documentar la solución antes de cerrar.", Ui.haceDias(0)),
        MensajeChat(4, 7, "Solicitante Demo", Roles.SOLICITANTE, "RRHH",
            "Gracias, la impresora ya vuelve a imprimir.", Ui.haceDias(0))
    )

    private var siguienteMensaje = 100

    fun enviarGlobal(userId: Int, nombre: String, rol: String, area: String?, texto: String): MensajeChat {
        val m = MensajeChat(siguienteMensaje++, userId, nombre, rol, area, texto, Ui.ahoraIso())
        chatGlobal += m
        return m
    }

    val contactos: List<ContactoPrivado> get() =
        usuarios.filter { it.rol in listOf(Roles.AGENTE, Roles.COORDINADOR, Roles.ADMINISTRADOR) }
            .take(5)
            .mapIndexed { i, u ->
                ContactoPrivado(u.id, u.nombre, u.rol, u.area, if (i == 0) 2 else 0,
                    "Hola, ¿cómo vamos con el ticket #TK-000${i + 1}?", Ui.haceDias(i))
            }

    private val privadas: MutableMap<Int, MutableList<MensajePrivado>> = mutableMapOf()

    fun conversacion(otroId: Int): List<MensajePrivado> = privadas.getOrPut(otroId) {
        mutableListOf(
            MensajePrivado(1, otroId, 8, "Hola, ¿puedes revisar el ticket de la impresora?", false, Ui.haceDias(1)),
            MensajePrivado(2, 8, otroId, "Claro, lo reviso ahora mismo.", true, Ui.haceDias(1))
        )
    }

    fun enviarPrivada(otroId: Int, mioId: Int, texto: String): MensajePrivado {
        val lista = privadas.getOrPut(otroId) { mutableListOf() }
        val m = MensajePrivado(siguienteMensaje++, mioId, otroId, texto, false, Ui.ahoraIso())
        lista += m
        return m
    }

    fun noLeidosPrivados(): Int = 3

    // ------------------------------------------------------------------- IA
    fun respuestaIa(consulta: String): String {
        val q = consulta.lowercase()
        return when {
            q.contains("sobrecarga") || q.contains("rebalance") || q.contains("carga") ->
                "Con los datos de demostración: Carlos Rodríguez está al 80% de su cupo diario (4/5) y Ana Martínez al 40% (2/5). Sugiero derivar los tickets nuevos de Hardware a Ana y mantener a Carlos en Cuentas/Accesos, que es su especialidad."
            q.contains("crític") || q.contains("sla") || q.contains("resumen") ->
                "Resumen ejecutivo: hay ${tickets.count { it.prioNivel == Prioridades.CRITICA }} tickets críticos, de los cuales ${tickets.count { it.prioNivel == Prioridades.CRITICA && it.estado !in listOf(Estados.RESUELTO, Estados.CERRADO) }} siguen abiertos. El de 'Sistema de inventario marca error 500' es el más urgente por SLA (15 min de respuesta)."
            q.contains("categor") || q.contains("causa") || q.contains("semana") ->
                "Las categorías más reportadas esta semana son ${estadisticas().categorias.take(3).joinToString(", ") { "${it.nombre} (${it.total})" }}. Causa raíz probable: equipos de hardware sin mantenimiento preventivo y accesos dados de baja sin notificación."
            q.contains("vpn") || q.contains("autenticación") ->
                "Para el error de autenticación en VPN: verifica credenciales, sincroniza la hora del equipo y revisa la política de bloqueo tras 5 intentos fallidos. En el histórico aparece una solución equivalente en #TK-0004 con 94% de similitud."
            else ->
                " Soy el asistente IA del HelpDesk (modo demostración, sin backend). Puedo ayudarte a analizar carga de agentes, resumir tickets críticos o explicar soluciones del histórico. Prueba con: '¿Qué agentes tienen mayor sobrecarga hoy?'"
        }
    }

    fun saludOk(): Boolean = false
}
