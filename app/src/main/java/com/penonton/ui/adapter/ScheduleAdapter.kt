package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.AnimeItem
import com.penonton.databinding.ItemScheduleBinding
import com.penonton.util.loadPoster

class ScheduleAdapter(
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    private val items = mutableListOf<AnimeItem>()

    fun submitList(newItems: List<AnimeItem>) {
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

            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
