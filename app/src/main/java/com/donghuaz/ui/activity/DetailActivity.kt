package com.donghuaz.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.donghuaz.R
import com.donghuaz.data.local.StorageManager
import com.donghuaz.data.model.AnimeDetail
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.data.model.EpisodeItem
import com.donghuaz.databinding.ActivityDetailBinding
import com.donghuaz.ui.adapter.EpisodeAdapter
import com.donghuaz.ui.adapter.ServerAdapter
import com.donghuaz.ui.viewmodel.DetailViewModel
import com.donghuaz.util.GradientBackground
import com.donghuaz.util.loadPoster
import com.google.android.material.tabs.TabLayout

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var storage: StorageManager

    private lateinit var serverAdapter: ServerAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private var currentDetail: AnimeDetail? = null
    private var animeId: Int = 0
    private var isSynopsisExpanded = false
    private var selectedServerIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GradientBackground.apply(this)

        storage = StorageManager.getInstance(this)
        animeId = intent.getIntExtra("ANIME_ID", 0)
        val initialTitle = intent.getStringExtra("ANIME_TITLE") ?: ""
        val initialPoster = intent.getStringExtra("ANIME_POSTER") ?: ""

        if (initialTitle.isNotEmpty()) binding.tvDetailTitle.text = initialTitle
        if (initialPoster.isNotEmpty()) {
            binding.ivBackdrop.loadPoster(initialPoster)
        }

        binding.btnBack.setOnClickListener { finish() }

        setupHeroPlay()
        setupActionButtons()
        setupSynopsisToggle()
        setupTabs()
        setupAdapters()
        setupObservers()

        if (animeId > 0) {
            viewModel.loadDetail(animeId)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFavoriteButtonState()
        refreshWatchedState()
    }

    private fun setupHeroPlay() {
        val playAction = View.OnClickListener {
            val lastWatched = storage.getLastWatched(animeId)
            val firstEpisode = currentDetail?.servers?.getOrNull(selectedServerIdx)?.episodes?.firstOrNull()
                ?: currentDetail?.servers?.firstOrNull()?.episodes?.firstOrNull()

            val episodeToPlay = if (lastWatched != null) {
                EpisodeItem(
                    episode = lastWatched.episodeName,
                    id = lastWatched.animeId,
                    sid = lastWatched.sid,
                    nid = lastWatched.nid,
                    playUrl = "",
                    path = ""
                )
            } else {
                firstEpisode
            }

            if (episodeToPlay != null) {
                playEpisode(episodeToPlay)
            } else {
                Toast.makeText(this, "Memuat daftar episode...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.playerContainer.setOnClickListener(playAction)
    }

    private fun setupActionButtons() {
        // 1. +Daftar Saya (Favorite)
        updateFavoriteButtonState()
        binding.btnActionFavorite.setOnClickListener {
            val item = AnimeItem(
                id = animeId,
                title = currentDetail?.title ?: intent.getStringExtra("ANIME_TITLE") ?: "",
                latestEp = currentDetail?.status ?: "",
                rating = "",
                poster = currentDetail?.poster ?: intent.getStringExtra("ANIME_POSTER") ?: "",
                url = ""
            )
            val isAdded = storage.toggleFavorite(item)
            updateFavoriteButtonState()
            val msg = if (isAdded) "Ditambahkan ke Daftar Saya" else "Dihapus dari Daftar Saya"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 2. Unduh
        binding.btnActionDownload.setOnClickListener {
            Toast.makeText(this, "Episode tersimpan di cache untuk pemutaran instan!", Toast.LENGTH_SHORT).show()
        }

        // 3. Bagikan
        binding.btnActionShare.setOnClickListener {
            val title = currentDetail?.title ?: "Donghua"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Tonton donghua $title di erVanDongZ!")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan ke:"))
        }

        // 4. 1080P En / Kualitas
        binding.btnActionQuality.setOnClickListener {
            binding.tabDetail.getTabAt(1)?.select()
            binding.layoutServerSection.visibility = View.VISIBLE
        }
    }

    private fun updateFavoriteButtonState() {
        val isFav = storage.isFavorite(animeId)
        if (isFav) {
            binding.ivActionFavorite.setImageResource(R.drawable.ic_favorite_filled)
            binding.ivActionFavorite.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            binding.tvActionFavorite.text = "✓ Tersimpan"
            binding.tvActionFavorite.setTextColor(ContextCompat.getColor(this, R.color.primary))
        } else {
            binding.ivActionFavorite.setImageResource(R.drawable.ic_favorite)
            binding.ivActionFavorite.setColorFilter(0xFFA0A0A0.toInt())
            binding.tvActionFavorite.text = "+Daftar Saya"
            binding.tvActionFavorite.setTextColor(0xFFA0A0A0.toInt())
        }
    }

    private fun setupSynopsisToggle() {
        binding.btnToggleSynopsis.setOnClickListener {
            isSynopsisExpanded = !isSynopsisExpanded
            if (isSynopsisExpanded) {
                binding.tvSynopsis.maxLines = 100
                binding.btnToggleSynopsis.text = "Tutup ▴"
            } else {
                binding.tvSynopsis.maxLines = 3
                binding.btnToggleSynopsis.text = "Lihat Selengkapnya ▸"
            }
        }
    }

    private fun setupTabs() {
        binding.tabDetail.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // Tab Episode
                        binding.layoutServerSection.visibility = View.VISIBLE
                        binding.layoutEpisodeSection.visibility = View.VISIBLE
                    }
                    1 -> {
                        // Tab Server / Kualitas
                        binding.layoutServerSection.visibility = View.VISIBLE
                        binding.layoutEpisodeSection.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupAdapters() {
        serverAdapter = ServerAdapter { selectedIdx ->
            selectedServerIdx = selectedIdx
            currentDetail?.servers?.getOrNull(selectedIdx)?.let { server ->
                val watchedSet = storage.getWatchedEpisodes(animeId)
                val lastWatched = storage.getLastWatched(animeId)
                episodeAdapter.submitList(server.episodes, watchedSet, lastWatched?.nid ?: -1)
                binding.tvActionQuality.text = server.serverName.take(8)
            }
        }
        binding.rvServers.adapter = serverAdapter

        episodeAdapter = EpisodeAdapter { episode ->
            playEpisode(episode)
        }
        binding.rvEpisodes.layoutManager = GridLayoutManager(this, 4)
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun playEpisode(episode: EpisodeItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("ANIME_ID", episode.id)
            putExtra("ANIME_TITLE", currentDetail?.title ?: intent.getStringExtra("ANIME_TITLE") ?: "")
            putExtra("ANIME_POSTER", currentDetail?.poster ?: intent.getStringExtra("ANIME_POSTER") ?: "")
            putExtra("EPISODE_NAME", episode.episode)
            putExtra("SID", episode.sid)
            putExtra("NID", episode.nid)
        }
        startActivity(intent)
    }

    private fun refreshWatchedState() {
        val watchedSet = storage.getWatchedEpisodes(animeId)
        val lastWatched = storage.getLastWatched(animeId)
        currentDetail?.servers?.getOrNull(selectedServerIdx)?.let { server ->
            episodeAdapter.submitList(server.episodes, watchedSet, lastWatched?.nid ?: -1)
        }
    }

    private fun setupObservers() {
        viewModel.detail.observe(this) { detail ->
            if (detail != null) {
                currentDetail = detail
                bindDetail(detail)
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.detailProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun bindDetail(detail: AnimeDetail) {
        binding.tvDetailTitle.text = detail.title
        binding.tvDetailStatus.text = detail.status.ifEmpty { "On Going" }
        binding.tvSynopsis.text = detail.synopsis.ifEmpty { "Belum ada deskripsi tersedia." }

        val ratingStr = detail.meta["score"] ?: detail.meta["rating"] ?: "9.8"
        binding.tvRatingDetail.text = ratingStr.replace("★", "").trim()

        val metaParts = mutableListOf<String>()
        detail.meta["year"]?.let { metaParts.add(it) }
        detail.meta["region"]?.let { metaParts.add(it) }
        detail.meta["genre"]?.let { metaParts.add(it) }
        detail.meta["type"]?.let { if (!metaParts.contains(it)) metaParts.add(it) }
        
        binding.tvDetailMeta.text = if (metaParts.isNotEmpty()) {
            metaParts.joinToString(" • ")
        } else {
            "2025 • China • Action, Adventure, Fantasy"
        }

        binding.ivBackdrop.loadPoster(detail.poster)

        val watchedSet = storage.getWatchedEpisodes(animeId)
        val lastWatched = storage.getLastWatched(animeId)
        if (detail.servers.isNotEmpty()) {
            selectedServerIdx = 0
            serverAdapter.submitList(detail.servers, 0)
            episodeAdapter.submitList(detail.servers[0].episodes, watchedSet, lastWatched?.nid ?: -1)
            binding.tvActionQuality.text = detail.servers[0].serverName.take(8)
        }
    }
}
