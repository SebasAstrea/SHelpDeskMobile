package online.sistemahelpdesk.movil.ui.common

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Ui

/**
 * Constructores de vistas para las secciones construidas en código
 * (coordinador y administrador), con el mismo lenguaje visual que el CSS web.
 */
object Vistas {

    private val blanco = 0xFFFFFFFF.toInt()
    private const val AUTO = Int.MIN_VALUE

    /** Color de texto por defecto: `text_dark`, que cambia con el tema. */
    fun colorTexto(ctx: Context): Int = ContextCompat.getColor(ctx, R.color.text_dark)

    fun texto(
        ctx: Context,
        valor: String,
        size: Float = 14f,
        color: Int = AUTO,
        negrita: Boolean = false
    ): TextView = TextView(ctx).apply {
        text = valor
        textSize = size
        setTextColor(if (color == AUTO) colorTexto(ctx) else color)
        if (negrita) setTypeface(null, Typeface.BOLD)
    }

    fun fila(ctx: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
        }

    fun columna(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    /** Tarjeta de contenido (`.glass-card`). */
    fun tarjeta(ctx: Context, padding: Int = 14, margenInferior: Int = 10): LinearLayout =
        columna(ctx).apply {
            setBackgroundResource(R.drawable.bg_card)
            val p = Ui.dp(padding)
            setPadding(p, p, p, p)
            (layoutParams ?: LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )).let { lp ->
                if (lp is LinearLayout.LayoutParams) {
                    lp.bottomMargin = Ui.dp(margenInferior)
                    layoutParams = lp
                }
            }
        }

    /** Banner de sección (`h2` + párrafo). */
    fun banner(ctx: Context, titulo: String, subtitulo: String? = null): LinearLayout =
        columna(ctx).apply {
            setBackgroundResource(R.drawable.bg_panel)
            val p = Ui.dp(16)
            setPadding(p, p, p, p)
            addView(texto(ctx, titulo, 18f, blanco, negrita = true))
            subtitulo?.let {
                addView(texto(ctx, it, 13f, 0xD9FFFFFF.toInt()).apply {
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = Ui.dp(4)
                    layoutParams = lp
                })
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = Ui.dp(12)
            layoutParams = lp
        }

    fun boton(ctx: Context, texto: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = texto
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(blanco)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_primary)
            val p = Ui.dp(11)
            setPadding(p, Ui.dp(8), p, Ui.dp(8))
            setOnClickListener { onClick() }
        }

    fun separador(ctx: Context): View = View(ctx).apply {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(1)
        )
        lp.topMargin = Ui.dp(8)
        lp.bottomMargin = Ui.dp(8)
        layoutParams = lp
        setBackgroundResource(R.drawable.bg_divider)
    }

    /** Barra de progreso horizontal (carga de agentes, volumen por categoría). */
    fun barra(ctx: Context, ratio: Float, color: Int, alto: Int = 8): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x33FFFFFF)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(alto)
            )
            lp.topMargin = Ui.dp(3)
            layoutParams = lp
            addView(View(ctx).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, ratio.coerceIn(0f, 1f))
            })
        }

    fun spinner(ctx: Context, opciones: List<String>): Spinner = Spinner(ctx).apply {
        adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, opciones)
    }

    // ------------------------------------------------------------------ tablas
    data class Col(
        val texto: String,
        val peso: Float,
        val color: Int = AUTO,
        val negrita: Boolean = false,
        val size: Float = 13f
    )

    private fun celda(ctx: Context, col: Col): TextView =
        texto(ctx, col.texto, col.size, col.color, col.negrita).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, col.peso)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(4), Ui.dp(2))
        }

    fun cabeceraTabla(ctx: Context, cols: List<Col>): LinearLayout =
        fila(ctx).apply {
            setBackgroundResource(R.drawable.bg_input_soft)
            val p = Ui.dp(8)
            setPadding(p, p, p, p)
            cols.forEach { addView(celda(ctx, it)) }
        }

    fun filaTabla(ctx: Context, cols: List<Col>): LinearLayout =
        fila(ctx).apply {
            val p = Ui.dp(8)
            setPadding(p, p, p, p)
            cols.forEach { addView(celda(ctx, it)) }
            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(1)
                )
                setBackgroundResource(R.drawable.bg_divider)
                gravity = Gravity.BOTTOM
            })
        }

    /** Fila decorativa de tarjeta (lista de items tipo `.item-glass`). */
    fun item(ctx: Context): LinearLayout =
        tarjeta(ctx, padding = 10, margenInferior = 6)

    /** Pill de estado/rol. */
    fun pill(ctx: Context, texto: String, colorFondo: Int, size: Float = 11f): TextView =
        texto(ctx, " $texto ", size, blanco, negrita = true).apply {
            background = Ui.pill(colorFondo)
            Ui.aplicarNeonPill(this)
        }
}
