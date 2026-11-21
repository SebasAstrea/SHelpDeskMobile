package online.sistemahelpdesk.movil.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.sistemahelpdesk.movil.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class LoginResultado(
    val mfaRequerido: Boolean = false,
    val mfaToken: String? = null,
    val sesion: Sesion? = null,
    val token: String? = null
)

/**
 * Fachada de datos: intenta hablar con el backend real y, si no responde o no
 * está conectado, sirve los datos locales de [MockData] con usuarios genéricos.
 *
 * - Lecturas: cualquier fallo cae a datos locales (la pantalla nunca queda vacía).
 * - Escrituras: los errores del backend (4xx) se propagan para mostrarlos; si la
 *   red está caída la acción se aplica sobre los datos locales.
 */
object DataProvider {

    private val token: String? get() = Session.token()

    /** Sesión demo (chips genéricos): se sirve todo desde [MockData] aunque el backend responda. */
    private val sesionLocal: Boolean get() = token?.startsWith(Session.PREFIJO_TOKEN_DEMO) == true

    private suspend fun <T> leer(online: suspend () -> T, local: suspend () -> T): T {
        if (sesionLocal || ApiClient.modo == ApiClient.Modo.SIN_CONEXION) return local()
        return try {
            online()
        } catch (e: SinConexion) {
            local()
        } catch (e: ApiException) {
            local()
        }
    }

    private suspend fun <T> escribir(online: suspend () -> T, local: suspend () -> T): T {
        if (sesionLocal || ApiClient.modo == ApiClient.Modo.SIN_CONEXION) return local()
        return try {
            online()
        } catch (e: SinConexion) {
            local()
        }
    }

    // ------------------------------------------------------------ autenticación
    suspend fun login(email: String, password: String): LoginResultado {
        if (ApiClient.modo != ApiClient.Modo.SIN_CONEXION) {
            try {
                val cuerpo = ApiClient.json(mapOf("email" to email, "password" to password))
                val texto = ApiClient.post("/auth/login", cuerpo, null)
                val o = JSONObject(texto)
                if (o.optBoolean("mfa_required", false)) {
                    return LoginResultado(mfaRequerido = true, mfaToken = o.optString("mfa_token"))
                }
                return LoginResultado(sesion = sesionDeLogin(o), token = accessToken(o))
            } catch (e: SinConexion) {
                // continúa en modo local
            } catch (e: ApiException) {
                // Los chips genéricos (correo demo) no existen en la BD real: si el backend
                // los rechaza, la app entra en modo demostración con los datos locales.
                if (!MockData.esEmailDemo(email)) throw e
            }
        }
        if (password.trim().isEmpty()) throw ApiException(400, "Por favor, completa todos los campos.")
        return LoginResultado(sesion = MockData.sesionDe(MockData.usuarioPorEmail(email)))
    }

    private fun accessToken(o: JSONObject): String? =
        o.optString("access_token").ifEmpty { null }

    private fun sesionDeLogin(o: JSONObject): Sesion {
        val u = o.optJSONObject("user") ?: JSONObject()
        return Sesion(
            userId = u.optInt("id_usuario", u.optInt("id", 0)),
            nombre = u.optString("nombre", u.optString("email", "")),
            email = u.optString("email", ""),
            rol = u.optString("rol", u.optString("role", Roles.SOLICITANTE)),
            area = u.optString("area").ifEmpty { null }
        )
    }

    suspend fun verificarMfa(mfaToken: String, code: String): LoginResultado {
        val cuerpo = ApiClient.json(mapOf("mfa_token" to mfaToken, "code" to code))
        val texto = ApiClient.post("/auth/mfa/verify", cuerpo, null)
        val o = JSONObject(texto)
        return LoginResultado(sesion = sesionDeLogin(o), token = accessToken(o))
    }

