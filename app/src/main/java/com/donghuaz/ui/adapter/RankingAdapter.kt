package com.donghuaz.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.donghuaz.R
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.databinding.ItemRankingBinding
import com.donghuaz.util.loadPoster

class RankingAdapter(
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    private val items = mutableListOf<AnimeItem>()

    
    fun submitList(newItems: List<AnimeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val binding = ItemRankingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RankingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun getItemCount(): Int = items.size

    inner class RankingViewHolder(private val binding: ItemRankingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AnimeItem, rank: Int) {
            binding.tvRankNumber.text = rank.toString()
            binding.tvRankTitle.text = item.title
            binding.tvRankEp.text = if (item.latestEp.isNotEmpty()) item.latestEp else "Hot Series"

            val context = binding.root.context
            when (rank) {
                1 -> binding.tvRankNumber.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
                2 -> binding.tvRankNumber.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                3 -> binding.tvRankNumber.setTextColor(ContextCompat.getColor(context, R.color.primary))
                else -> binding.tvRankNumber.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            }

            binding.ivRankPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
