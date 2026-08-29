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

            val currentMins = item.positionMs / 60000
            val currentSecs = (item.positionMs % 60000) / 1000
            val totalMins = item.durationMs / 60000
            val totalSecs = (item.durationMs % 60000) / 1000

            val effectiveDuration = if (item.durationMs > 0L) item.durationMs else 1200000L
            val progress = ((item.positionMs.toDouble() / effectiveDuration.toDouble()) * 100.0).toInt().coerceIn(1, 100)
            binding.pbWatchProgress.progress = progress

            binding.tvHistoryEpisode.text = if (item.durationMs > 0L) {
                "${item.episodeName} • %02d:%02d / %02d:%02d".format(currentMins, currentSecs, totalMins, totalSecs)
            } else if (item.positionMs > 1000L) {
                "${item.episodeName} • Lanjut %02d:%02d".format(currentMins, currentSecs)
            } else {
                item.episodeName
            }

            binding.ivHistoryPoster.loadPoster(item.poster)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnContinueWatch.setOnClickListener { onItemClick(item) }
        }
    }
}