    suspend fun authMe(): Sesion = leer(
        online = {
            val texto = ApiClient.get("/auth/me", token)
            val o = JSONObject(texto)
            Sesion(
                userId = o.optInt("user_id", 0),
                nombre = o.optString("nombre", o.optString("email", "")),
                email = o.optString("email", ""),
                rol = o.optString("role", Roles.SOLICITANTE),
                area = o.optString("area").ifEmpty { null },
                mfaEnabled = o.optBoolean("mfa_enabled", false)
            )
        },
        local = {
            Session.sesion() ?: MockData.sesionDe(MockData.usuarioPorEmail("solicitante@demo.helpdesk"))
        }
    )

    suspend fun registrar(
        nombres: String, apellidos: String, email: String, area: String, password: String
    ): String {
        val cuerpo = ApiClient.json(
            mapOf(
                "nombres" to nombres, "apellidos" to apellidos, "email" to email,
                "area" to area, "password" to password
            )
        )
        return escribir(
            online = { ApiClient.post("/auth/register", cuerpo, null) },
            local = { """{"message":"Usuario registrado exitosamente"}""" }
        )
    }

    suspend fun salud(): Boolean = withContext(Dispatchers.IO) { ApiClient.salud() }

    // ---------------------------------------------------------------- tickets
    suspend fun tickets(): List<Ticket> = leer(
        online = { Ticket.lista(JSONArray(ApiClient.get("/tickets", token))) },
        local = {
            val s = Session.sesion()
            MockData.ticketsVisibles(s?.rol ?: Roles.SOLICITANTE, s?.userId ?: 7)
        }
    )

    suspend fun detalleTicket(id: Int): TicketDetalle = leer(
        online = {
            val o = JSONObject(ApiClient.get("/tickets/$id", token))
            parseDetalle(o)
        },
        local = { MockData.detalle(id) ?: throw ApiException(404, "Ticket no encontrado") }
    )

    private fun parseDetalle(o: JSONObject): TicketDetalle {
        fun usuarioDe(clave: String): Usuario? =
            if (o.isNull(clave)) null else Usuario.desdeJson(o.getJSONObject(clave))

        val ia = if (o.isNull("analisis_ia")) null else o.getJSONObject("analisis_ia").let {
            AnalisisIa(
                prioridadIa = it.optString("prioridad_ia", ""),
                categoriaIa = it.optString("categoria_ia", ""),
                confianza = it.optDouble("confianza", 0.0),
                modelo = it.optString("modelo_ia", ""),
                tokens = it.optInt("tokens_usados", 0),
                tiempoMs = it.optInt("tiempo_ejecucion_ms", 0),
                fecha = it.optString("fecha_clasificacion", ""),
                revisionManual = it.optBoolean("revision_manual", false),
                comentario = it.optString("comentario_revision").ifEmpty { null }
            )
        }
        val adjuntos = o.optJSONArray("adjuntos")?.let { arr ->
            (0 until arr.length()).map {
                val a = arr.getJSONObject(it)
                Adjunto(
                    id = a.optInt("id_adjunto", 0),
                    nombre = a.optString("nombre_archivo", ""),
                    tipo = a.optString("tipo", ""),
                    tamano = a.optLong("tamaño", 0L),
                    fecha = a.optString("fecha_subida", "")
                )
            }
        } ?: emptyList()
        val historial = o.optJSONArray("historial")?.let { arr ->
            (0 until arr.length()).map {
                val h = arr.getJSONObject(it)
                ItemHistorial(
                    anterior = h.optString("estado_anterior", ""),
                    nuevo = h.optString("estado_nuevo", ""),
                    fecha = h.optString("fecha", "")
                )
            }
        } ?: emptyList()

        return TicketDetalle(
            ticket = Ticket.desdeJson(o.getJSONObject("ticket")),
            solicitante = usuarioDe("solicitante"),
            agente = usuarioDe("agente_asignado"),
            analisis = ia, adjuntos = adjuntos, historial = historial
        )
    }

