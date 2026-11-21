package online.sistemahelpdesk.movil.ui.login

import android.os.Bundle
import android.text.InputType
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.ApiException
import online.sistemahelpdesk.movil.core.ApiClient
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.Sesion
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.Navegacion
import online.sistemahelpdesk.movil.ui.common.ThemeSwitch

/**
 * Port de `login.html` + `js/login.js`: credenciales, segundo paso MFA,
 * recordarme, badges de estado y acceso con usuarios genéricos.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var campoEmail: EditText
    private lateinit var campoPassword: EditText
    private lateinit var groupPassword: android.view.View
    private lateinit var togglePassword: TextView
    private lateinit var btnLogin: TextView
    private lateinit var mfaSection: LinearLayout
    private lateinit var campoMfa: EditText
    private lateinit var btnMfa: TextView
    private lateinit var errorMessage: TextView
    private lateinit var loadingMessage: LinearLayout
    private lateinit var remember: android.widget.CheckBox

    private var mfaToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        Ui.protegerCapturas(this)
        Session.init(this)

        campoEmail = findViewById(R.id.email)
        campoPassword = findViewById(R.id.password)
        groupPassword = findViewById(R.id.groupPassword)
        togglePassword = findViewById(R.id.togglePassword)
        btnLogin = findViewById(R.id.btnLogin)
        mfaSection = findViewById(R.id.mfaSection)
        campoMfa = findViewById(R.id.mfaCode)
        btnMfa = findViewById(R.id.btnMfaVerify)
        errorMessage = findViewById(R.id.errorMessage)
        loadingMessage = findViewById(R.id.loadingMessage)
        remember = findViewById(R.id.remember)

        ThemeSwitch.instalar(this, findViewById<SwitchCompat>(R.id.themeCheck))

        Session.emailRecordado?.let {
            campoEmail.setText(it)
            remember.isChecked = true
        }

        togglePassword.setOnClickListener { alternarPassword() }
        btnLogin.setOnClickListener { intentarLogin() }
        btnMfa.setOnClickListener { verificarMfa() }
        campoMfa.setOnEditorActionListener { _, _, _ -> verificarMfa(); true }
        findViewById<TextView>(R.id.goRegister).setOnClickListener {
            startActivity(android.content.Intent(this, online.sistemahelpdesk.movil.ui.register.RegisterActivity::class.java))
        }
        findViewById<TextView>(R.id.forgot).setOnClickListener {
            Dialogos.informacion(
                this, "Recuperar contraseña",
                "Contacta al equipo de HelpDesk o al administrador del sistema para restablecer tu contraseña."
            )
        }
        configurarBadges()
        demo()
        restaurarSesion()
        comprobarSalud()
    }

    private fun demo() {
        val pares = listOf(
            R.id.demoSolicitante to "solicitante@demo.helpdesk",
            R.id.demoAgente to "agente@demo.helpdesk",
            R.id.demoCoordinador to "coordinador@demo.helpdesk",
            R.id.demoAdmin to "admin@demo.helpdesk"
        )
        pares.forEach { (id, correo) ->
            findViewById<TextView>(id).setOnClickListener {
                campoEmail.setText(correo)
                campoPassword.setText("demo1234")
                remember.isChecked = true
                intentarLogin()
            }
        }
    }

    private fun alternarPassword() {
        val enTexto = campoPassword.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val seleccion = campoPassword.selectionEnd
        campoPassword.inputType = if (enTexto)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (seleccion >= 0) campoPassword.setSelection(seleccion)
        togglePassword.text = if (enTexto) "Mostrar" else "Ocultar"
    }

    // ------------------------------------------------------------------ login
    private fun intentarLogin() {
        val email = campoEmail.text.toString().trim()
        val password = campoPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            mostrarError("Por favor, completa todos los campos.")
            return
        }
        ocultarError()
        btnLogin.isEnabled = false
        loadingMessage.visibility = TextView.VISIBLE

        lifecycleScope.launch {
            try {
                val r = DataProvider.login(email, password)
                if (r.mfaRequerido) {
                    mfaToken = r.mfaToken
                    mostrarPasoMfa()
                } else {
                    if (remember.isChecked) Session.emailRecordado = email
                    exito(r.sesion!!, r.token)
                }
            } catch (e: ApiException) {
                mostrarError(e.detalle)
            } catch (e: Exception) {
                mostrarError("Error de conexión. Por favor, verifica si el servidor está en funcionamiento.")
            } finally {
                btnLogin.isEnabled = true
                loadingMessage.visibility = TextView.GONE
            }
        }
    }

    private fun verificarMfa() {
        val token = mfaToken
        val code = campoMfa.text.toString().trim()
        if (!code.matches(Regex("\\d{6}"))) {
            mostrarError("Introduce el código de 6 dígitos.")
            return
        }
        if (token == null) {
            mostrarError("Reto 2FA inválido o expirado.")
            return
        }
        ocultarError()
        btnMfa.isEnabled = false
        loadingMessage.visibility = TextView.VISIBLE
        lifecycleScope.launch {
            try {
                val r = DataProvider.verificarMfa(token, code)
                exito(r.sesion!!, r.token)
            } catch (e: ApiException) {
                mostrarError(e.detalle)
            } catch (e: Exception) {
                mostrarError("Error de conexión con el servidor.")
            } finally {
                btnMfa.isEnabled = true
                loadingMessage.visibility = TextView.GONE
            }
        }
    }

    private fun exito(sesion: Sesion, tokenReal: String? = null) {
        val token = tokenReal ?: (Session.PREFIJO_TOKEN_DEMO + sesion.userId)
        Session.guardarToken(token, remember.isChecked)
        Session.guardarUsuario(sesion, remember.isChecked)
        mostrarExito("Inicio exitoso, redirigiendo...")
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                Navegacion.irADashboard(this, sesion.rol)
            }
        }, 800)
    }

    private fun restaurarSesion() {
        if (Session.token().isNullOrBlank() && Session.sesion() == null) return
        val guardada = Session.sesion() ?: return
        lifecycleScope.launch {
            try {
                val me = DataProvider.authMe()
                Session.guardarUsuario(me, true)
                Navegacion.irADashboard(this@LoginActivity, me.rol)
            } catch (e: Exception) {
                // sin backend y sin sesión local: se queda en login
                if (guardada.rol.isNotBlank()) {
                    Navegacion.irADashboard(this@LoginActivity, guardada.rol)
                }
            }
        }
    }

    // --------------------------------------------------------------- mensajes
    private fun mostrarError(texto: String) {
        errorMessage.text = texto
        errorMessage.visibility = TextView.VISIBLE
        errorMessage.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun ocultarError() {
        errorMessage.visibility = TextView.GONE
    }

    private fun mostrarExito(texto: String) {
        errorMessage.setBackgroundResource(R.drawable.bg_msg_success)
        errorMessage.setTextColor(getColor(R.color.success_text))
        errorMessage.text = texto
        errorMessage.visibility = TextView.VISIBLE
    }

    private fun mostrarPasoMfa() {
        findViewById<android.view.View>(R.id.email).visibility = TextView.GONE
        findViewById<android.view.View>(R.id.groupPassword).visibility = TextView.GONE
        btnLogin.visibility = TextView.GONE
        mfaSection.visibility = TextView.VISIBLE
        campoMfa.requestFocus()
    }

    // ----------------------------------------------------------------- badges
    private fun configurarBadges() {
        val descSistema = getString(R.string.desc_sistema)
        val descOffline = getString(R.string.desc_sistema_offline)
        val badge = findViewById<TextView>(R.id.badgeSistema)
        badge.setOnClickListener {
            val estado = badge.text.toString()
            Dialogos.informacion(
                this,
                if (estado.contains("fuera")) "Sistema fuera de línea" else "Sistemas en línea",
                if (estado.contains("fuera")) descOffline else descSistema
            )
        }
        findViewById<TextView>(R.id.badgeIa).setOnClickListener {
            Dialogos.informacion(
                this, "IA Local",
                "La clasificación de tickets y el chat usan un modelo de IA que se ejecuta en la propia infraestructura: los datos no se envían a servicios de terceros."
            )
        }
        findViewById<TextView>(R.id.badgeSla).setOnClickListener {
            Dialogos.informacion(
                this, "SLA por Prioridad",
                "Cada ticket se atiende según su prioridad y los tiempos de SLA configurados, con seguimiento hasta su resolución."
            )
        }
    }

    private fun comprobarSalud() {
        val badge = findViewById<TextView>(R.id.badgeSistema)
        lifecycleScope.launch {
            val ok = DataProvider.salud()
            if (isFinishing || isDestroyed) return@launch
            if (ok) {
                badge.setBackgroundResource(R.drawable.bg_badge_green)
                badge.text = "Sistemas en línea"
                badge.setTextColor(getColor(R.color.white))
            } else {
                badge.setBackgroundResource(R.drawable.bg_badge_red)
                badge.text = "Sistema fuera de línea"
                badge.setTextColor(getColor(R.color.white))
                Ui.toast(this@LoginActivity, "Sin conexión con el backend: modo demostración")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeSwitch.actualizarEtiqueta(findViewById(R.id.themeCheck))
        if (ApiClient.modo == ApiClient.Modo.SIN_CONEXION) comprobarSalud()
    }
}
