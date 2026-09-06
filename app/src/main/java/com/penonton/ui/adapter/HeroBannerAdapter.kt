package com.penonton.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.penonton.data.model.AnimeItem
import com.penonton.databinding.ItemHeroBannerBinding
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

class HeroBannerAdapter(
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.BannerViewHolder>() {

    private val items = mutableListOf<AnimeItem>()

    fun submitList(newItems: List<AnimeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemHeroBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun onViewAttachedToWindow(holder: BannerViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.startZoomAnimation()
    }

    override fun onViewDetachedFromWindow(holder: BannerViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.resetZoom()
    }

    override fun getItemCount(): Int = items.size

    inner class BannerViewHolder(private val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AnimeItem, rank: Int) {
            binding.tvBannerTitle.text = item.title
            binding.tvBannerRank.text = "🔥 Top #$rank"

            val countryBadge = CountryUtils.getCountryBadge(item)
            if (countryBadge.isNotEmpty()) {
                binding.tvBannerCountry.visibility = android.view.View.VISIBLE
                binding.tvBannerCountry.text = countryBadge
            } else {
                binding.tvBannerCountry.visibility = android.view.View.GONE
            }

            binding.tvBannerEp.text = if (item.latestEp.isNotEmpty()) item.latestEp else "HD"
            binding.tvBannerRating.text = if (item.rating.isNotEmpty()) "★ ${item.rating}" else "★ 9.0"

            resetZoom()
            binding.ivBanner.loadPoster(item.poster)
            startZoomAnimation()

            binding.root.setOnClickListener { onItemClick(item) }
        }

        fun startZoomAnimation() {
            binding.ivBanner.animate().cancel()
            binding.ivBanner.scaleX = 1.0f
            binding.ivBanner.scaleY = 1.0f
            binding.ivBanner.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(3800)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
        }

        fun resetZoom() {
            binding.ivBanner.animate().cancel()
            binding.ivBanner.scaleX = 1.0f
            binding.ivBanner.scaleY = 1.0f
        }
    }
}