    suspend fun actualizarTicket(id: Int, estado: String?, solucion: String?): Unit =
        escribir(
            online = {
                val body = mutableMapOf<String, Any?>()
                estado?.let { body["estado"] = it }
                solucion?.let { body["solucion"] = it }
                ApiClient.patch("/tickets/$id", ApiClient.json(body), token)
            },
            local = {
                MockData.actualizar(id, estado, solucion)
                Unit
            }
        )

    suspend fun crearTicket(
        asunto: String, descripcion: String, idCategoria: Int, idPrioridad: Int
    ): Int = escribir(
        online = {
            val cuerpo = ApiClient.json(
                mapOf(
                    "asunto" to asunto, "descripcion" to descripcion,
                    "id_categoria" to idCategoria, "id_prioridad" to idPrioridad
                )
            )
            val o = JSONObject(ApiClient.post("/tickets", cuerpo, token))
            o.optInt("ticket_id", 0)
        },
        local = {
            MockData.crear(
                asunto, descripcion, idCategoria, idPrioridad,
                Session.sesion()?.userId ?: 7
            )
        }
    )

    suspend fun subirAdjunto(ticketId: Int, nombre: String, bytes: ByteArray, mime: String): Boolean =
        escribir(
            online = {
                val boundary = "----HelpDesk" + System.currentTimeMillis()
                val url = URL("${ApiClient.BASE}/tickets/$ticketId/adjuntos")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Authorization", "Bearer ${token ?: ""}")
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.outputStream.use { out ->
                    fun linea(s: String) = out.write((s + "\r\n").toByteArray())
                    linea("--$boundary")
                    linea("Content-Disposition: form-data; name=\"files\"; filename=\"$nombre\"")
                    linea("Content-Type: $mime")
                    linea("")
                    out.write(bytes)
                    linea("")
                    linea("--$boundary--")
                }
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            },
            local = { true }
        )

    // ----------------------------------------------------------- coordinador
    suspend fun estadisticas(): Estadisticas = leer(
        online = {
            val o = JSONObject(ApiClient.get("/coordinator/estadisticas", token))
            val k = o.optJSONObject("kpis") ?: JSONObject()
            val cats = o.optJSONArray("categorias")?.let { arr ->
                (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    Stat(c.optString("categoria", ""), c.optInt("total", 0))
                }
            } ?: emptyList()
            val efic = o.optJSONArray("agentes_eficiencia")?.let { arr ->
                (0 until arr.length()).map {
                    val c = arr.getJSONObject(it)
                    EficienciaAgente(
                        id = c.optInt("id_usuario", 0), nombre = c.optString("nombre", ""),
                        especialidad = c.optString("especialidad", ""), carga = c.optInt("carga_trabajo", 0),
                        asignados = c.optInt("asignados", 0), resueltos = c.optInt("resueltos", 0),
                        enProceso = c.optInt("en_proceso", 0)
                    )
                }
            } ?: emptyList()
            Estadisticas(
                kpis = Kpis(
                    totalTicketsMes = k.optInt("total_tickets_mes", 0),
                    cumplimientoSla = if (k.isNull("cumplimiento_sla")) null else k.optDouble("cumplimiento_sla"),
                    tiempoMedioSolucion = k.optInt("tiempo_medio_solucion_min", 0),
                    agentesActivos = k.optInt("agentes_activos", 0),
                    agentesTotales = k.optInt("agentes_totales", 0),
                    ticketsActivos = k.optInt("tickets_activos", 0)
                ),
                categorias = cats, agentes = efic
            )
        },
        local = { MockData.estadisticas() }
    )

    data class FiltrosReporte(
        val desde: String, val hasta: String, val categoria: String,
        val prioridad: String, val estado: String
    )

