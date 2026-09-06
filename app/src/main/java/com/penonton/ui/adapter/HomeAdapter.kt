package com.penonton.ui.adapter

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.penonton.R
import com.penonton.data.model.MovieItem
import com.penonton.data.model.WatchHistoryItem
import com.penonton.databinding.ItemMovieBinding
import com.penonton.databinding.ItemContinueWatchingBinding
import com.penonton.databinding.ItemHomeBannerContainerBinding
import com.penonton.databinding.ItemHomeContinueWatchingBinding
import com.penonton.databinding.ItemHomeHeaderBinding
import com.penonton.databinding.ItemLoadingFooterBinding
import com.penonton.ui.activity.PlayerActivity
import com.penonton.util.CountryUtils
import com.penonton.util.loadPoster

class HomeAdapter(
    private val onAnimeClick: (MovieItem) -> Unit,
    private val onBannerClick: (MovieItem) -> Unit,
    private val onBannerSelected: (String) -> Unit = {},
    private val defaultHeaderTitle: String = "Rilis Terbaru"
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_BANNER = 0
        const val TYPE_HISTORY_HEADER = 1
        const val TYPE_CONTINUE = 2
        const val TYPE_HEADER = 3
        const val TYPE_ANIME = 4
        const val TYPE_LOADING = 5
    }

    private val banners = mutableListOf<MovieItem>()
    private val movieList = mutableListOf<MovieItem>()
    private val continueWatching = mutableListOf<WatchHistoryItem>()
    private var headerTitle: String = defaultHeaderTitle
    private var isSearching = false
    private var isLoadingMore = false

    // ── Public API ──────────────────────────────────────────────────────────

    fun setData(
        newBanners: List<MovieItem>,
        newAnime: List<MovieItem>,
        history: List<WatchHistoryItem> = emptyList(),
        customTitle: String? = null
    ) {
        isSearching = false
        headerTitle = customTitle ?: defaultHeaderTitle
        banners.clear(); banners.addAll(newBanners)
        movieList.clear(); movieList.addAll(newAnime)
        continueWatching.clear(); continueWatching.addAll(history.take(10))
        isLoadingMore = false
        notifyDataSetChanged()
    }

    fun appendAnime(newItems: List<MovieItem>) {
        if (newItems.isEmpty()) return
        val startPos = itemCount - if (isLoadingMore) 1 else 0
        movieList.addAll(newItems)
        notifyItemRangeInserted(startPos, newItems.size)
    }

    fun setLoadingMore(loading: Boolean) {
        if (isLoadingMore == loading) return
        isLoadingMore = loading
        if (loading) notifyItemInserted(itemCount)
        else notifyItemRemoved(itemCount)
    }

    fun setSearchResult(query: String, results: List<MovieItem>) {
        isSearching = true
        headerTitle = "Hasil Pencarian: $query"
        banners.clear()
        continueWatching.clear()
        movieList.clear(); movieList.addAll(results)
        isLoadingMore = false
        notifyDataSetChanged()
    }

    fun updateContinueWatching(history: List<WatchHistoryItem>) {
        continueWatching.clear()
        continueWatching.addAll(history.take(10))
        notifyDataSetChanged()
    }

    // ── Position helpers ────────────────────────────────────────────────────

    private fun hasContinue() = !isSearching && continueWatching.isNotEmpty()
    private fun hasBanner() = !isSearching && banners.isNotEmpty()

    private fun bannerPos() = if (hasBanner()) 0 else -1
    private fun histHeaderPos() = if (hasContinue()) (if (hasBanner()) 1 else 0) else -1
    private fun continuePos() = if (hasContinue()) histHeaderPos() + 1 else -1
    private fun animeHeaderPos(): Int {
        var pos = 0
        if (hasBanner()) pos++
        if (hasContinue()) pos += 2
        return pos
    }
    private fun animeStartPos() = animeHeaderPos() + 1

    override fun getItemViewType(position: Int): Int {
        // Loading footer at very end
        if (isLoadingMore && position == itemCount - 1) return TYPE_LOADING
        return when (position) {
            bannerPos() -> TYPE_BANNER
            histHeaderPos() -> TYPE_HISTORY_HEADER
            continuePos() -> TYPE_CONTINUE
            animeHeaderPos() -> TYPE_HEADER
            else -> TYPE_ANIME
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        if (hasBanner()) count++ // banner
        if (hasContinue()) count += 2 // history header + continue
        count++ // anime header
        count += movieList.size
        if (isLoadingMore) count++ // loading footer
        return count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BANNER   -> BannerViewHolder(ItemHomeBannerContainerBinding.inflate(inflater, parent, false))
            TYPE_HISTORY_HEADER -> HistoryHeaderViewHolder(ItemHomeHeaderBinding.inflate(inflater, parent, false))
            TYPE_CONTINUE -> ContinueWatchingViewHolder(ItemHomeContinueWatchingBinding.inflate(inflater, parent, false))
            TYPE_HEADER   -> HeaderViewHolder(ItemHomeHeaderBinding.inflate(inflater, parent, false))
            TYPE_LOADING  -> LoadingViewHolder(ItemLoadingFooterBinding.inflate(inflater, parent, false))
            else          -> MovieViewHolder(ItemMovieBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BannerViewHolder        -> holder.bind(banners)
            is HistoryHeaderViewHolder -> holder.bind("▶ Lanjutkan Menonton")
            is ContinueWatchingViewHolder -> holder.bind(continueWatching)
            is HeaderViewHolder        -> holder.bind(headerTitle)
            is LoadingViewHolder       -> { /* spinner, no binding needed */ }
            is MovieViewHolder -> {
                val idx = position - animeStartPos()
                if (idx in movieList.indices) holder.bind(movieList[idx])
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is BannerViewHolder) holder.stopAutoSlide()
    }

    // ── ViewHolders ─────────────────────────────────────────────────────────

    inner class BannerViewHolder(private val binding: ItemHomeBannerContainerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val bannerAdapter = HeroBannerAdapter { onBannerClick(it) }
        private val handler = Handler(Looper.getMainLooper())
        private val runnable = object : Runnable {
            override fun run() {
                val count = bannerAdapter.itemCount
                if (count > 1) { binding.vpFeatured.setCurrentItem((binding.vpFeatured.currentItem + 1) % count, true); handler.postDelayed(this, 3500) }
            }
        }
        init {
            binding.vpFeatured.adapter = bannerAdapter
            binding.vpFeatured.offscreenPageLimit = 3
            binding.vpFeatured.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position in banners.indices) {
                        onBannerSelected(banners[position].poster)
                    }
                    updateActiveDot(position)
                    val rv = binding.vpFeatured.getChildAt(0) as? RecyclerView
                    val holder = rv?.findViewHolderForAdapterPosition(position) as? HeroBannerAdapter.BannerViewHolder
                    holder?.startZoomAnimation()
                }
                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) handler.removeCallbacks(runnable)
                    else if (state == ViewPager2.SCROLL_STATE_IDLE) startAutoSlide()
                }
            })
        }

        private fun setupDots(count: Int, activePos: Int) {
            binding.layoutDotsIndicator.removeAllViews()
            if (count <= 1) return

            val ctx = binding.root.context
            val density = ctx.resources.displayMetrics.density
            val normalPx = (6 * density).toInt()
            val activePx = (7 * density).toInt()
            val marginPx = (1.5f * density).toInt().coerceAtLeast(1)

            for (i in 0 until count) {
                val dot = View(ctx).apply {
                    val size = if (i == activePos) activePx else normalPx
                    val lp = LinearLayout.LayoutParams(size, size).apply {
                        setMargins(marginPx, 0, marginPx, 0)
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    layoutParams = lp
                    background = ContextCompat.getDrawable(
                        ctx,
                        if (i == activePos) R.drawable.dot_banner_active else R.drawable.dot_banner_inactive
                    )
                }
                binding.layoutDotsIndicator.addView(dot)
            }
        }

        private fun updateActiveDot(activePos: Int) {
            val ctx = binding.root.context
            val density = ctx.resources.displayMetrics.density
            val normalPx = (6 * density).toInt()
            val activePx = (7 * density).toInt()
            val marginPx = (1.5f * density).toInt().coerceAtLeast(1)

            val total = binding.layoutDotsIndicator.childCount
            for (i in 0 until total) {
                val dot = binding.layoutDotsIndicator.getChildAt(i) ?: continue
                val isActive = (i == activePos)
                val size = if (isActive) activePx else normalPx
                val lp = dot.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(size, size)
                lp.width = size
                lp.height = size
                lp.setMargins(marginPx, 0, marginPx, 0)
                dot.layoutParams = lp
                dot.background = ContextCompat.getDrawable(
                    ctx,
                    if (isActive) R.drawable.dot_banner_active else R.drawable.dot_banner_inactive
                )
            }
        }

        fun bind(items: List<MovieItem>) {
            bannerAdapter.submitList(items)
            if (items.isNotEmpty()) {
                val cur = binding.vpFeatured.currentItem.coerceIn(0, items.size - 1)
                onBannerSelected(items[cur].poster)
                setupDots(items.size, cur)
            } else {
                binding.layoutDotsIndicator.removeAllViews()
            }
            startAutoSlide()
        }
        private fun startAutoSlide() { handler.removeCallbacks(runnable); handler.postDelayed(runnable, 3500) }
        fun stopAutoSlide() { handler.removeCallbacks(runnable) }
    }

    inner class HistoryHeaderViewHolder(private val binding: ItemHomeHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) { binding.tvSectionHeader.text = title }
    }

    inner class ContinueWatchingViewHolder(private val binding: ItemHomeContinueWatchingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(items: List<WatchHistoryItem>) {
            val ctx = binding.root.context
            val inflater = LayoutInflater.from(ctx)
            binding.llContinueWatchingCards.removeAllViews()
            items.forEach { item ->
                val card = ItemContinueWatchingBinding.inflate(inflater, binding.llContinueWatchingCards, false)
                card.ivCwPoster.loadPoster(item.poster)

                val dummyItem = MovieItem(id = item.movieId, title = item.title, latestEp = item.episodeName, rating = "", poster = item.poster, url = "")
                val countryBadge = CountryUtils.getCountryBadge(dummyItem)
                if (countryBadge.isNotEmpty()) {
                    card.tvCwCountry.visibility = View.VISIBLE
                    card.tvCwCountry.text = countryBadge
                } else {
                    card.tvCwCountry.visibility = View.GONE
                }

                val mins = item.positionMs / 60000
                val secs = (item.positionMs % 60000) / 1000
                val effectiveDuration = if (item.durationMs > 0L) item.durationMs else 1200000L
                val progress = ((item.positionMs.toDouble() / effectiveDuration.toDouble()) * 100.0).toInt().coerceIn(1, 100)
                card.pbCwProgress.progress = progress
                card.pbCwProgress.visibility = View.VISIBLE

                if (item.durationMs > 0L) {
                    val totalMins = item.durationMs / 60000
                    val totalSecs = (item.durationMs % 60000) / 1000
                    card.tvCwPosition.text = "%02d:%02d / %02d:%02d".format(mins, secs, totalMins, totalSecs)
                } else if (item.positionMs > 1000L) {
                    card.tvCwPosition.text = "Lanjut %02d:%02d".format(mins, secs)
                } else {
                    card.tvCwPosition.text = item.episodeName
                }

                card.root.setOnClickListener {
                    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
                        putExtra("MEDIA_ID", item.movieId)
                        putExtra("MEDIA_TITLE", item.title)
                        putExtra("MEDIA_POSTER", item.poster)
                        putExtra("EPISODE_NAME", item.episodeName)
                        putExtra("SID", item.sid)
                        putExtra("NID", item.nid)
                        putExtra("START_POSITION", item.positionMs)
                    })
                }
                binding.llContinueWatchingCards.addView(card.root)
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemHomeHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) { binding.tvSectionHeader.text = title }
    }

    inner class MovieViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MovieItem) {
            binding.tvTitle.text = item.title
            if (item.latestEp.isNotEmpty()) { binding.tvLatestEp.visibility = View.VISIBLE; binding.tvLatestEp.text = item.latestEp } else binding.tvLatestEp.visibility = View.GONE
            if (item.rating.isNotEmpty()) { binding.layoutRating.visibility = View.VISIBLE; binding.tvRating.text = item.rating } else binding.layoutRating.visibility = View.GONE
            if (item.year.isNotEmpty()) { binding.tvYear.visibility = View.VISIBLE; binding.tvYear.text = item.year } else binding.tvYear.visibility = View.GONE

            val countryBadge = CountryUtils.getCountryBadge(item)
            if (countryBadge.isNotEmpty()) {
                binding.tvCountry.visibility = View.VISIBLE
                binding.tvCountry.text = countryBadge
            } else {
                binding.tvCountry.visibility = View.GONE
            }

            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onAnimeClick(item) }
        }
    }

    inner class LoadingViewHolder(binding: ItemLoadingFooterBinding) :
        RecyclerView.ViewHolder(binding.root)
}
