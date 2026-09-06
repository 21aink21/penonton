package com.penonton.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.R
import com.penonton.data.model.EpisodeItem
import com.penonton.databinding.ItemEpisodeBinding

class EpisodeAdapter(
    private val onEpisodeClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    private val items = mutableListOf<EpisodeItem>()
    private var watchedSet = setOf<String>()
    private var activeNid: Int = -1

    fun submitList(newItems: List<EpisodeItem>, watchedEpisodes: Set<String> = emptySet(), currentNid: Int = -1) {
        items.clear()
        items.addAll(newItems)
        watchedSet = watchedEpisodes
        activeNid = currentNid
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class EpisodeViewHolder(private val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EpisodeItem) {
            val isWatched = watchedSet.contains(item.episode)
            val isActive = item.nid == activeNid

            val isMovie = item.episode.contains("Movie", ignoreCase = true) || item.episode.contains("Film", ignoreCase = true)

            if (isMovie) {
                binding.tvEpisodeName.text = "▶ Putar Film (Full Movie HD)"
                binding.tvEpisodeName.textSize = 15f
            } else {
                val cleanEp = item.episode.replace("EP", "", ignoreCase = true).trim()
                binding.tvEpisodeName.text = if (cleanEp.isNotEmpty()) cleanEp else "${item.nid}"
                binding.tvEpisodeName.textSize = 17f
            }

            if (isActive) {
                // Episode Sedang Diputar (Aktif): Latar merah maroon solid (#A11228), teks angka putih bold
                binding.layoutEpisodeBox.setBackgroundResource(R.drawable.bg_episode_card_active)
                binding.tvEpisodeName.setTextColor(0xFFFFFFFF.toInt())
                binding.tvEpisodeName.setTypeface(null, Typeface.BOLD)
            } else {
                // Episode Tersedia: Latar abu-abu gelap transparan (rgba(255,255,255,0.07)), teks angka abu-abu terang
                binding.layoutEpisodeBox.setBackgroundResource(R.drawable.bg_episode_card_normal)
                binding.tvEpisodeName.setTextColor(0xFFE2E8F0.toInt())
                binding.tvEpisodeName.setTypeface(null, Typeface.NORMAL)
            }

            // Status Tonton: Ikon tanda centang kecil (checkmark 12x12dp) di pojok kanan atas kartu
            if (isWatched) {
                binding.ivWatchedCheckmark.visibility = View.VISIBLE
            } else {
                binding.ivWatchedCheckmark.visibility = View.GONE
            }

            binding.root.setOnClickListener { onEpisodeClick(item) }
        }
    }
}
