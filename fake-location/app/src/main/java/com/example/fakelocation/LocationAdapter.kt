package com.example.fakelocation

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LocationAdapter(
    private var items: List<SavedLocation>,
    private val onSelect: (SavedLocation) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<LocationAdapter.VH>() {

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(android.R.id.text1)
        val coords: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = "📌 ${item.name}"
        holder.coords.text = "%.6f, %.6f".format(item.lat, item.lng)

        holder.itemView.setOnClickListener { onSelect(item) }

        // 長押しで削除
        holder.itemView.setOnLongClickListener {
            onDelete(holder.adapterPosition)
            true
        }
    }

    fun update(newItems: List<SavedLocation>) {
        items = newItems
        notifyDataSetChanged()
    }
}
