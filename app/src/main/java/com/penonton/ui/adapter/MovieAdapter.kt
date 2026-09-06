package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.MovieItem
import com.penonton.databinding.ItemMovieBinding
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

class MovieAdapter(
    private val onItemClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    private val items = mutableListOf<MovieItem>()

    
    fun submitList(newItems: List<MovieItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MovieViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MovieItem) {
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

            val countryBadge = CountryUtils.getCountryBadge(item)
            if (countryBadge.isNotEmpty()) {
                binding.tvCountry.visibility = View.VISIBLE
                binding.tvCountry.text = countryBadge
            } else {
                binding.tvCountry.visibility = View.GONE
            }

            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
