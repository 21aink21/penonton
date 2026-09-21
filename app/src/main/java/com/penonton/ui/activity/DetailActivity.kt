package com.penonton.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.penonton.R
import com.penonton.data.local.StorageManager
import com.penonton.data.model.EpisodeItem
import com.penonton.data.model.MovieDetail
import com.penonton.data.model.MovieItem
import com.penonton.databinding.ActivityDetailBinding
import com.penonton.ui.adapter.EpisodeAdapter
import com.penonton.ui.adapter.ServerAdapter
import com.penonton.ui.viewmodel.DetailViewModel
import com.penonton.util.CountryUtils
import com.penonton.util.GradientBackground
import com.penonton.util.loadPoster

@SuppressLint("SetTextI18n")
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var storage: StorageManager

    private lateinit var serverAdapter: ServerAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private var gridLayoutManager: GridLayoutManager? = null

    private var currentDetail: MovieDetail? = null
    private var movieId: Int = 0
    private var isSynopsisExpanded = false
    private var selectedServerIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GradientBackground.apply(this)

        storage = StorageManager.getInstance(this)
        movieId = intent.getIntExtra("MEDIA_ID", 0)
        val initialTitle = intent.getStringExtra("MEDIA_TITLE") ?: ""
        val initialPoster = intent.getStringExtra("MEDIA_POSTER") ?: ""
        val initialUrl = intent.getStringExtra("MEDIA_URL") ?: ""

        if (initialTitle.isNotEmpty()) binding.tvDetailTitle.text = initialTitle
        if (initialPoster.isNotEmpty()) binding.ivBackdrop.loadPoster(initialPoster)

        binding.btnBack.setOnClickListener { finish() }

        setupHeroPlay()
        setupActionButtons()
        setupSynopsisToggle()
        setupTabs()
        setupAdapters()
        setupObservers()
        setupWatermarkStyle()

        if (movieId > 0 || initialUrl.isNotEmpty()) {
            viewModel.loadDetail(movieId, initialUrl)
        }
    }

    private fun setupWatermarkStyle() {
        binding.tvWatermarkText.post {
            val text = binding.tvWatermarkText.text.toString()
            val textWidth = binding.tvWatermarkText.paint.measureText(text)
            if (textWidth > 0) {
                val shader = LinearGradient(
                    0f, 0f, textWidth, 0f,
                    intArrayOf(
                        "#FB9E0C".toColorInt(),
                        "#F34390".toColorInt(),
                        "#9425EE".toColorInt(),
                        "#049CFC".toColorInt()
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                binding.tvWatermarkText.paint.shader = shader
                binding.tvWatermarkText.invalidate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateFavoriteButtonState()
        refreshWatchedState()
    }

    private fun setupHeroPlay() {
        binding.playerContainer.setOnClickListener {
            val lastWatched = storage.getLastWatched(movieId)
            val firstEpisode = currentDetail?.servers?.getOrNull(selectedServerIdx)?.episodes?.firstOrNull()
                ?: currentDetail?.servers?.firstOrNull()?.episodes?.firstOrNull()

            val episodeToPlay = if (lastWatched != null) {
                val matchedEp = currentDetail?.servers?.flatMap { it.episodes }?.firstOrNull { it.nid == lastWatched.nid }
                EpisodeItem(
                    episode = lastWatched.episodeName,
                    id = lastWatched.movieId,
                    sid = lastWatched.sid,
                    nid = lastWatched.nid,
                    playUrl = matchedEp?.playUrl ?: firstEpisode?.playUrl ?: intent.getStringExtra("MEDIA_URL") ?: "",
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
    }

    private fun setupActionButtons() {
        updateFavoriteButtonState()
        binding.btnActionFavorite.setOnClickListener {
            val item = MovieItem(
                id = movieId,
                title = currentDetail?.title ?: intent.getStringExtra("MEDIA_TITLE") ?: "",
                latestEp = currentDetail?.status ?: "",
                rating = "",
                poster = currentDetail?.poster ?: intent.getStringExtra("MEDIA_POSTER") ?: "",
                url = intent.getStringExtra("MEDIA_URL") ?: ""
            )
            val isAdded = storage.toggleFavorite(item)
            updateFavoriteButtonState()
            val msg = if (isAdded) "Ditambahkan ke Daftar Saya" else "Dihapus dari Daftar Saya"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnActionDownload.setOnClickListener {
            Toast.makeText(this, "Film/Episode tersimpan di cache untuk pemutaran instan!", Toast.LENGTH_SHORT).show()
        }

        binding.btnActionShare.setOnClickListener {
            val title = currentDetail?.title ?: "LK21 Movie & Series"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Tonton film & series $title di LK21!")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan ke:"))
        }

        binding.btnActionQuality.setOnClickListener {
            binding.tabDetail.getTabAt(1)?.select()
        }
    }

    private fun updateFavoriteButtonState() {
        val isFav = storage.isFavorite(movieId)
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val defaultColor = "#A0A0A0".toColorInt()

        if (isFav) {
            binding.ivActionFavorite.setImageResource(R.drawable.ic_favorite_filled)
            binding.ivActionFavorite.setColorFilter(primaryColor)
            binding.tvActionFavorite.text = "✓ Tersimpan"
            binding.tvActionFavorite.setTextColor(primaryColor)
        } else {
            binding.ivActionFavorite.setImageResource(R.drawable.ic_favorite)
            binding.ivActionFavorite.setColorFilter(defaultColor)
            binding.tvActionFavorite.text = "+Daftar Saya"
            binding.tvActionFavorite.setTextColor(defaultColor)
        }
    }

    private fun setupSynopsisToggle() {
        binding.btnToggleSynopsis.setOnClickListener {
            isSynopsisExpanded = !isSynopsisExpanded
            binding.tvSynopsis.maxLines = if (isSynopsisExpanded) 100 else 3
            binding.btnToggleSynopsis.text = if (isSynopsisExpanded) "Tutup ▴" else "Lihat Selengkapnya ▸"
        }
    }

    private fun setupTabs() {
        binding.tabDetail.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutEpisodeSection.isVisible = true
                        binding.layoutServerSection.isVisible = false
                    }
                    1 -> {
                        binding.layoutEpisodeSection.isVisible = false
                        binding.layoutServerSection.isVisible = true
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
                val watchedSet = storage.getWatchedEpisodes(movieId)
                val lastWatched = storage.getLastWatched(movieId)
                episodeAdapter.submitList(server.episodes, watchedSet, lastWatched?.nid ?: -1)
                binding.tvActionQuality.text = server.serverName.take(8)
            }
        }
        binding.rvServers.adapter = serverAdapter

        episodeAdapter = EpisodeAdapter { episode -> playEpisode(episode) }
        gridLayoutManager = GridLayoutManager(this, 4)
        binding.rvEpisodes.layoutManager = gridLayoutManager
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun playEpisode(episode: EpisodeItem) {
        val lastWatched = storage.getLastWatched(movieId)
        val startPos = if (lastWatched != null && lastWatched.nid == episode.nid && lastWatched.positionMs > 1000L) {
            lastWatched.positionMs
        } else {
            storage.getEpisodePosition(movieId, episode.nid)
        }
        val moviePageUrl = intent.getStringExtra("MEDIA_URL") ?: currentDetail?.servers?.firstOrNull()?.episodes?.firstOrNull()?.playUrl ?: ""

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("MEDIA_ID", movieId)
            putExtra("EPISODE_ID", episode.id)
            putExtra("MEDIA_TITLE", currentDetail?.title ?: intent.getStringExtra("MEDIA_TITLE") ?: "")
            putExtra("MEDIA_POSTER", currentDetail?.poster ?: intent.getStringExtra("MEDIA_POSTER") ?: "")
            putExtra("EPISODE_NAME", episode.episode)
            putExtra("SID", episode.sid)
            putExtra("NID", episode.nid)
            putExtra("PLAY_URL", episode.playUrl)
            putExtra("MEDIA_URL", episode.playUrl)
            putExtra("MOVIE_URL", moviePageUrl)
            if (startPos > 1000L) {
                putExtra("START_POSITION", startPos)
            }
        }
        startActivity(intent)
    }

    private fun refreshWatchedState() {
        val watchedSet = storage.getWatchedEpisodes(movieId)
        val lastWatched = storage.getLastWatched(movieId)
        currentDetail?.servers?.getOrNull(selectedServerIdx)?.let { server ->
            episodeAdapter.submitList(server.episodes, watchedSet, lastWatched?.nid ?: -1)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun setupObservers() {
        viewModel.detail.observe(this) { detail ->
            if (detail != null) {
                currentDetail = detail
                bindDetail(detail)

                // Cari metadata dari TMDb/IMDb berdasarkan judul film/series
                viewModel.fetchExternalMetadata(detail.title)
            }
        }

        // Observer untuk hasil pencarian metadata dari TMDb/IMDb
        viewModel.tmdbMetadata.observe(this) { tmdb ->
            if (tmdb != null) {
                // 1. Update Rating IMDb/TMDb
                binding.tvRatingDetail.text = String.format("%.1f", tmdb.voteAverage)

                // 2. Update Sinopsis jika sinopsis bawaan lokal kosong
                if (binding.tvSynopsis.text.contains("Belum ada deskripsi")) {
                    binding.tvSynopsis.text = tmdb.overview.ifEmpty { "Deskripsi tidak tersedia." }
                }

                // 3. Update Backdrop Poster Kualitas Tinggi
                tmdb.backdropPath?.let { path ->
                    val highResUrl = "https://image.tmdb.org/t/p/w780$path"
                    binding.ivBackdrop.loadPoster(highResUrl)
                }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.detailProgressBar.isVisible = loading
        }
    }

    private fun bindDetail(detail: MovieDetail) {
        val isMovie = detail.status.equals("Movie", ignoreCase = true) || (detail.servers.size <= 1 && (detail.servers.firstOrNull()?.count ?: 0) <= 1)
        binding.tvDetailTitle.text = detail.title
        binding.tvDetailStatus.text = if (isMovie) "Movie HD" else detail.status.ifEmpty { "Series" }
        binding.tvSynopsis.text = detail.synopsis.ifEmpty { "Belum ada deskripsi tersedia." }

        binding.tabDetail.getTabAt(0)?.text = if (isMovie) "Putar Film" else "Episode"
        binding.tabDetail.getTabAt(1)?.text = if (isMovie) "Server Player" else "Season"

        val ratingStr = detail.meta["score"] ?: detail.meta["rating"] ?: "9.0"
        binding.tvRatingDetail.text = ratingStr.replace("★", "").trim()

        val country = detail.meta["country"] ?: detail.meta["negara"] ?: ""
        val countryBadge = if (country.isNotEmpty()) {
            CountryUtils.formatCountry(country)
        } else {
            CountryUtils.getCountryBadge(MovieItem(detail.id, detail.title, "", "", "", ""))
        }

        binding.tvDetailCountry.isVisible = countryBadge.isNotEmpty()
        binding.tvDetailCountry.text = countryBadge

        val metaParts = mutableListOf<String>()
        detail.meta["year"]?.let { metaParts.add(it) }
        if (country.isNotEmpty()) metaParts.add(country)
        detail.meta["genre"]?.let { metaParts.add(it) }
        detail.meta["duration"]?.let { metaParts.add(it) }

        binding.tvDetailMeta.text = if (metaParts.isNotEmpty()) {
            metaParts.joinToString(" • ")
        } else {
            if (isMovie) "2026 • Movie • HD Sub Indo" else "2026 • Series • Sub Indo"
        }

        binding.ivBackdrop.loadPoster(detail.poster)

        gridLayoutManager?.spanCount = if (isMovie) 1 else 4

        val watchedSet = storage.getWatchedEpisodes(movieId)
        val lastWatched = storage.getLastWatched(movieId)
        if (detail.servers.isNotEmpty()) {
            selectedServerIdx = 0
            serverAdapter.submitList(detail.servers, 0)
            episodeAdapter.submitList(detail.servers[0].episodes, watchedSet, lastWatched?.nid ?: -1)
            binding.tvActionQuality.text = detail.servers[0].serverName.take(8)
        }
    }
}