package com.donghuaz.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.donghuaz.R
import com.donghuaz.data.local.StorageManager
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.data.parser.DonghuaParser
import com.donghuaz.databinding.FragmentScheduleBinding
import com.donghuaz.ui.activity.DetailActivity
import com.donghuaz.ui.activity.MainActivity
import com.donghuaz.ui.adapter.HomeAdapter
import kotlinx.coroutines.launch

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var seriesAdapter: HomeAdapter
    private lateinit var storage: StorageManager

    // Pagination state
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = StorageManager.getInstance(requireContext())
        setupRecyclerView()
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { refreshAll() }
        loadData(forceRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        // Refresh continue watching section on return
        val history = storage.getHistory()
        if (history.isNotEmpty()) seriesAdapter.updateContinueWatching(history)
    }

    private fun setupRecyclerView() {
        seriesAdapter = HomeAdapter(
            onAnimeClick = { openDetail(it) },
            onBannerClick = { openDetail(it) },
            onBannerSelected = { (activity as? MainActivity)?.updateAmbientBackdrop(it) },
            onSearchSubmit = { search(it) },
            onSearchClear = { clearSearch() },
            defaultHeaderTitle = "Series & Drama Terbaru",
            searchHint = "Cari serial drama, drakor, series..."
        )

        val glm = GridLayoutManager(requireContext(), 3)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (seriesAdapter.getItemViewType(position)) {
                    HomeAdapter.TYPE_BANNER,
                    HomeAdapter.TYPE_SEARCH,
                    HomeAdapter.TYPE_HISTORY_HEADER,
                    HomeAdapter.TYPE_CONTINUE,
                    HomeAdapter.TYPE_HEADER,
                    HomeAdapter.TYPE_LOADING -> 3 // full width
                    else -> 1 // 3 columns
                }
            }
        }

        binding.rvScheduleAnime.apply {
            layoutManager = glm
            adapter = seriesAdapter
            setHasFixedSize(false)

            // ── Infinite Scroll ───────────────────────────────────────────
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return // only trigger when scrolling down
                    val lm = rv.layoutManager as GridLayoutManager
                    val totalItems = lm.itemCount
                    val lastVisible = lm.findLastVisibleItemPosition()
                    // Trigger load when 6 items from the bottom
                    if (!isLoadingMore && hasMorePages && lastVisible >= totalItems - 6) {
                        loadNextPage()
                    }
                }
            })
        }
    }

    private fun openDetail(anime: AnimeItem) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra("ANIME_ID", anime.id)
            putExtra("ANIME_TITLE", anime.title)
            putExtra("ANIME_POSTER", anime.poster)
        })
    }

    fun search(query: String) {
        binding.shimmerView.startShimmer()
        binding.shimmerView.visibility = View.VISIBLE
        binding.rvScheduleAnime.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val results = DonghuaParser.search(query, 1)
                seriesAdapter.setSearchResult(query, results)
            } catch (e: Exception) {
                Log.e("ScheduleFragment", "Search error", e)
            } finally {
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.rvScheduleAnime.visibility = View.VISIBLE
            }
        }
    }

    fun clearSearch() { loadData(forceRefresh = false) }

    private fun refreshAll() {
        currentPage = 1
        hasMorePages = true
        loadData(forceRefresh = true)
    }

    private fun loadData(forceRefresh: Boolean = false) {
        if (forceRefresh || seriesAdapter.itemCount <= 1) {
            binding.shimmerView.startShimmer()
            binding.shimmerView.visibility = View.VISIBLE
            binding.rvScheduleAnime.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                val seriesBanners = DonghuaParser.getFeaturedSeriesBanners(forceRefresh)
                val latestSeries = DonghuaParser.getLatestSeries(1, forceRefresh)
                val history = storage.getHistory()
                currentPage = 1
                hasMorePages = latestSeries.isNotEmpty()
                seriesAdapter.setData(
                    newBanners = seriesBanners.take(8),
                    newAnime = latestSeries,
                    history = history,
                    customTitle = "Series & Drama Terbaru"
                )
            } catch (e: Exception) {
                Log.e("ScheduleFragment", "Load error", e)
            } finally {
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.rvScheduleAnime.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadNextPage() {
        if (isLoadingMore || !hasMorePages) return
        isLoadingMore = true
        seriesAdapter.setLoadingMore(true)

        lifecycleScope.launch {
            try {
                val nextPage = currentPage + 1
                val items = DonghuaParser.getLatestSeries(nextPage)
                if (items.isEmpty()) {
                    hasMorePages = false
                } else {
                    currentPage = nextPage
                    seriesAdapter.setLoadingMore(false)
                    seriesAdapter.appendAnime(items)
                }
            } catch (e: Exception) {
                Log.e("ScheduleFragment", "Load more error", e)
            } finally {
                isLoadingMore = false
                seriesAdapter.setLoadingMore(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