    suspend fun reportes(f: FiltrosReporte): List<Ticket> = leer(
        online = {
            val q = "?fecha_desde=${f.desde}&fecha_hasta=${f.hasta}&categoria=${f.categoria}" +
                "&prioridad=${f.prioridad}&estado=${f.estado}"
            Ticket.lista(JSONArray(ApiClient.get("/coordinator/reportes$q", token)))
        },
        local = {
            MockData.tickets.filter { t ->
                (f.categoria == "todas" || t.catNombre == f.categoria) &&
                    (f.prioridad == "todas" || t.prioNivel.equals(f.prioridad, true) ||
                        t.prioNivel.equals(normaliza(f.prioridad), true)) &&
                    (f.estado == "todos" || t.estado == f.estado)
            }
        }
    )

    private fun normaliza(p: String): String = when (p.lowercase()) {
        "critica", "crítica" -> Prioridades.CRITICA
        "alta" -> Prioridades.ALTA
        "media" -> Prioridades.MEDIA
        else -> Prioridades.BAJA
    }

    suspend fun asignacion(): AsignacionData = leer(
        online = {
            val o = JSONObject(ApiClient.get("/coordinator/asignacion", token))
            val agentes = o.optJSONArray("agentes")?.let { arr ->
                (0 until arr.length()).map { Usuario.desdeJson(arr.getJSONObject(it)) }
            } ?: emptyList()
            val pend = o.optJSONArray("sin_asignar")?.let { arr ->
                (0 until arr.length()).map {
                    val p = arr.getJSONObject(it)
                    val rec = if (p.isNull("recomendacion")) null else {
                        val r = p.getJSONObject("recomendacion")
                        RecomendacionIa(r.optInt("id_usuario", 0), r.optString("nombre", ""), r.optInt("afinidad", 0))
                    }
                    TicketPendiente(
                        id = p.optInt("id_solicitud", 0), asunto = p.optString("asunto", ""),
                        estado = p.optString("estado", ""), categoria = p.optString("categoria", ""),
                        prioridad = p.optString("prioridad", ""), recomendacion = rec
                    )
                }
            } ?: emptyList()
            AsignacionData(agentes, pend, o.optInt("max_diario", 3))
        },
        local = { MockData.asignacion() }
    )

    suspend fun asignarTicket(ticketId: Int, agenteId: Int): Unit = escribir(
        online = {
            ApiClient.post("/coordinator/asignar/$ticketId", ApiClient.json(mapOf("agente_id" to agenteId)), token)
        },
        local = {
            MockData.asignar(ticketId, agenteId)
            Unit
        }
    )

    suspend fun balanceoIa(): Unit = escribir(
        online = { ApiClient.post("/coordinator/asignacion/balanceo", "{}", token) },
        local = {
            val data = MockData.asignacion()
            data.pendientes.forEach { p ->
                p.recomendacion?.let { MockData.asignar(p.id, it.agenteId) }
            }
            Unit
        }
    )

