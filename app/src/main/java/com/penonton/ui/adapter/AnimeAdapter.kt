package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.AnimeItem
import com.penonton.databinding.ItemAnimeBinding
import com.penonton.util.loadPoster

class AnimeAdapter(
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<AnimeAdapter.AnimeViewHolder>() {

    private val items = mutableListOf<AnimeItem>()

    
    fun submitList(newItems: List<AnimeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimeViewHolder {
        val binding = ItemAnimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimeViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AnimeViewHolder(private val binding: ItemAnimeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AnimeItem) {
            binding.tvTitle.text = item.title

            if (item.latestEp.isNotEmpty()) {
                binding.tvLatestEp.visibility = View.VISIBLE
                binding.tvLatestEp.text = item.latestEp
            } else {
                binding.tvLatestEp.visibility = View.GONE
            }

            if (item.rating.isNotEmpty()) {
                binding.layoutRating.visibility = View.VISIBLE
                binding.tvRating.text = item.rating
            } else {
                binding.layoutRating.visibility = View.GONE
            }

            if (item.year.isNotEmpty()) {
                binding.tvYear.visibility = View.VISIBLE
                binding.tvYear.text = item.year
            } else {
                binding.tvYear.visibility = View.GONE
            }

            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
