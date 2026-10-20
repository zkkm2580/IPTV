package io.microshow.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.microshow.iptv.R

class GroupAdapter(
    private var groups: List<String>,
    private val onGroupSelected: (String, Int) -> Unit
) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    private var selectedIndex = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGroup: TextView = view.findViewById(R.id.tv_group_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.tvGroup.text = group

        val isSelected = position == selectedIndex
        if (isSelected) {
            holder.tvGroup.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.item_focus))
            holder.tvGroup.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.item_focus_text))
        } else {
            holder.tvGroup.setBackgroundColor(0)
            holder.tvGroup.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_sub))
        }

        holder.itemView.setOnClickListener {
            setSelectedIndex(holder.bindingAdapterPosition)
            onGroupSelected(group, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = groups.size

    fun updateGroups(newGroups: List<String>) {
        this.groups = newGroups
        this.selectedIndex = 0
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        if (index in groups.indices && index != selectedIndex) {
            val old = selectedIndex
            selectedIndex = index
            notifyItemChanged(old)
            notifyItemChanged(selectedIndex)
        }
    }
}
