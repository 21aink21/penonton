package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.MovieItem
import com.penonton.databinding.ItemMovieBinding
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

class MovieAdapter(
    private val onItemClick: (MovieItem) -> Unit
) : ListAdapter<MovieItem, MovieAdapter.MovieViewHolder>(MovieDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MovieViewHolder(
        private val binding: ItemMovieBinding,
        private val onItemClick: (MovieItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MovieItem) {
            binding.tvTitle.text = item.title

            binding.tvLatestEp.isVisible = item.latestEp.isNotEmpty()
            binding.tvLatestEp.text = item.latestEp

            binding.layoutRating.isVisible = item.rating.isNotEmpty()
            binding.tvRating.text = item.rating

            binding.tvYear.isVisible = item.year.isNotEmpty()
            binding.tvYear.text = item.year

            val countryBadge = CountryUtils.getCountryBadge(item)
            binding.tvCountry.isVisible = countryBadge.isNotEmpty()
            binding.tvCountry.text = countryBadge

            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private class MovieDiffCallback : DiffUtil.ItemCallback<MovieItem>() {
        override fun areItemsTheSame(oldItem: MovieItem, newItem: MovieItem): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: MovieItem, newItem: MovieItem): Boolean {
            return oldItem == newItem
        }
    }
}