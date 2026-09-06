package com.penonton.ui.fragment

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
import com.penonton.R
import com.penonton.data.local.StorageManager
import com.penonton.data.model.MovieItem
import com.penonton.data.parser.Lk21Parser
import com.penonton.databinding.FragmentHomeBinding
import com.penonton.ui.activity.DetailActivity
import com.penonton.ui.adapter.HomeAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeAdapter: HomeAdapter
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
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
        if (history.isNotEmpty()) homeAdapter.updateContinueWatching(history)
    }

    private fun setupRecyclerView() {
        homeAdapter = HomeAdapter(
            onAnimeClick = { openDetail(it) },
            onBannerClick = { openDetail(it) },
            onBannerSelected = { (activity as? com.penonton.ui.activity.MainActivity)?.updateAmbientBackdrop(it) },
            onSearchSubmit = { search(it) },
            onSearchClear = { clearSearch() },
            defaultHeaderTitle = "Film & Movie Terbaru",
            searchHint = "Cari film LK21..."
        )

        val glm = GridLayoutManager(requireContext(), 3)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (homeAdapter.getItemViewType(position)) {
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

        binding.rvHomeContent.apply {
            layoutManager = glm
            adapter = homeAdapter
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

    private fun openDetail(anime: MovieItem) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra("MEDIA_ID", anime.id)
            putExtra("MEDIA_TITLE", anime.title)
            putExtra("MEDIA_POSTER", anime.poster)
            putExtra("MEDIA_URL", anime.url)
        })
    }

    fun search(query: String) {
        binding.shimmerView.startShimmer()
        binding.shimmerView.visibility = View.VISIBLE
        binding.rvHomeContent.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val results = Lk21Parser.search(query, 1)
                homeAdapter.setSearchResult(query, results)
            } catch (e: Exception) {
                Log.e("HomeFragment", "Search error", e)
            } finally {
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.rvHomeContent.visibility = View.VISIBLE
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
        if (forceRefresh || homeAdapter.itemCount <= 1) {
            binding.shimmerView.startShimmer()
            binding.shimmerView.visibility = View.VISIBLE
            binding.rvHomeContent.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                val rankings = Lk21Parser.getRankings(forceRefresh)
                val latest = Lk21Parser.getLatest(1, forceRefresh)
                val history = storage.getHistory()
                currentPage = 1
                hasMorePages = latest.isNotEmpty()
                homeAdapter.setData(rankings.take(8), latest, history)
            } catch (e: Exception) {
                Log.e("HomeFragment", "Load error", e)
            } finally {
                binding.shimmerView.stopShimmer()
                binding.shimmerView.visibility = View.GONE
                binding.rvHomeContent.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun loadNextPage() {
        if (isLoadingMore || !hasMorePages) return
        isLoadingMore = true
        homeAdapter.setLoadingMore(true)

        lifecycleScope.launch {
            try {
                val nextPage = currentPage + 1
                val items = Lk21Parser.getLatest(nextPage)
                if (items.isEmpty()) {
                    hasMorePages = false
                } else {
                    currentPage = nextPage
                    homeAdapter.setLoadingMore(false)
                    homeAdapter.appendAnime(items)
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Load more error", e)
            } finally {
                isLoadingMore = false
                homeAdapter.setLoadingMore(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