    suspend fun agentes(): List<Usuario> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/agentes", token))
            (0 until arr.length()).map { Usuario.desdeJson(arr.getJSONObject(it)) }
        },
        local = { MockData.usuarios.filter { it.rol == Roles.AGENTE } }
    )

    suspend fun guardarPermisos(id: Int, supervision: Boolean, especiales: Boolean): Unit = escribir(
        online = {
            ApiClient.post(
                "/coordinator/agentes/$id/permisos",
                ApiClient.json(
                    mapOf(
                        "permisos_supervision" to supervision,
                        "permisos_especiales" to especiales
                    )
                ), token
            )
        },
        local = {
            MockData.guardarPermisos(id, supervision, especiales)
            Unit
        }
    )

    suspend fun sla(): List<Sla> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/sla", token))
            (0 until arr.length()).map {
                val s = arr.getJSONObject(it)
                Sla(
                    id = s.optInt("id_prioridad", it + 1), nivel = s.optString("nivel", ""),
                    color = s.optString("color", ""), respuestaMin = s.optInt("tiempo_respuesta_min", 0),
                    solucionMin = s.optInt("tiempo_solucion_min", 0), activo = s.optBoolean("activo", true)
                )
            }
        },
        local = { MockData.slas.toList() }
    )

    suspend fun guardarSla(lista: List<Sla>): Unit = escribir(
        online = {
            val arr = JSONArray()
            lista.forEach {
                arr.put(
                    JSONObject()
                        .put("id_prioridad", it.id)
                        .put("tiempo_respuesta_min", it.respuestaMin)
                        .put("tiempo_solucion_min", it.solucionMin)
                        .put("activo", it.activo)
                )
            }
            ApiClient.post("/coordinator/sla", """{"sla":${arr}}""", token)
        },
        local = {
            MockData.guardarSla(lista)
            Unit
        }
    )

    suspend fun rag(consulta: String): List<RagResultado> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/rag?query=${java.net.URLEncoder.encode(consulta, "UTF-8")}", token))
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                RagResultado(
                    id = r.optInt("id_solicitud", 0), asunto = r.optString("asunto", ""),
                    descripcion = r.optString("descripcion", ""), estado = r.optString("estado", ""),
                    categoria = r.optString("categoria", ""), agente = r.optString("agente", ""),
                    similitud = r.optDouble("similitud", 0.0)
                )
            }
        },
        local = { MockData.rag(consulta) }
    )

    suspend fun indexarRag(): Unit = escribir(
        online = { ApiClient.post("/coordinator/rag/indexar", "{}", token) },
        local = { }
    )

    // ------------------------------------------------------------ usuarios
    suspend fun usuarios(): List<Usuario> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/usuarios", token))
            (0 until arr.length()).map { Usuario.desdeJson(arr.getJSONObject(it)) }
        },
        local = { MockData.usuarios.toList() }
    )

    suspend fun crearUsuario(
        nombre: String, email: String, area: String, rol: String, password: String
    ): Unit = escribir(
        online = {
            ApiClient.post(
                "/coordinator/usuarios",
                ApiClient.json(
                    mapOf(
                        "nombre" to nombre, "email" to email, "area" to area,
                        "rol" to rol, "password" to password
                    )
                ), token
            )
        },
        local = {
            MockData.usuarios += Usuario(
                id = (MockData.usuarios.maxOfOrNull { it.id } ?: 0) + 1,
                nombre = nombre, email = email, rol = rol, area = area, estado = "activo"
            )
            Unit
        }
    )

    suspend fun cambiarRol(id: Int, rol: String, temporalHoras: Int?): Unit = escribir(
        online = {
            ApiClient.patch(
                "/coordinator/usuarios/$id/rol",
                ApiClient.json(mapOf("rol" to rol, "temporal_horas" to temporalHoras)), token
            )
        },
        local = {
            val i = MockData.usuarios.indexOfFirst { it.id == id }
            if (i >= 0) MockData.usuarios[i] = MockData.usuarios[i].copy(rol = rol)
            Unit
        }
    )

    suspend fun cambiarEstado(id: Int, estado: String): Unit = escribir(
        online = { ApiClient.patch("/coordinator/usuarios/$id", ApiClient.json(mapOf("estado" to estado)), token) },
        local = {
            val i = MockData.usuarios.indexOfFirst { it.id == id }
            if (i >= 0) MockData.usuarios[i] = MockData.usuarios[i].copy(estado = estado)
            Unit
        }
    )

    suspend fun restablecerPassword(id: Int, password: String): Unit = escribir(
        online = {
            ApiClient.patch("/coordinator/usuarios/$id", ApiClient.json(mapOf("password" to password)), token)
        },
        local = { }
    )

    suspend fun eliminarUsuario(id: Int): Unit = escribir(
        online = { ApiClient.delete("/coordinator/usuarios/$id", token) },
        local = {
            MockData.usuarios.removeAll { it.id == id }
            Unit
        }
    )

    // ---------------------------------------------------------- administración
    suspend fun tablasBD(): List<TablaBD> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/bd/tablas", token))
            (0 until arr.length()).map {
                val t = arr.getJSONObject(it)
                TablaBD(t.optString("tabla", ""), if (t.isNull("filas")) null else t.optInt("filas"))
            }
        },
        local = { MockData.tablasBD }
    )

    suspend fun respaldos(): List<Respaldo> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/respaldos", token))
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                Respaldo(r.optString("nombre", ""), r.optLong("bytes", 0), r.optString("fecha", ""))
            }
        },
        local = { MockData.respaldos.toList() }
    )

    suspend fun crearRespaldo(): Unit = escribir(
        online = { ApiClient.post("/coordinator/respaldos", "{}", token) },
        local = {
            MockData.respaldos.add(
                0, Respaldo(
                    "helpdesk_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date()) + ".dump",
                    5_120_000,
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                )
            )
            Unit
        }
    )

    suspend fun eliminarRespaldo(nombre: String): Unit = escribir(
        online = { ApiClient.delete("/coordinator/respaldos/$nombre", token) },
        local = {
            MockData.respaldos.removeAll { it.nombre == nombre }
            Unit
        }
    )

    suspend fun restaurarRespaldo(nombre: String): Unit = escribir(
        online = { ApiClient.post("/coordinator/respaldos/$nombre/restaurar", "{}", token) },
        local = { }
    )

    suspend fun logs(contenedor: String, lineas: Int): String = leer(
        online = { JSONObject(ApiClient.get("/coordinator/logs/$contenedor?tail=$lineas", token)).optString("logs", "") },
        local = { MockData.logs(contenedor, lineas) }
    )

    suspend fun workflows(): List<Workflow> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/coordinator/n8n/workflows", token))
            (0 until arr.length()).map {
                val w = arr.getJSONObject(it)
                Workflow((w.opt("id") ?: "").toString(), w.optString("nombre", ""), w.optBoolean("activo", false))
            }
        },
        local = { MockData.workflows.toList() }
    )

    suspend fun toggleWorkflow(id: String, activo: Boolean): Unit = escribir(
        online = {
            ApiClient.post("/coordinator/n8n/workflows/$id/toggle", ApiClient.json(mapOf("activo" to activo)), token)
        },
        local = {
            val i = MockData.workflows.indexOfFirst { it.id == id }
            if (i >= 0) MockData.workflows[i] = MockData.workflows[i].copy(activo = activo)
            Unit
        }
    )

    suspend fun ia(): Pair<List<ModeloIa>, ConfigIa> = leer(
        online = {
            val o = JSONObject(ApiClient.get("/coordinator/ia", token))
            val activo = o.optString("modelo_activo", "")
            val arr = o.optJSONArray("modelos") ?: JSONArray()
            val modelos = (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                ModeloIa(
                    nombre = m.optString("name", ""), tamano = m.optLong("size", 0),
                    tipo = m.optString("tipo", "chat"), activo = m.optString("name", "") == activo
                )
            }
            val cfg = o.optJSONObject("config") ?: JSONObject()
            Pair(
                modelos,
                ConfigIa(
                    temperatura = cfg.optDouble("temperatura", 0.8),
                    numPredict = cfg.optInt("num_predict", 512),
                    topP = cfg.optDouble("top_p", 0.9)
                )
            )
        },
        local = { Pair(MockData.modelosIa.toList(), MockData.configIa) }
    )

    suspend fun usarModelo(modelo: String): Unit = escribir(
        online = { ApiClient.post("/coordinator/ia/modelo", ApiClient.json(mapOf("modelo" to modelo)), token) },
        local = {
            val i = MockData.modelosIa.indexOfFirst { it.nombre == modelo }
            if (i >= 0) {
                MockData.modelosIa.replaceAll { it.copy(activo = it.nombre == modelo) }
            }
            Unit
        }
    )

    suspend fun probarModelo(modelo: String): String = escribir(
        online = {
            val o = JSONObject(ApiClient.post("/coordinator/ia/probar", ApiClient.json(mapOf("modelo" to modelo)), token))
            o.optString("respuesta", "—")
        },
        local = { "Conexión correcta con $modelo. Listo para clasificar tickets (demo local)." }
    )

    suspend fun pullModelo(modelo: String): Unit = escribir(
        online = { ApiClient.post("/coordinator/ia/pull", ApiClient.json(mapOf("modelo" to modelo)), token) },
        local = {
            MockData.modelosIa += ModeloIa(modelo, 1_500_000_000, "chat", false)
            Unit
        }
    )

    suspend fun guardarParamsIa(temp: Double, predict: Int, topP: Double): Unit = escribir(
        online = {
            ApiClient.post(
                "/coordinator/ia/params",
                ApiClient.json(mapOf("temperatura" to temp, "num_predict" to predict, "top_p" to topP)),
                token
            )
        },
        local = {
            MockData.configIa = ConfigIa(temp, predict, topP)
            Unit
        }
    )

    // ----------------------------------------------------------------- 2FA
    suspend fun mfaSetup(): Triple<String, String, String> {
        val o = JSONObject(ApiClient.post("/auth/mfa/setup", "{}", token))
        return Triple(o.optString("secret", ""), o.optString("otpauth_uri", ""), o.optString("qr", ""))
    }

    suspend fun mfaEnable(code: String): Boolean {
        val o = JSONObject(ApiClient.post("/auth/mfa/enable", ApiClient.json(mapOf("code" to code)), token))
        return o.optBoolean("mfa_enabled", true)
    }

    suspend fun mfaDisable(code: String): Boolean {
        val o = JSONObject(ApiClient.post("/auth/mfa/disable", ApiClient.json(mapOf("code" to code)), token))
        return !o.optBoolean("mfa_enabled", true)
    }

    // ----------------------------------------------------------------- chat
    suspend fun chatIa(mensaje: String, historial: List<Pair<String, String>>, ticketId: Int?): Pair<String, String> {
        // Cada par es (mensaje del usuario, respuesta del asistente): se expande a dos
        // entradas con `role` explícito, que es lo que valida el backend
        // (ChatMessage.role: Literal["user", "assistant"], máximo 20 mensajes).
        // El mensaje actual NO se incluye: el backend lo añade como último turno `user`.
        val arr = JSONArray()
        historial.takeLast(10).forEach { (pregunta, respuesta) ->
            arr.put(JSONObject().put("role", "user").put("content", pregunta))
            arr.put(JSONObject().put("role", "assistant").put("content", respuesta))
        }
        // `modelo` no se envía: el servidor resuelve el modelo activo y filtra por denylist.
        val body = mutableMapOf<String, Any?>(
            "mensaje" to mensaje, "historial" to arr
        )
        ticketId?.let { body["ticket_id"] = it }
        if (ApiClient.modo == ApiClient.Modo.SIN_CONEXION) {
            return Pair(MockData.respuestaIa(mensaje), "demo-local")
        }
        return try {
            val o = JSONObject(ApiClient.post("/chat", ApiClient.json(body), token))
            val meta = o.optJSONObject("tokens")
            Pair(
                o.optString("respuesta", "—"),
                o.optString("modelo", "llama3.2:3b") +
                    (meta?.let { " • ${it.optInt("tokens_generados", 0)} tokens • ${it.optInt("tiempo_total_ms", 0)}ms" } ?: "")
            )
        } catch (e: SinConexion) {
            Pair(MockData.respuestaIa(mensaje), "demo-local")
        }
    }

    suspend fun chatGlobal(sinceId: Int = 0): List<MensajeChat> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/chat-global/mensajes?since_id=$sinceId&limit=50", token))
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                MensajeChat(
                    id = m.optInt("id_mensaje", i), userId = m.optInt("user_id", 0),
                    nombre = m.optString("nombre", ""), rol = m.optString("rol", ""),
                    area = m.optString("area").ifEmpty { null }, mensaje = m.optString("mensaje", ""),
                    fecha = m.optString("fecha", "")
                )
            }
        },
        local = { MockData.chatGlobal.toList() }
    )

    suspend fun enviarGlobal(mensaje: String): MensajeChat = escribir(
        online = {
            val s = Session.sesion()
            val o = JSONObject(
                ApiClient.post("/chat-global/mensajes", ApiClient.json(mapOf("mensaje" to mensaje)), token)
            )
            MensajeChat(
                id = o.optInt("id_mensaje", 0), userId = o.optInt("user_id", s?.userId ?: 0),
                nombre = o.optString("nombre", s?.nombre ?: ""), rol = o.optString("rol", s?.rol ?: ""),
                area = o.optString("area").ifEmpty { s?.area }, mensaje = o.optString("mensaje", mensaje),
                fecha = o.optString("fecha", Ui.ahoraIso())
            )
        },
        local = {
            val s = Session.sesion()
            MockData.enviarGlobal(s?.userId ?: 7, s?.nombre ?: "Tú", s?.rol ?: Roles.SOLICITANTE, s?.area, mensaje)
        }
    )

    suspend fun contactos(): List<ContactoPrivado> = leer(
        online = {
            val arr = JSONArray(ApiClient.get("/chat-privado/contactos", token))
            (0 until arr.length()).map {
                val c = arr.getJSONObject(it)
                ContactoPrivado(
                    id = c.optInt("id_usuario", 0), nombre = c.optString("nombre", ""),
                    rol = c.optString("rol", ""), area = c.optString("area").ifEmpty { null },
                    noLeidos = c.optInt("no_leidos", 0),
                    ultimoMensaje = c.optString("ultimo_mensaje", ""),
                    ultimaFecha = c.optString("ultima_fecha").ifEmpty { null }
                )
            }
        },
        local = { MockData.contactos }
    )

    suspend fun conversacion(contactoId: Int, sinceId: Int = 0): List<MensajePrivado> = leer(
        online = {
            val arr = JSONArray(
                ApiClient.get("/chat-privado/conversacion/$contactoId?since_id=$sinceId&limit=50", token)
            )
            (0 until arr.length()).map {
                val m = arr.getJSONObject(it)
                MensajePrivado(
                    id = m.optInt("id_mensaje", 0), idEmisor = m.optInt("id_emisor", 0),
                    idReceptor = m.optInt("id_receptor", 0), mensaje = m.optString("mensaje", ""),
                    leido = m.optBoolean("leido", false), fecha = m.optString("fecha", "")
                )
            }
        },
        local = { MockData.conversacion(contactoId) }
    )

    suspend fun enviarPrivada(contactoId: Int, mensaje: String): MensajePrivado = escribir(
        online = {
            val o = JSONObject(
                ApiClient.post("/chat-privado/conversacion/$contactoId", ApiClient.json(mapOf("mensaje" to mensaje)), token)
            )
            MensajePrivado(
                id = o.optInt("id_mensaje", 0), idEmisor = Session.sesion()?.userId ?: 0,
                idReceptor = contactoId, mensaje = o.optString("mensaje", mensaje),
                leido = false, fecha = o.optString("fecha", Ui.ahoraIso())
            )
        },
        local = { MockData.enviarPrivada(contactoId, Session.sesion()?.userId ?: 8, mensaje) }
    )

    suspend fun noLeidosPrivados(): Int = leer(
        online = { JSONObject(ApiClient.get("/chat-privado/no-leidos", token)).optInt("total", 0) },
        local = { MockData.noLeidosPrivados() }
    )
}
