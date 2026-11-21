package online.sistemahelpdesk.movil.data

import org.json.JSONArray
import org.json.JSONObject

/** Rol de usuario (enums del backend). */
object Roles {
    const val SOLICITANTE = "solicitante"
    const val AGENTE = "agente"
    const val COORDINADOR = "coordinador"
    const val ADMINISTRADOR = "administrador"

    fun etiqueta(rol: String?): String = when (rol) {
        SOLICITANTE -> "Solicitante"
        AGENTE -> "Agente"
        COORDINADOR -> "Coordinador"
        ADMINISTRADOR -> "Administrador"
        else -> "Sin rol"
    }
}

/** Estados de ticket (CHECK de la BD original). */
object Estados {
    const val NUEVO = "nuevo"
    const val ASIGNADO = "asignado"
    const val EN_PROCESO = "en_proceso"
    const val ESCALADO = "escalado"
    const val RESUELTO = "resuelto"
    const val CERRADO = "cerrado"
    const val ARCHIVADO = "archivado"

    fun etiqueta(estado: String?): String = when (estado) {
        NUEVO -> "Nuevo"
        ASIGNADO -> "Asignado"
        EN_PROCESO -> "En proceso"
        ESCALADO -> "Escalado"
        RESUELTO -> "Resuelto"
        CERRADO -> "Cerrado"
        ARCHIVADO -> "Archivado"
        else -> estado ?: ""
    }
}

/** Prioridades (1..4). */
object Prioridades {
    const val CRITICA = "crítica"
    const val ALTA = "alta"
    const val MEDIA = "media"
    const val BAJA = "baja"

    /** Prioridad -> columna del kanban (COLUMN_MAP del JS). */
    fun columnaDeEstado(estado: String?): String = when (estado) {
        Estados.NUEVO, Estados.ASIGNADO -> "todo"
        Estados.EN_PROCESO, Estados.ESCALADO -> "progress"
        Estados.RESUELTO -> "review"
        Estados.CERRADO -> "done"
        else -> ""
    }

    /** Columna -> estado al soltar una tarjeta. */
    fun estadoParaColumna(columna: String, tieneAgente: Boolean): String = when (columna) {
        "todo" -> if (tieneAgente) Estados.ASIGNADO else Estados.NUEVO
        "progress" -> Estados.EN_PROCESO
        "review" -> Estados.RESUELTO
        "done" -> Estados.CERRADO
        else -> ""
    }
}

data class Sesion(
    val userId: Int,
    val nombre: String,
    val email: String,
    val rol: String,
    val area: String?,
    val mfaEnabled: Boolean = false
) {
    val rolEtiqueta: String get() = Roles.etiqueta(rol)
}

data class Usuario(
    val id: Int,
    val nombre: String,
    val email: String,
    val rol: String,
    val area: String?,
    val estado: String,
    val cargaTrabajo: Int = 0,
    val rolAnterior: String? = null,
    val adminTemporalHasta: String? = null,
    val fechaUltimoAcceso: String? = null,
    val fechaRegistro: String? = null,
    val especialidad: String? = null,
    val nivelJerarquia: String = "Técnico",
    val permisosSupervision: Boolean = false,
    val permisosEspeciales: Boolean = false,
    val asignadosHoy: Int = 0
) {
    val activo: Boolean get() = estado == "activo"

    companion object {
        fun desdeJson(o: JSONObject): Usuario = Usuario(
            id = o.optInt("id_usuario", o.optInt("id", 0)),
            nombre = o.optString("nombre", ""),
            email = o.optString("email", ""),
            rol = o.optString("rol", Roles.SOLICITANTE),
            area = o.optString("area").ifEmpty { null },
            estado = o.optString("estado", "activo"),
            cargaTrabajo = o.optInt("carga_trabajo", 0),
            rolAnterior = o.optString("rol_anterior").ifEmpty { null },
            adminTemporalHasta = o.optString("admin_temporal_hasta").ifEmpty { null },
            fechaUltimoAcceso = o.optString("fecha_ultimo_acceso").ifEmpty { null },
            fechaRegistro = o.optString("fecha_registro").ifEmpty { null },
            especialidad = o.optString("especialidad").ifEmpty { null },
            nivelJerarquia = o.optString("nivel_jerarquia", "Técnico"),
            permisosSupervision = o.optBoolean("permisos_supervision", false),
            permisosEspeciales = o.optBoolean("permisos_especiales", false),
            asignadosHoy = o.optInt("asignados_hoy", 0)
        )
    }
}

