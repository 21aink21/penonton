package com.penonton.ui.adapter

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.R
import com.penonton.data.model.ServerGroup
import com.penonton.databinding.ItemServerBinding

class ServerAdapter(
    private val onServerSelected: (Int) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    private val items = mutableListOf<ServerGroup>()
    private var selectedIndex = 0

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<ServerGroup>, initialSelection: Int = 0) {
        items.clear()
        items.addAll(newItems)
        selectedIndex = initialSelection
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class ServerViewHolder(private val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServerGroup, isSelected: Boolean) {
            val compactName = getCompactServerName(item.serverName)
            binding.tvServerName.text = compactName
            
            if (isSelected) {
                binding.tvServerName.setBackgroundResource(R.drawable.bg_server_chip_active)
                binding.tvServerName.setTextColor(0xFFFFFFFF.toInt())
                binding.tvServerName.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvServerName.setBackgroundResource(R.drawable.bg_server_chip_inactive)
                binding.tvServerName.setTextColor(0xFFA0A0A0.toInt())
                binding.tvServerName.setTypeface(null, Typeface.NORMAL)
            }

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && selectedIndex != pos) {
                    val prev = selectedIndex
                    selectedIndex = pos
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedIndex)
                    onServerSelected(selectedIndex)
                }
            }
        }
    }

    private fun getCompactServerName(rawName: String): String {
        val lower = rawName.lowercase()
        val isIndo = lower.contains("indo") || lower.contains("indonesia")
        val isEng = lower.contains("eng") || lower.contains("english")
        val flag = if (isIndo) "🇮🇩 " else if (isEng) "🇬🇧 " else ""

        val core = when {
            lower.contains("ganjing") || lower.contains("ganjian") || lower.contains("gang") -> "GanJing"
            lower.contains("4kvip") || lower.contains("4k-vip") || lower.contains("4k") -> "4K-VIP"
            lower.contains("rumble") || lower.contains("rum") -> "Rumble"
            lower.contains("daily") -> "Daily"
            lower.contains("1080") -> "1080p"
            else -> rawName
                .replace("INDO", "", ignoreCase = true)
                .replace("ENG", "", ignoreCase = true)
                .replace("VIP", "", ignoreCase = true)
                .replace("-", " ")
                .trim()
                .take(8)
        }

        return "$flag$core"
    }
}
