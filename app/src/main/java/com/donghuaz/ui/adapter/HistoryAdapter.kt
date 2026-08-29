package com.donghuaz.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.donghuaz.data.model.WatchHistoryItem
import com.donghuaz.databinding.ItemHistoryBinding
import com.donghuaz.util.loadPoster

class HistoryAdapter(
    private val onItemClick: (WatchHistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<WatchHistoryItem>()

    fun submitList(newItems: List<WatchHistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchHistoryItem) {
            binding.tvHistoryTitle.text = item.title

            val currentMinutes = item.positionMs / 60000
            val totalMinutes = item.durationMs / 60000
            binding.tvHistoryEpisode.text = if (totalMinutes > 0) {
                "${item.episodeName} (${currentMinutes}m / ${totalMinutes}m)"
            } else {
                item.episodeName
            }

            val progress = if (item.durationMs > 0) {
                ((item.positionMs.toDouble() / item.durationMs) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            binding.pbWatchProgress.progress = progress

            binding.ivHistoryPoster.loadPoster(item.poster)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnContinueWatch.setOnClickListener { onItemClick(item) }
        }
    }
}
