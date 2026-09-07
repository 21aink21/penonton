package com.penonton.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.penonton.data.local.StorageManager
import com.penonton.databinding.FragmentLibraryBinding
import com.penonton.ui.activity.DetailActivity
import com.penonton.ui.activity.PlayerActivity
import com.penonton.ui.adapter.MovieAdapter
import com.penonton.ui.adapter.HistoryAdapter
import com.google.android.material.tabs.TabLayout

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var storage: StorageManager
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var favoritesAdapter: MovieAdapter

    private var currentTab = 0 // 0=History, 1=Favorites

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        storage = StorageManager.getInstance(requireContext())

        setupAdapters()
        setupTabs()

        binding.btnClearHistory.setOnClickListener {
            if (currentTab == 0) {
                storage.clearHistory()
                refreshData()
            }
        }
    }

    private fun setupAdapters() {
        historyAdapter = HistoryAdapter { item ->
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra("MEDIA_ID", item.movieId)
                putExtra("MEDIA_TITLE", item.title)
                putExtra("MEDIA_POSTER", item.poster)
                putExtra("EPISODE_NAME", item.episodeName)
                putExtra("SID", item.sid)
                putExtra("NID", item.nid)
                putExtra("PLAY_URL", item.playUrl)
                putExtra("MEDIA_URL", item.playUrl)
                putExtra("MOVIE_URL", item.movieUrl)
                putExtra("START_POSITION", item.positionMs)
            }
            startActivity(intent)
        }

        favoritesAdapter = MovieAdapter { anime ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("MEDIA_ID", anime.id)
                putExtra("MEDIA_TITLE", anime.title)
                putExtra("MEDIA_POSTER", anime.poster)
                putExtra("MEDIA_URL", anime.url)
            }
            startActivity(intent)
        }
    }

    private fun setupTabs() {
        binding.tabLibrary.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                refreshData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        if (currentTab == 0) {
            // Watch History
            binding.btnClearHistory.visibility = View.VISIBLE
            binding.rvLibrary.layoutManager = LinearLayoutManager(requireContext())
            binding.rvLibrary.adapter = historyAdapter

            val history = storage.getHistory()
            historyAdapter.submitList(history)

            binding.tvEmptyLibrary.text = "Belum ada riwayat tontonan"
            binding.tvEmptyLibrary.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
            binding.rvLibrary.visibility = if (history.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            // Favorites
            binding.btnClearHistory.visibility = View.GONE
            binding.rvLibrary.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvLibrary.adapter = favoritesAdapter

            val favorites = storage.getFavorites()
            favoritesAdapter.submitList(favorites)

            binding.tvEmptyLibrary.text = "Belum ada film atau serial favorit"
            binding.tvEmptyLibrary.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
            binding.rvLibrary.visibility = if (favorites.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
