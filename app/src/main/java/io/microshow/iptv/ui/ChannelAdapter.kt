package io.microshow.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.microshow.iptv.R
import io.microshow.iptv.model.IptvChannel

class ChannelAdapter(
    private var channels: List<IptvChannel>,
    private val onChannelClick: (IptvChannel, Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private var selectedIndex = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layout: View = view.findViewById(R.id.layout_channel_item)
        val tvNumber: TextView = view.findViewById(R.id.tv_channel_number)
        val tvName: TextView = view.findViewById(R.id.tv_channel_name)
        val tvBadge: TextView = view.findViewById(R.id.tv_channel_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.tvNumber.text = String.format("%02d", position + 1)
        holder.tvName.text = channel.name

        val isSelected = position == selectedIndex
        if (isSelected) {
            holder.layout.setBackgroundResource(R.drawable.bg_channel_selected)
            holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.item_focus_text))
            holder.tvNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.item_focus_text))
        } else {
            holder.layout.setBackgroundColor(0)
            holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_main))
            holder.tvNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_sub))
        }

        holder.itemView.setOnClickListener {
            setSelectedIndex(holder.bindingAdapterPosition)
            onChannelClick(channel, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = channels.size

    fun updateChannels(newChannels: List<IptvChannel>) {
        this.channels = newChannels
        this.selectedIndex = 0
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        if (index in channels.indices && index != selectedIndex) {
            val old = selectedIndex
            selectedIndex = index
            notifyItemChanged(old)
            notifyItemChanged(selectedIndex)
        }
    }
}
