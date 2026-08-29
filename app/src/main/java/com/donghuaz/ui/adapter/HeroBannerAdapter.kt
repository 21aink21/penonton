package com.donghuaz.ui.adapter

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.databinding.ItemHeroBannerBinding
import com.donghuaz.util.loadPoster

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

    override fun getItemCount(): Int = items.size

    inner class BannerViewHolder(private val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Apply 50% Gaussian Blur effect on modern Android (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.ivBannerBlur.setRenderEffect(
                    RenderEffect.createBlurEffect(
                        35f, 35f, Shader.TileMode.CLAMP
                    )
                )
            }
        }

        fun bind(item: AnimeItem, rank: Int) {
            binding.tvBannerTitle.text = item.title
            binding.tvBannerRank.text = "🔥 Top #$rank"
            binding.tvBannerEp.text = if (item.latestEp.isNotEmpty()) item.latestEp else "HD"
            binding.tvBannerRating.text = if (item.rating.isNotEmpty()) "★ ${item.rating}" else "★ 9.0"

            // Foreground crisp poster
            binding.ivBanner.loadPoster(item.poster)

            // Background duplicate ambient blur poster (30% opacity)
            binding.ivBannerBlur.loadPoster(item.poster)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
