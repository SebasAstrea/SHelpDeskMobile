package online.sistemahelpdesk.movil.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import online.sistemahelpdesk.movil.R
import online.sistemahelpdesk.movil.core.Ui
import online.sistemahelpdesk.movil.data.ContactoPrivado

/** Fila del hilo de chat: burbuja propia / ajena / sistema + línea de meta. */
data class ItemChat(val texto: String, val meta: String, val tipo: Int) {
    companion object {
        const val PROPIO = 0
        const val AJENO = 1
        const val SISTEMA = 2
    }
}

/** Adaptador común a los tres hilos (IA, global y privado). */
class MsgAdapter(private val items: MutableList<ItemChat>) : RecyclerView.Adapter<MsgAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val root: LinearLayout = v as LinearLayout
        val bubble: TextView = v.findViewById(R.id.bubble)
        val meta: TextView = v.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_mensaje, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val ctx = holder.root.context
        holder.bubble.text = item.texto
        holder.meta.text = item.meta
        holder.meta.visibility = if (item.meta.isBlank()) View.GONE else View.VISIBLE
        val (fondo, gravedad) = when (item.tipo) {
            ItemChat.PROPIO -> R.drawable.bg_bubble_right to Gravity.END
            ItemChat.SISTEMA -> R.drawable.bg_bubble_system to Gravity.CENTER
            else -> R.drawable.bg_bubble_left to Gravity.START
        }
        holder.root.gravity = gravedad
        holder.bubble.background = ContextCompat.getDrawable(ctx, fondo)
    }

    fun agregar(item: ItemChat) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun reemplazar(nuevos: List<ItemChat>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }
}

/** Contactos del chat privado (avatar con iniciales + badge de no leídos). */
class ContactoAdapter(
    private val items: MutableList<ContactoPrivado>,
    private val onClick: (ContactoPrivado) -> Unit
) : RecyclerView.Adapter<ContactoAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.avatar)
        val nombre: TextView = v.findViewById(R.id.nombre)
        val subtitulo: TextView = v.findViewById(R.id.subtitulo)
        val noLeidos: TextView = v.findViewById(R.id.noLeidos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_contacto, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = items[position]
        holder.avatar.text = Ui.iniciales(c.nombre)
        holder.avatar.background = Ui.pill(Ui.colorDeNombre(c.nombre))
        holder.nombre.text = c.nombre
        holder.subtitulo.text = buildString {
            append(c.ultimoMensaje.ifBlank { "Sin mensajes" })
            c.ultimaFecha?.let { append(" · ").append(Ui.hora(it)) }
        }
        if (c.noLeidos > 0) {
            holder.noLeidos.visibility = View.VISIBLE
            holder.noLeidos.text = c.noLeidos.toString()
        } else {
            holder.noLeidos.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onClick(c) }
    }

    fun reemplazar(nuevos: List<ContactoPrivado>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }
}
