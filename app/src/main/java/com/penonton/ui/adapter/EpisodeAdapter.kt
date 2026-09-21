package com.penonton.ui.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.penonton.R
import com.penonton.data.model.EpisodeItem
import com.penonton.databinding.ItemEpisodeBinding
import androidx.core.graphics.toColorInt

class EpisodeAdapter(
    private val onEpisodeClick: (EpisodeItem) -> Unit
) : ListAdapter<EpisodeItem, EpisodeAdapter.EpisodeViewHolder>(EpisodeDiffCallback()) {

    private var watchedSet = setOf<String>()
    private var activeNid: Int = -1

    fun submitList(newItems: List<EpisodeItem>?, watchedEpisodes: Set<String> = emptySet(), currentNid: Int = -1) {
        watchedSet = watchedEpisodes
        activeNid = currentNid
        super.submitList(newItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EpisodeViewHolder(binding, onEpisodeClick)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val item = getItem(position)
        val isWatched = watchedSet.contains(item.episode)
        val isActive = item.nid == activeNid
        holder.bind(item, isWatched, isActive)
    }

    class EpisodeViewHolder(
        private val binding: ItemEpisodeBinding,
        private val onEpisodeClick: (EpisodeItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: EpisodeItem, isWatched: Boolean, isActive: Boolean) {
            val isMovie = item.episode.contains("Movie", ignoreCase = true) || item.episode.contains("Film", ignoreCase = true)

            if (isMovie) {
                binding.tvEpisodeName.text = "▶ Putar Film (Full Movie HD)"
                binding.tvEpisodeName.textSize = 15f
            } else {
                val cleanEp = item.episode.replace("EP", "", ignoreCase = true).trim()
                binding.tvEpisodeName.text = cleanEp.ifEmpty { item.nid.toString() }
                binding.tvEpisodeName.textSize = 17f
            }

            if (isActive) {
                binding.layoutEpisodeBox.setBackgroundResource(R.drawable.bg_episode_card_active)
                binding.tvEpisodeName.setTextColor(Color.WHITE)
                binding.tvEpisodeName.setTypeface(null, Typeface.BOLD)
            } else {
                binding.layoutEpisodeBox.setBackgroundResource(R.drawable.bg_episode_card_normal)
                binding.tvEpisodeName.setTextColor("#E2E8F0".toColorInt())
                binding.tvEpisodeName.setTypeface(null, Typeface.NORMAL)
            }

            binding.ivWatchedCheckmark.isVisible = isWatched
            binding.root.setOnClickListener { onEpisodeClick(item) }
        }
    }

    private class EpisodeDiffCallback : DiffUtil.ItemCallback<EpisodeItem>() {
        override fun areItemsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean {
            return oldItem.nid == newItem.nid
        }

        override fun areContentsTheSame(oldItem: EpisodeItem, newItem: EpisodeItem): Boolean {
            return oldItem == newItem
        }
    }
}