data class Ticket(
    val id: Int,
    val idSolicitante: Int,
    val asunto: String,
    val descripcion: String,
    val estado: String,
    val prioridad: String,
    val categoria: String,
    val fechaCreacion: String,
    val fechaActualizacion: String,
    val idAgente: Int? = null,
    val agente: String = "Sin asignar",
    val catNombre: String,
    val prioNivel: String,
    val prioColor: String,
    val confianzaIa: Double? = null,
    val prioridadIa: String? = null,
    val solucion: String? = null
) {
    val tieneAgente: Boolean get() = idAgente != null && idAgente > 0
    val columna: String get() = Prioridades.columnaDeEstado(estado)

    companion object {
        fun desdeJson(o: JSONObject): Ticket {
            val idAgente = if (o.isNull("id_agente_asignado")) null else o.optInt("id_agente_asignado", 0)
            return Ticket(
                id = o.optInt("id_solicitud", 0),
                idSolicitante = o.optInt("id_solicitante", 0),
                asunto = o.optString("asunto", ""),
                descripcion = o.optString("descripcion", ""),
                estado = o.optString("estado", Estados.NUEVO),
                prioridad = o.optString("prioridad", ""),
                categoria = o.optString("categoria", ""),
                fechaCreacion = o.optString("fecha_creacion", ""),
                fechaActualizacion = o.optString("fecha_actualizacion", ""),
                idAgente = idAgente,
                agente = o.optString("agente", "Sin asignar"),
                catNombre = o.optString("cat_nombre", o.optString("categoria", "Otros")),
                prioNivel = o.optString("prio_nivel", o.optString("prioridad", Prioridades.MEDIA)),
                prioColor = o.optString("prio_color", ""),
                confianzaIa = if (o.isNull("confianza_ia")) null else o.optDouble("confianza_ia", 0.0),
                prioridadIa = o.optString("prioridad_ia").ifEmpty { null },
                solucion = o.optString("solucion").ifEmpty { null }
            )
        }

        fun lista(json: JSONArray): List<Ticket> =
            (0 until json.length()).map { Ticket.desdeJson(json.getJSONObject(it)) }
    }
}

data class Adjunto(
    val id: Int,
    val nombre: String,
    val tipo: String,
    val tamano: Long,
    val fecha: String
)

data class ItemHistorial(
    val anterior: String,
    val nuevo: String,
    val fecha: String
)

data class AnalisisIa(
    val prioridadIa: String,
    val categoriaIa: String,
    val confianza: Double,
    val modelo: String,
    val tokens: Int,
    val tiempoMs: Int,
    val fecha: String,
    val revisionManual: Boolean,
    val comentario: String?
)

data class TicketDetalle(
    val ticket: Ticket,
    val solicitante: Usuario?,
    val agente: Usuario?,
    val analisis: AnalisisIa?,
    val adjuntos: List<Adjunto>,
    val historial: List<ItemHistorial>
)

data class Kpis(
    val totalTicketsMes: Int = 0,
    val cumplimientoSla: Double? = null,
    val tiempoMedioSolucion: Int = 0,
    val agentesActivos: Int = 0,
    val agentesTotales: Int = 0,
    val ticketsActivos: Int = 0
)

data class Stat(val nombre: String, val total: Int)

data class EficienciaAgente(
    val id: Int,
    val nombre: String,
    val especialidad: String,
    val carga: Int,
    val asignados: Int,
    val resueltos: Int,
    val enProceso: Int
)

data class Estadisticas(
    val kpis: Kpis,
    val categorias: List<Stat>,
    val agentes: List<EficienciaAgente>
)

data class TablaBD(val tabla: String, val filas: Int?)

data class Respaldo(val nombre: String, val bytes: Long, val fecha: String)

data class Workflow(val id: String, val nombre: String, val activo: Boolean)

data class ModeloIa(val nombre: String, val tamano: Long, val tipo: String, val activo: Boolean)

data class ConfigIa(
    val temperatura: Double = 0.8,
    val numPredict: Int = 512,
    val topP: Double = 0.9
)

data class MensajeChat(
    val id: Int,
    val userId: Int,
    val nombre: String,
    val rol: String,
    val area: String?,
    val mensaje: String,
    val fecha: String
)

data class ContactoPrivado(
    val id: Int,
    val nombre: String,
    val rol: String,
    val area: String?,
    val noLeidos: Int,
    val ultimoMensaje: String,
    val ultimaFecha: String?
)

data class MensajePrivado(
    val id: Int,
    val idEmisor: Int,
    val idReceptor: Int,
    val mensaje: String,
    val leido: Boolean,
    val fecha: String
)

data class RagResultado(
    val id: Int,
    val asunto: String,
    val descripcion: String,
    val estado: String,
    val categoria: String,
    val agente: String,
    val similitud: Double
)

data class Sla(
    val id: Int,
    val nivel: String,
    val color: String,
    val respuestaMin: Int,
    val solucionMin: Int,
    val activo: Boolean
)

data class RecomendacionIa(val agenteId: Int, val nombre: String, val afinidad: Int)

data class TicketPendiente(
    val id: Int,
    val asunto: String,
    val estado: String,
    val categoria: String,
    val prioridad: String,
    val recomendacion: RecomendacionIa?
)

data class AsignacionData(
    val agentes: List<Usuario>,
    val pendientes: List<TicketPendiente>,
    val maxDiario: Int
)
