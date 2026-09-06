package com.penonton.ui.adapter

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.penonton.data.model.AnimeItem
import com.penonton.data.model.WatchHistoryItem
import com.penonton.databinding.ItemAnimeBinding
import com.penonton.databinding.ItemContinueWatchingBinding
import com.penonton.databinding.ItemHomeBannerContainerBinding
import com.penonton.databinding.ItemHomeContinueWatchingBinding
import com.penonton.databinding.ItemHomeHeaderBinding
import com.penonton.databinding.ItemHomeSearchBinding
import com.penonton.databinding.ItemLoadingFooterBinding
import com.penonton.ui.activity.PlayerActivity
import com.penonton.util.loadPoster
import com.google.android.material.tabs.TabLayoutMediator

class HomeAdapter(
    private val onAnimeClick: (AnimeItem) -> Unit,
    private val onBannerClick: (AnimeItem) -> Unit,
    private val onBannerSelected: (String) -> Unit = {},
    private val onSearchSubmit: (String) -> Unit,
    private val onSearchClear: () -> Unit,
    private val defaultHeaderTitle: String = "Rilis Terbaru",
    private val searchHint: String = "Cari film atau serial drama..."
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_BANNER = 0
        const val TYPE_SEARCH = 1
        const val TYPE_HISTORY_HEADER = 2
        const val TYPE_CONTINUE = 3
        const val TYPE_HEADER = 4
        const val TYPE_ANIME = 5
        const val TYPE_LOADING = 6
    }

    private val banners = mutableListOf<AnimeItem>()
    private val animeList = mutableListOf<AnimeItem>()
    private val continueWatching = mutableListOf<WatchHistoryItem>()
    private var headerTitle: String = defaultHeaderTitle
    private var currentSearchQuery: String = ""
    private var isSearching = false
    private var isLoadingMore = false

    // ── Public API ──────────────────────────────────────────────────────────

    fun setData(
        newBanners: List<AnimeItem>,
        newAnime: List<AnimeItem>,
        history: List<WatchHistoryItem> = emptyList(),
        customTitle: String? = null
    ) {
        isSearching = false
        currentSearchQuery = ""
        headerTitle = customTitle ?: defaultHeaderTitle
        banners.clear(); banners.addAll(newBanners)
        animeList.clear(); animeList.addAll(newAnime)
        continueWatching.clear(); continueWatching.addAll(history.take(10))
        isLoadingMore = false
        notifyDataSetChanged()
    }

    fun appendAnime(newItems: List<AnimeItem>) {
        if (newItems.isEmpty()) return
        val startPos = itemCount - if (isLoadingMore) 1 else 0
        animeList.addAll(newItems)
        notifyItemRangeInserted(startPos, newItems.size)
    }

    fun setLoadingMore(loading: Boolean) {
        if (isLoadingMore == loading) return
        isLoadingMore = loading
        if (loading) notifyItemInserted(itemCount)
        else notifyItemRemoved(itemCount)
    }

    fun setSearchResult(query: String, results: List<AnimeItem>) {
        isSearching = true
        currentSearchQuery = query
        headerTitle = "Hasil Pencarian: $query"
        banners.clear()
        continueWatching.clear()
        animeList.clear(); animeList.addAll(results)
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

    private fun searchPos() = if (hasBanner()) 1 else 0
    private fun histHeaderPos() = if (hasContinue()) searchPos() + 1 else -1
    private fun continuePos() = if (hasContinue()) histHeaderPos() + 1 else -1
    private fun animeHeaderPos() = if (hasContinue()) continuePos() + 1 else searchPos() + 1
    private fun animeStartPos() = animeHeaderPos() + 1

    override fun getItemViewType(position: Int): Int {
        // Loading footer at very end
        if (isLoadingMore && position == itemCount - 1) return TYPE_LOADING
        return when {
            hasBanner() && position == 0 -> TYPE_BANNER
            position == searchPos() -> TYPE_SEARCH
            position == histHeaderPos() -> TYPE_HISTORY_HEADER
            position == continuePos() -> TYPE_CONTINUE
            position == animeHeaderPos() -> TYPE_HEADER
            else -> TYPE_ANIME
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        if (hasBanner()) count++ // banner
        count++ // search
        if (hasContinue()) count += 2 // history header + continue
        count++ // anime header
        count += animeList.size
        if (isLoadingMore) count++ // loading footer
        return count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BANNER   -> BannerViewHolder(ItemHomeBannerContainerBinding.inflate(inflater, parent, false))
            TYPE_SEARCH   -> SearchViewHolder(ItemHomeSearchBinding.inflate(inflater, parent, false))
            TYPE_HISTORY_HEADER -> HistoryHeaderViewHolder(ItemHomeHeaderBinding.inflate(inflater, parent, false))
            TYPE_CONTINUE -> ContinueWatchingViewHolder(ItemHomeContinueWatchingBinding.inflate(inflater, parent, false))
            TYPE_HEADER   -> HeaderViewHolder(ItemHomeHeaderBinding.inflate(inflater, parent, false))
            TYPE_LOADING  -> LoadingViewHolder(ItemLoadingFooterBinding.inflate(inflater, parent, false))
            else          -> AnimeViewHolder(ItemAnimeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BannerViewHolder        -> holder.bind(banners)
            is SearchViewHolder        -> holder.bind(currentSearchQuery)
            is HistoryHeaderViewHolder -> holder.bind("▶ Lanjutkan Menonton")
            is ContinueWatchingViewHolder -> holder.bind(continueWatching)
            is HeaderViewHolder        -> holder.bind(headerTitle)
            is LoadingViewHolder       -> { /* spinner, no binding needed */ }
            is AnimeViewHolder -> {
                val idx = position - animeStartPos()
                if (idx in animeList.indices) holder.bind(animeList[idx])
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
            TabLayoutMediator(binding.tabIndicator, binding.vpFeatured) { _, _ -> }.attach()
            binding.vpFeatured.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (position in banners.indices) {
                        onBannerSelected(banners[position].poster)
                    }
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
        fun bind(items: List<AnimeItem>) {
            bannerAdapter.submitList(items)
            if (items.isNotEmpty()) {
                val cur = binding.vpFeatured.currentItem.coerceIn(0, items.size - 1)
                onBannerSelected(items[cur].poster)
            }
            startAutoSlide()
        }
        private fun startAutoSlide() { handler.removeCallbacks(runnable); handler.postDelayed(runnable, 3500) }
        fun stopAutoSlide() { handler.removeCallbacks(runnable) }
    }

    inner class SearchViewHolder(private val binding: ItemHomeSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.etSearch.hint = searchHint
            binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = binding.etSearch.text?.toString()?.trim() ?: ""
                    if (query.isNotEmpty()) {
                        onSearchSubmit(query)
                    }
                    true
                } else false
            }

            binding.etSearch.addTextChangedListener { text ->
                val query = text?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                if (query.isEmpty() && isSearching) {
                    onSearchClear()
                }
            }

            binding.btnClearSearch.setOnClickListener {
                binding.etSearch.setText("")
                onSearchClear()
            }
        }

        fun bind(query: String) {
            if (binding.etSearch.text.toString() != query) {
                binding.etSearch.setText(query)
            }
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }
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
                        putExtra("ANIME_ID", item.animeId)
                        putExtra("ANIME_TITLE", item.title)
                        putExtra("ANIME_POSTER", item.poster)
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

    inner class AnimeViewHolder(private val binding: ItemAnimeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AnimeItem) {
            binding.tvTitle.text = item.title
            if (item.latestEp.isNotEmpty()) { binding.tvLatestEp.visibility = View.VISIBLE; binding.tvLatestEp.text = item.latestEp } else binding.tvLatestEp.visibility = View.GONE
            if (item.rating.isNotEmpty()) { binding.layoutRating.visibility = View.VISIBLE; binding.tvRating.text = item.rating } else binding.layoutRating.visibility = View.GONE
            if (item.year.isNotEmpty()) { binding.tvYear.visibility = View.VISIBLE; binding.tvYear.text = item.year } else binding.tvYear.visibility = View.GONE
            binding.ivPoster.loadPoster(item.poster)
            binding.root.setOnClickListener { onAnimeClick(item) }
        }
    }

    inner class LoadingViewHolder(binding: ItemLoadingFooterBinding) :
        RecyclerView.ViewHolder(binding.root)
}
