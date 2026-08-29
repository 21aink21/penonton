package com.donghuaz.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.donghuaz.R
import com.donghuaz.data.parser.DonghuaParser
import com.donghuaz.databinding.FragmentRankingBinding
import com.donghuaz.ui.activity.DetailActivity
import com.donghuaz.ui.adapter.RankingAdapter
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rankingAdapter = RankingAdapter { anime ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("ANIME_ID", anime.id)
                putExtra("ANIME_TITLE", anime.title)
                putExtra("ANIME_POSTER", anime.poster)
            }
            startActivity(intent)
        }

        binding.rvRanking.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rankingAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }

        binding.swipeRefreshRanking.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshRanking.setOnRefreshListener { loadRankings(forceRefresh = true) }

        loadRankings(forceRefresh = false)
    }

    private fun loadRankings(forceRefresh: Boolean = false) {
        if (forceRefresh || rankingAdapter.itemCount == 0) {
            binding.shimmerRanking.startShimmer()
            binding.shimmerRanking.visibility = View.VISIBLE
            binding.rvRanking.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val rankings = DonghuaParser.getRankings(forceRefresh)
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
