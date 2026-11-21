package online.sistemahelpdesk.movil.ui.register

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.ApiException
import online.sistemahelpdesk.movil.core.DataProvider
import online.sistemahelpdesk.movil.core.Session
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.ui.common.Dialogos
import online.sistemahelpdesk.movil.ui.common.ThemeSwitch
import online.sistemahelpdesk.movil.ui.login.LoginActivity

/** Port de `register.html` + `js/register.js`: alta de solicitantes. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var nombres: EditText
    private lateinit var apellidos: EditText
    private lateinit var email: EditText
    private lateinit var area: Spinner
    private lateinit var password: EditText
    private lateinit var password2: EditText
    private lateinit var error: TextView
    private lateinit var exito: TextView
    private lateinit var loading: android.widget.LinearLayout
    private lateinit var btnRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        Session.init(this)
        Ui.protegerCapturas(this)

        nombres = findViewById(R.id.nombres)
        apellidos = findViewById(R.id.apellidos)
        email = findViewById(R.id.email)
        area = findViewById(R.id.area)
        password = findViewById(R.id.password)
        password2 = findViewById(R.id.password2)
        error = findViewById(R.id.errorMessage)
        exito = findViewById(R.id.successMessage)
        loading = findViewById(R.id.loadingMessage)
        btnRegister = findViewById(R.id.btnRegister)

        ThemeSwitch.instalar(this, findViewById<SwitchCompat>(R.id.themeCheck))

        area.adapter = ArrayAdapter.createFromResource(
            this, R.array.areas, android.R.layout.simple_spinner_dropdown_item
        )

        findViewById<TextView>(R.id.togglePassword).setOnClickListener { alternar(password, it as TextView) }
        findViewById<TextView>(R.id.togglePassword2).setOnClickListener { alternar(password2, it as TextView) }
        btnRegister.setOnClickListener { registrar() }
        findViewById<TextView>(R.id.goLogin).setOnClickListener { finish() }

        findViewById<TextView>(R.id.badge1).setOnClickListener {
            Dialogos.informacion(this, "Registro Seguro", "El registro crea una cuenta de solicitante y valida los datos antes de dar de alta el usuario.")
        }
        findViewById<TextView>(R.id.badge2).setOnClickListener {
            Dialogos.informacion(this, "Datos Protegidos", "Los datos se envían cifrados y solo se usan para gestionar tus tickets de soporte.")
        }
        findViewById<TextView>(R.id.badge3).setOnClickListener {
            Dialogos.informacion(this, "Contraseña Hasheada", "La contraseña nunca se guarda en texto plano: se almacena con hash en el servidor.")
        }
    }

    private fun alternar(campo: EditText, boton: TextView) {
        val visible = campo.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val pos = campo.selectionEnd
        campo.inputType = if (visible)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (pos >= 0) campo.setSelection(pos)
        boton.text = if (visible) "Mostrar" else "Ocultar"
    }

    private fun registrar() {
        val nom = nombres.text.toString().trim()
        val ape = apellidos.text.toString().trim()
        val mail = email.text.toString().trim()
        val valores = resources.getStringArray(R.array.areas_valores)
        val valorArea = valores.getOrElse(area.selectedItemPosition) { "" }
        val pass = password.text.toString()
        val pass2 = password2.text.toString()

        ocultarMensajes()
        if (nom.isEmpty() || ape.isEmpty() || mail.isEmpty() || valorArea.isEmpty() || pass.isEmpty() || pass2.isEmpty()) {
            mostrarError("Por favor completa todos los campos")
            return
        }
        if (pass != pass2) {
            mostrarError("Las contraseñas no coinciden")
            return
        }
        if (pass.length < 8) {
            mostrarError("La contraseña debe tener al menos 8 caracteres")
            return
        }
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(mail)) {
            mostrarError("Por favor ingresa un correo válido")
            return
        }

        btnRegister.isEnabled = false
        loading.visibility = TextView.VISIBLE
        lifecycleScope.launch {
            try {
                DataProvider.registrar(nom, ape, mail, valorArea, pass)
                exito.text = "¡Registro exitoso! Ahora puedes iniciar sesión."
                exito.visibility = TextView.VISIBLE
                deshabilitarFormulario()
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) finish()
                }, 2000)
            } catch (e: ApiException) {
                mostrarError(e.detalle.ifBlank { "Error al registrar usuario" })
            } catch (e: Exception) {
                mostrarError("Error de conexión. Verifica que el servidor esté corriendo.")
            } finally {
                btnRegister.isEnabled = true
                loading.visibility = TextView.GONE
            }
        }
    }

    private fun deshabilitarFormulario() {
        listOf(nombres, apellidos, email, password, password2).forEach {
            it.isEnabled = false
        }
        area.isEnabled = false
        btnRegister.isEnabled = false
    }

    private fun mostrarError(texto: String) {
        error.text = texto
        error.visibility = TextView.VISIBLE
        error.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun ocultarMensajes() {
        error.visibility = TextView.GONE
        exito.visibility = TextView.GONE
    }
}
