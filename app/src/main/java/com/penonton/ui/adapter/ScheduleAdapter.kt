package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.MovieItem
import com.penonton.databinding.ItemScheduleBinding
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

class ScheduleAdapter(
    private val onItemClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    private val items = mutableListOf<MovieItem>()

    fun submitList(newItems: List<MovieItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ScheduleViewHolder(private val binding: ItemScheduleBinding) :
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
