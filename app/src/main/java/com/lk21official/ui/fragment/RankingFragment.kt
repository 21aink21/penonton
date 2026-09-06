package com.lk21official.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lk21official.R
import com.lk21official.data.parser.DonghuaParser
import com.lk21official.databinding.FragmentRankingBinding
import com.lk21official.ui.activity.DetailActivity
import com.lk21official.ui.adapter.RankingAdapter
import kotlinx.coroutines.launch

class RankingFragment : Fragment() {

    private var _binding: FragmentRankingBinding? = null
    private val binding get() = _binding!!

    private lateinit var rankingAdapter: RankingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var currentType: String = "movie"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rankingAdapter = RankingAdapter { anime ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("ANIME_ID", anime.id)
                putExtra("ANIME_TITLE", anime.title)
                putExtra("ANIME_POSTER", anime.poster)
                putExtra("ANIME_URL", anime.url)
            }
            startActivity(intent)
        }

        binding.rvRanking.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rankingAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }

        setupTabs()

        binding.swipeRefreshRanking.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshRanking.setOnRefreshListener { loadRankings(forceRefresh = true) }

        loadRankings(forceRefresh = false)
    }

    private fun setupTabs() {
        binding.tabRankingType.removeAllTabs()
        binding.tabRankingType.addTab(binding.tabRankingType.newTab().setText("🎬 Top Movies"))
        binding.tabRankingType.addTab(binding.tabRankingType.newTab().setText("📺 Top Series"))

        binding.tabRankingType.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                currentType = if (tab?.position == 1) "series" else "movie"
                loadRankings(forceRefresh = false)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun loadRankings(forceRefresh: Boolean = false) {
        if (forceRefresh || rankingAdapter.itemCount == 0) {
            binding.shimmerRanking.startShimmer()
            binding.shimmerRanking.visibility = View.VISIBLE
            binding.rvRanking.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val rankings = DonghuaParser.getRankings(forceRefresh, currentType)
                rankingAdapter.submitList(rankings)
            } catch (_: Exception) {
            } finally {
                binding.shimmerRanking.stopShimmer()
                binding.shimmerRanking.visibility = View.GONE
                binding.rvRanking.visibility = View.VISIBLE
                binding.swipeRefreshRanking.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
