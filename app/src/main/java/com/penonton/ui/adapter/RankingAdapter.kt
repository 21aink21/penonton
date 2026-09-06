package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.penonton.R
import com.penonton.data.model.AnimeItem
import com.penonton.databinding.ItemRankingBinding
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

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

            val countryBadge = CountryUtils.getCountryBadge(item)
            if (countryBadge.isNotEmpty()) {
                binding.tvRankCountry.visibility = android.view.View.VISIBLE
                binding.tvRankCountry.text = countryBadge
            } else {
                binding.tvRankCountry.visibility = android.view.View.GONE
            }

            val info = listOfNotNull(
                item.latestEp.takeIf { it.isNotEmpty() },
                item.year.takeIf { it.isNotEmpty() },
                item.rating.takeIf { it.isNotEmpty() }?.let { "★ $it" }
            ).joinToString(" • ")
            binding.tvRankEp.text = if (info.isNotEmpty()) info else "Popular"

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
