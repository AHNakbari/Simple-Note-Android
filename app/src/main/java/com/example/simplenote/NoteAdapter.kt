package com.example.simplenote

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.simplenote.core.Note

class NoteAdapter(
    private var notes: ArrayList<Note> = ArrayList(),
    private val onNoteClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.noteTitle)
        val content: TextView = itemView.findViewById(R.id.noteContent)
        val image: ImageView = itemView.findViewById(R.id.noteImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.title.text = note.title
        holder.content.text = note.content
        holder.itemView.setBackgroundColor(transformColor(note.color))
        holder.itemView.setOnClickListener { onNoteClick(note) }
        if (note.isPinned) holder.image.visibility = ImageView.VISIBLE
    }

    override fun getItemCount(): Int = notes.size

    fun clear() {
        notes.clear()
        notifyDataSetChanged()
    }

    fun updateNotes(newNotes: List<Note>) {
        notes.addAll(newNotes)
        notifyDataSetChanged()
    }

    private fun transformColor(color: String): Int {
        return when (color.lowercase()) {
            "orange" -> Color.parseColor("#FFF9DB") // Hex for Orange
            "red" -> Color.parseColor("#FFA3A3")    // Hex for Red
            "green" -> Color.parseColor("#95F995")  // Hex for Green
            "blue" -> Color.parseColor("#A6A6FF")   // Hex for Blue
            "yellow" -> Color.parseColor("#F3F382") // Hex for Yellow
            else -> Color.parseColor("#FFF9DB")     // Default to White
        }
    }
}