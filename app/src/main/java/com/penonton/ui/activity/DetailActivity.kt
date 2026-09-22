package com.penonton.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.util.Log
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

    companion object {

        private const val TAG = "DetailActivity"

        private const val TMDB_DEBUG_TAG = "TMDB_DEBUG"
        private const val SYNOPSIS_DEBUG_TAG = "SYNOPSIS_DEBUG"

        const val EXTRA_MEDIA_ID = "MEDIA_ID"
        const val EXTRA_MEDIA_TITLE = "MEDIA_TITLE"
        const val EXTRA_MEDIA_POSTER = "MEDIA_POSTER"
        const val EXTRA_MEDIA_URL = "MEDIA_URL"

        private const val EXTRA_EPISODE_ID = "EPISODE_ID"
        private const val EXTRA_EPISODE_NAME = "EPISODE_NAME"
        private const val EXTRA_SID = "SID"
        private const val EXTRA_NID = "NID"
        private const val EXTRA_PLAY_URL = "PLAY_URL"
        private const val EXTRA_MOVIE_URL = "MOVIE_URL"
        private const val EXTRA_START_POSITION = "START_POSITION"
    }

    // ============================================================
    // Binding & ViewModel
    // ============================================================

    private lateinit var binding: ActivityDetailBinding

    private val viewModel: DetailViewModel by viewModels()

    private lateinit var storage: StorageManager

    // ============================================================
    // Adapter
    // ============================================================

    private lateinit var serverAdapter: ServerAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    private var gridLayoutManager: GridLayoutManager? = null

    // ============================================================
    // State
    // ============================================================

    private var currentDetail: MovieDetail? = null

    private var movieId: Int = 0

    private var isSynopsisExpanded = false

    private var selectedServerIdx = 0

    /*
     * URL halaman detail film/series.
     *
     * Ini berbeda dengan URL player episode.
     */
    private var mediaPageUrl: String = ""

    /*
     * Poster awal dari Intent.
     *
     * Digunakan sebagai fallback sebelum detail parser selesai.
     */
    private var initialPoster: String = ""

    // ============================================================
    // Lifecycle
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityDetailBinding.inflate(layoutInflater)

        setContentView(binding.root)

        GradientBackground.apply(this)

        initializeData()
        setupViews()
        setupObservers()
        loadDetail()
    }

    override fun onResume() {
        super.onResume()

        if (::storage.isInitialized) {

            updateFavoriteButtonState()

            refreshWatchedState()
        }
    }

    // ============================================================
    // Initialization
    // ============================================================

    private fun initializeData() {

        storage =
            StorageManager.getInstance(this)

        movieId =
            intent.getIntExtra(
                EXTRA_MEDIA_ID,
                0
            )

        mediaPageUrl =
            intent.getStringExtra(
                EXTRA_MEDIA_URL
            )
                ?.trim()
                .orEmpty()

        initialPoster =
            intent.getStringExtra(
                EXTRA_MEDIA_POSTER
            )
                ?.trim()
                .orEmpty()

        val initialTitle =
            intent.getStringExtra(
                EXTRA_MEDIA_TITLE
            )
                ?.trim()
                .orEmpty()

        /*
         * Tampilkan title dari Intent terlebih dahulu
         * agar UI tidak kosong ketika network masih loading.
         */
        if (initialTitle.isNotBlank()) {

            binding.tvDetailTitle.text =
                initialTitle
        }

        /*
         * MovieDetail belum memiliki backdrop.
         *
         * Karena itu poster digunakan sebagai background
         * sementara.
         *
         * Jika TMDb mempunyai backdrop, observer TMDb
         * akan menggantinya.
         */
        if (initialPoster.isNotBlank()) {

            binding.ivBackdrop.loadPoster(
                initialPoster
            )
        }
    }

    // ============================================================
    // Setup Views
    // ============================================================

    private fun setupViews() {

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupHeroPlay()
        setupActionButtons()
        setupSynopsisToggle()
        setupTabs()
        setupAdapters()
        setupWatermarkStyle()
    }

    // ============================================================
    // Load Detail
    // ============================================================

    private fun loadDetail() {

        if (
            movieId > 0 ||
            mediaPageUrl.isNotBlank()
        ) {

            viewModel.loadDetail(
                movieId = movieId,
                url = mediaPageUrl.takeIf {
                    it.isNotBlank()
                }
            )

        } else {

            Toast.makeText(
                this,
                "Data film tidak ditemukan",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }

    // ============================================================
    // Watermark
    // ============================================================

    private fun setupWatermarkStyle() {

        binding.tvWatermarkText.post {

            val text =
                binding.tvWatermarkText.text
                    ?.toString()
                    .orEmpty()

            val textWidth =
                binding.tvWatermarkText.paint
                    .measureText(text)

            if (textWidth <= 0f) {
                return@post
            }

            val shader =
                LinearGradient(
                    0f,
                    0f,
                    textWidth,
                    0f,
                    intArrayOf(
                        "#FB9E0C".toColorInt(),
                        "#F34390".toColorInt(),
                        "#9425EE".toColorInt(),
                        "#049CFC".toColorInt()
                    ),
                    floatArrayOf(
                        0f,
                        0.35f,
                        0.70f,
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )

            binding.tvWatermarkText.paint.shader =
                shader

            binding.tvWatermarkText.invalidate()
        }
    }

    // ============================================================
    // Hero Player
    // ============================================================

    private fun setupHeroPlay() {

        binding.playerContainer.setOnClickListener {

            val episode =
                getEpisodeToPlay()

            if (episode != null) {

                playEpisode(episode)

            } else {

                Toast.makeText(
                    this,
                    "Memuat daftar episode...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Menentukan episode yang akan diputar.
     *
     * Prioritas:
     *
     * 1. Episode terakhir ditonton jika masih tersedia.
     * 2. Episode pertama pada server terpilih.
     * 3. Episode pertama dari server mana pun.
     */
    private fun getEpisodeToPlay(): EpisodeItem? {

        val detail =
            currentDetail
                ?: return null

        val lastWatched =
            storage.getLastWatched(
                movieId
            )

        /*
         * Coba resume episode terakhir.
         */
        if (lastWatched != null) {

            val matchedEpisode =
                detail.servers
                    .asSequence()
                    .flatMap { server ->
                        server.episodes.asSequence()
                    }
                    .firstOrNull { episode ->

                        episode.sid ==
                                lastWatched.sid &&
                                episode.nid ==
                                lastWatched.nid
                    }

            if (matchedEpisode != null) {

                Log.d(
                    TAG,
                    "Hero play: resume " +
                            "${matchedEpisode.episode}, " +
                            "sid=${matchedEpisode.sid}, " +
                            "nid=${matchedEpisode.nid}"
                )

                return matchedEpisode
            }
        }

        /*
         * Jika tidak ada history yang cocok,
         * gunakan server yang sedang dipilih.
         */
        val selectedEpisode =
            detail.servers
                .getOrNull(
                    selectedServerIdx
                )
                ?.episodes
                ?.firstOrNull()

        if (selectedEpisode != null) {
            return selectedEpisode
        }

        /*
         * Fallback ke episode pertama dari server mana pun.
         */
        return detail.servers
            .asSequence()
            .flatMap { server ->
                server.episodes.asSequence()
            }
            .firstOrNull()
    }

    // ============================================================
    // Action Buttons
    // ============================================================

    private fun setupActionButtons() {

        updateFavoriteButtonState()

        // --------------------------------------------------------
        // Favorite
        // --------------------------------------------------------

        binding.btnActionFavorite.setOnClickListener {

            val item =
                MovieItem(
                    id = movieId,

                    title =
                        currentDetail
                            ?.title
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: intent.getStringExtra(
                                EXTRA_MEDIA_TITLE
                            ).orEmpty(),

                    latestEp =
                        currentDetail
                            ?.status
                            .orEmpty(),

                    rating =
                        getLocalRating(),

                    poster =
                        currentDetail
                            ?.poster
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: initialPoster,

                    url =
                        mediaPageUrl
                )

            val isAdded =
                storage.toggleFavorite(
                    item
                )

            updateFavoriteButtonState()

            val message =
                if (isAdded) {
                    "Ditambahkan ke Daftar Saya"
                } else {
                    "Dihapus dari Daftar Saya"
                }

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }

        // --------------------------------------------------------
        // Download
        // --------------------------------------------------------

        binding.btnActionDownload.setOnClickListener {

            Toast.makeText(
                this,
                "Film/Episode tersimpan di cache untuk pemutaran instan!",
                Toast.LENGTH_SHORT
            ).show()
        }

        // --------------------------------------------------------
        // Share
        // --------------------------------------------------------

        binding.btnActionShare.setOnClickListener {

            val title =
                currentDetail
                    ?.title
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: intent.getStringExtra(
                        EXTRA_MEDIA_TITLE
                    )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                    ?: "LK21 Movie & Series"

            val shareIntent =
                Intent(
                    Intent.ACTION_SEND
                ).apply {

                    type =
                        "text/plain"

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        title
                    )

                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Tonton film & series $title di LK21!"
                    )
                }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Bagikan ke:"
                )
            )
        }

        // --------------------------------------------------------
        // Quality / Server
        // --------------------------------------------------------

        binding.btnActionQuality.setOnClickListener {

            binding.tabDetail
                .getTabAt(1)
                ?.select()
        }
    }

    // ============================================================
    // Favorite State
    // ============================================================

    private fun updateFavoriteButtonState() {

        if (!::storage.isInitialized) {
            return
        }

        if (movieId <= 0) {
            return
        }

        val isFavorite =
            storage.isFavorite(
                movieId
            )

        val primaryColor =
            ContextCompat.getColor(
                this,
                R.color.primary
            )

        val defaultColor =
            "#A0A0A0".toColorInt()

        if (isFavorite) {

            binding.ivActionFavorite.setImageResource(
                R.drawable.ic_favorite_filled
            )

            binding.ivActionFavorite.setColorFilter(
                primaryColor
            )

            binding.tvActionFavorite.text =
                "✓ Tersimpan"

            binding.tvActionFavorite.setTextColor(
                primaryColor
            )

        } else {

            binding.ivActionFavorite.setImageResource(
                R.drawable.ic_favorite
            )

            binding.ivActionFavorite.setColorFilter(
                defaultColor
            )

            binding.tvActionFavorite.text =
                "+Daftar Saya"

            binding.tvActionFavorite.setTextColor(
                defaultColor
            )
        }
    }

    // ============================================================
    // Synopsis
    // ============================================================

    private fun setupSynopsisToggle() {

        binding.btnToggleSynopsis.setOnClickListener {

            /*
             * Jangan izinkan toggle kalau teks memang <= 3 baris.
             */
            if (
                !binding.btnToggleSynopsis.isVisible
            ) {
                return@setOnClickListener
            }

            isSynopsisExpanded =
                !isSynopsisExpanded

            if (isSynopsisExpanded) {

                expandSynopsis()

            } else {

                collapseSynopsis()
            }
        }
    }

    /**
     * Menampilkan seluruh synopsis.
     */
    private fun expandSynopsis() {

        isSynopsisExpanded = true

        binding.tvSynopsis.maxLines =
            Int.MAX_VALUE

        binding.tvSynopsis.ellipsize =
            null

        binding.btnToggleSynopsis.text =
            "Tutup ▴"

        binding.tvSynopsis.requestLayout()
    }

    /**
     * Membatasi synopsis menjadi 3 baris.
     */
    private fun collapseSynopsis() {

        isSynopsisExpanded = false

        binding.tvSynopsis.maxLines =
            3

        binding.tvSynopsis.ellipsize =
            TextUtils.TruncateAt.END

        binding.btnToggleSynopsis.text =
            "Lihat Selengkapnya ▸"

        binding.tvSynopsis.requestLayout()
    }

    /**
     * Mengatur synopsis.
     *
     * Tombol tidak langsung ditentukan berdasarkan panjang karakter.
     * Setelah TextView mendapatkan ukuran sebenarnya, kita menghitung
     * jumlah baris menggunakan StaticLayout.
     */
    private fun setSynopsis(
        text: String
    ) {

        isSynopsisExpanded = false

        binding.tvSynopsis.maxLines =
            3

        binding.tvSynopsis.ellipsize =
            TextUtils.TruncateAt.END

        binding.tvSynopsis.text =
            text.trim()

        /*
         * Tunggu sampai ukuran TextView sudah tersedia.
         */
        binding.tvSynopsis.post {

            updateSynopsisToggleVisibility()
        }
    }

    /**
     * Menentukan apakah synopsis sebenarnya lebih dari 3 baris.
     *
     * Penting:
     *
     * Kita TIDAK menggunakan:
     *
     * binding.tvSynopsis.lineCount
     *
     * secara langsung karena TextView sedang dibatasi maxLines=3.
     *
     * Sebagai gantinya StaticLayout menghitung seluruh teks dengan
     * lebar TextView yang sebenarnya.
     *
     * Ini bekerja baik untuk:
     *
     * - paragraf panjang
     * - teks tanpa newline
     * - teks dengan newline
     * - synopsis hasil scraper
     * - synopsis TMDb
     */
    private fun updateSynopsisToggleVisibility() {

        binding.tvSynopsis.post {

            val text =
                binding.tvSynopsis.text
                    ?.toString()
                    .orEmpty()

            /*
             * Tidak ada synopsis.
             */
            if (text.isBlank()) {

                binding.btnToggleSynopsis.isVisible =
                    false

                return@post
            }

            /*
             * Pastikan width sudah tersedia.
             */
            val availableWidth =
                binding.tvSynopsis.width

            if (availableWidth <= 0) {

                /*
                 * Layout belum selesai.
                 * Coba lagi pada frame berikutnya.
                 */
                binding.tvSynopsis.post {

                    updateSynopsisToggleVisibility()
                }

                return@post
            }

            /*
             * Gunakan Paint milik TextView agar:
             *
             * - font
             * - textSize
             * - typeface
             * - style
             *
             * sama dengan tampilan sebenarnya.
             */
            val textPaint =
                binding.tvSynopsis.paint

            /*
             * StaticLayout menghitung seluruh teks tanpa
             * dibatasi maxLines.
             */
            val staticLayout =
                StaticLayout.Builder
                    .obtain(
                        text,
                        0,
                        text.length,
                        textPaint,
                        availableWidth
                    )
                    .setAlignment(
                        Layout.Alignment.ALIGN_NORMAL
                    )
                    .setIncludePad(
                        binding.tvSynopsis.includeFontPadding
                    )
                    .setLineSpacing(
                        binding.tvSynopsis.lineSpacingExtra,
                        binding.tvSynopsis.lineSpacingMultiplier
                    )
                    .build()

            val actualLineCount =
                staticLayout.lineCount

            val hasMoreThanThreeLines =
                actualLineCount > 3

            /*
             * Tombol hanya muncul jika teks benar-benar
             * menghasilkan lebih dari 3 baris.
             */
            binding.btnToggleSynopsis.isVisible =
                hasMoreThanThreeLines

            Log.d(
                SYNOPSIS_DEBUG_TAG,
                "Synopsis lines=$actualLineCount, " +
                        "width=$availableWidth, " +
                        "showToggle=$hasMoreThanThreeLines"
            )

            if (!hasMoreThanThreeLines) {

                /*
                 * Tidak perlu tombol.
                 */
                isSynopsisExpanded = false

                binding.tvSynopsis.maxLines =
                    Int.MAX_VALUE

                binding.tvSynopsis.ellipsize =
                    null

                binding.btnToggleSynopsis.text =
                    "Lihat Selengkapnya ▸"

            } else {

                /*
                 * Jika synopsis panjang dan belum expanded,
                 * tetap dalam mode 3 baris.
                 */
                if (!isSynopsisExpanded) {

                    binding.tvSynopsis.maxLines =
                        3

                    binding.tvSynopsis.ellipsize =
                        TextUtils.TruncateAt.END

                    binding.btnToggleSynopsis.text =
                        "Lihat Selengkapnya ▸"
                }
            }
        }
    }

    // ============================================================
    // Tabs
    // ============================================================

    private fun setupTabs() {

        binding.tabDetail.addOnTabSelectedListener(
            object :
                TabLayout.OnTabSelectedListener {

                override fun onTabSelected(
                    tab: TabLayout.Tab?
                ) {

                    when (tab?.position) {

                        0 -> {

                            binding.layoutEpisodeSection
                                .isVisible = true

                            binding.layoutServerSection
                                .isVisible = false
                        }

                        1 -> {

                            binding.layoutEpisodeSection
                                .isVisible = false

                            binding.layoutServerSection
                                .isVisible = true
                        }
                    }
                }

                override fun onTabUnselected(
                    tab: TabLayout.Tab?
                ) = Unit

                override fun onTabReselected(
                    tab: TabLayout.Tab?
                ) = Unit
            }
        )
    }

    // ============================================================
    // RecyclerView / Adapter
    // ============================================================

    private fun setupAdapters() {

        serverAdapter =
            ServerAdapter { selectedIdx ->

                selectedServerIdx =
                    selectedIdx

                val server =
                    currentDetail
                        ?.servers
                        ?.getOrNull(
                            selectedIdx
                        )
                        ?: return@ServerAdapter

                val watchedSet =
                    storage.getWatchedEpisodes(
                        movieId
                    )

                val lastWatched =
                    storage.getLastWatched(
                        movieId
                    )

                episodeAdapter.submitList(
                    server.episodes,
                    watchedSet,
                    lastWatched?.nid ?: -1
                )

                binding.tvActionQuality.text =
                    server.serverName
                        .take(8)
            }

        binding.rvServers.adapter =
            serverAdapter

        episodeAdapter =
            EpisodeAdapter { episode ->

                playEpisode(
                    episode
                )
            }

        gridLayoutManager =
            GridLayoutManager(
                this,
                4
            )

        binding.rvEpisodes.layoutManager =
            gridLayoutManager

        binding.rvEpisodes.adapter =
            episodeAdapter
    }

    // ============================================================
    // Player
    // ============================================================

    private fun playEpisode(
        episode: EpisodeItem
    ) {

        if (episode.playUrl.isBlank()) {

            Toast.makeText(
                this,
                "URL episode tidak tersedia",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val lastWatched =
            storage.getLastWatched(
                movieId
            )

        /*
         * Prioritas posisi:
         *
         * 1. History SID + NID.
         * 2. Posisi berdasarkan NID.
         * 3. 0.
         */
        val startPosition =
            if (
                lastWatched != null &&
                lastWatched.sid ==
                episode.sid &&
                lastWatched.nid ==
                episode.nid &&
                lastWatched.positionMs >
                1000L
            ) {

                lastWatched.positionMs

            } else {

                storage.getEpisodePosition(
                    movieId,
                    episode.nid
                )
            }

        val title =
            currentDetail
                ?.title
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: intent.getStringExtra(
                    EXTRA_MEDIA_TITLE
                )
                    .orEmpty()

        val poster =
            currentDetail
                ?.poster
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: initialPoster

        /*
         * Ini tetap URL halaman detail film/series.
         */
        val moviePageUrl =
            mediaPageUrl

        val playerIntent =
            Intent(
                this,
                PlayerActivity::class.java
            ).apply {

                putExtra(
                    EXTRA_MEDIA_ID,
                    movieId
                )

                putExtra(
                    EXTRA_EPISODE_ID,
                    episode.id
                )

                putExtra(
                    EXTRA_MEDIA_TITLE,
                    title
                )

                putExtra(
                    EXTRA_MEDIA_POSTER,
                    poster
                )

                putExtra(
                    EXTRA_EPISODE_NAME,
                    episode.episode
                )

                putExtra(
                    EXTRA_SID,
                    episode.sid
                )

                putExtra(
                    EXTRA_NID,
                    episode.nid
                )

                putExtra(
                    EXTRA_PLAY_URL,
                    episode.playUrl
                )

                /*
                 * Dipertahankan untuk kompatibilitas dengan
                 * PlayerActivity lama.
                 */
                putExtra(
                    EXTRA_MEDIA_URL,
                    episode.playUrl
                )

                /*
                 * URL halaman detail.
                 */
                putExtra(
                    EXTRA_MOVIE_URL,
                    moviePageUrl
                )

                if (startPosition > 1000L) {

                    putExtra(
                        EXTRA_START_POSITION,
                        startPosition
                    )
                }
            }

        Log.d(
            TAG,
            "Opening player: " +
                    "episode=${episode.episode}, " +
                    "id=${episode.id}, " +
                    "sid=${episode.sid}, " +
                    "nid=${episode.nid}, " +
                    "startPosition=$startPosition"
        )

        startActivity(
            playerIntent
        )
    }

    // ============================================================
    // Watched State
    // ============================================================

    private fun refreshWatchedState() {

        if (!::episodeAdapter.isInitialized) {
            return
        }

        val server =
            currentDetail
                ?.servers
                ?.getOrNull(
                    selectedServerIdx
                )
                ?: return

        val watchedSet =
            storage.getWatchedEpisodes(
                movieId
            )

        val lastWatched =
            storage.getLastWatched(
                movieId
            )

        episodeAdapter.submitList(
            server.episodes,
            watchedSet,
            lastWatched?.nid ?: -1
        )
    }

    // ============================================================
    // Observers
    // ============================================================

    @SuppressLint("DefaultLocale")
    private fun setupObservers() {

        // ========================================================
        // DETAIL
        // ========================================================

        viewModel.detail.observe(
            this
        ) { detail ->

            if (detail == null) {
                return@observe
            }

            currentDetail =
                detail

            /*
             * Pastikan selected server masih valid.
             */
            selectedServerIdx = if (detail.servers.isNotEmpty()) {

                selectedServerIdx.coerceIn(
                    0,
                    detail.servers.lastIndex
                )

            } else {

                0
            }

            bindDetail(
                detail
            )

            /*
             * TMDb hanya metadata tambahan/fallback.
             */
            val title =
                detail.title.trim()

            if (title.isNotBlank()) {

                viewModel.fetchExternalMetadata(
                    title
                )
            }
        }

        // ========================================================
        // TMDb
        // ========================================================

        viewModel.tmdbMetadata.observe(
            this
        ) { tmdb ->

            if (tmdb == null) {
                return@observe
            }

            Log.d(
                TMDB_DEBUG_TAG,
                "title=${tmdb.title ?: tmdb.name}, " +
                        "overviewLen=${tmdb.overview.length}, " +
                        "voteAverage=${tmdb.voteAverage}"
            )

            // ----------------------------------------------------
            // Rating
            // ----------------------------------------------------

            val localRating =
                getLocalRating()

            if (
                localRating.isBlank() &&
                tmdb.voteAverage > 0
            ) {

                binding.tvRatingDetail.text =
                    String.format(
                        "%.1f",
                        tmdb.voteAverage
                    )

                Log.d(
                    TMDB_DEBUG_TAG,
                    "Rating: menggunakan TMDb"
                )
            }

            // ----------------------------------------------------
            // Synopsis
            // ----------------------------------------------------

            val localSynopsis =
                currentDetail
                    ?.synopsis
                    ?.trim()
                    .orEmpty()

            if (
                localSynopsis.isBlank() &&
                tmdb.overview.isNotBlank()
            ) {

                setSynopsis(
                    tmdb.overview.trim()
                )

                Log.d(
                    SYNOPSIS_DEBUG_TAG,
                    "Synopsis: menggunakan TMDb"
                )
            }

            // ----------------------------------------------------
            // Backdrop
            // ----------------------------------------------------

            if (
                !tmdb.backdropPath
                    .isNullOrBlank()
            ) {

                val backdropUrl =
                    "https://image.tmdb.org/t/p/w780" +
                            tmdb.backdropPath

                binding.ivBackdrop.loadPoster(
                    backdropUrl
                )

                Log.d(
                    TMDB_DEBUG_TAG,
                    "Hero background: menggunakan TMDb backdrop"
                )
            }
        }

        // ========================================================
        // Loading
        // ========================================================

        viewModel.isLoading.observe(
            this
        ) { loading ->

            binding.detailProgressBar.isVisible =
                loading
        }

        // ========================================================
        // Error
        // ========================================================

        viewModel.error.observe(
            this
        ) { error ->

            if (!error.isNullOrBlank()) {

                Toast.makeText(
                    this,
                    error,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // Local Rating
    // ============================================================

    private fun getLocalRating(): String {

        return currentDetail
            ?.meta
            ?.let { meta ->

                (
                        meta["score"]
                            ?: meta["rating"]
                            ?: ""
                        )
                    .replace(
                        "★",
                        ""
                    )
                    .trim()
            }
            .orEmpty()
    }

    // ============================================================
    // Bind Detail
    // ============================================================

    private fun bindDetail(
        detail: MovieDetail
    ) {

        /*
         * Tentukan Movie / Series.
         */
        val isMovie =
            detail.status.equals(
                "Movie",
                ignoreCase = true
            ) ||
                    (
                            detail.servers.size <= 1 &&
                                    (
                                            detail.servers
                                                .firstOrNull()
                                                ?.count
                                                ?: 0
                                            ) <= 1
                            )

        // ========================================================
        // Title
        // ========================================================

        binding.tvDetailTitle.text =
            detail.title

        // ========================================================
        // Status
        // ========================================================

        binding.tvDetailStatus.text =
            if (isMovie) {

                "Movie HD"

            } else {

                detail.status
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "Series"
            }

        // ========================================================
        // Synopsis
        // ========================================================

        val localSynopsis =
            detail.synopsis
                .trim()

        if (localSynopsis.isNotBlank()) {

            setSynopsis(
                localSynopsis
            )

        } else {

            /*
             * Placeholder hanya visual.
             *
             * currentDetail tetap menyimpan synopsis kosong,
             * sehingga TMDb masih bisa digunakan sebagai fallback.
             */
            setSynopsis(
                "Belum ada deskripsi tersedia."
            )
        }

        Log.d(
            SYNOPSIS_DEBUG_TAG,
            "Local synopsis length=${localSynopsis.length}"
        )

        // ========================================================
        // Tabs
        // ========================================================

        binding.tabDetail
            .getTabAt(0)
            ?.text =
            if (isMovie) {
                "Putar Film"
            } else {
                "Episode"
            }

        binding.tabDetail
            .getTabAt(1)
            ?.text =
            if (isMovie) {
                "Server Player"
            } else {
                "Season"
            }

        // ========================================================
        // Rating
        // ========================================================

        val localRating =
            getLocalRating()

        binding.tvRatingDetail.text =
            localRating.ifBlank {
                "-"
            }

        // ========================================================
        // Country
        // ========================================================

        val country =
            detail.meta["country"]
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: detail.meta["negara"]
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: ""

        val countryBadge =
            if (country.isNotBlank()) {

                CountryUtils.formatCountry(
                    country
                )

            } else {

                CountryUtils.getCountryBadge(
                    MovieItem(
                        detail.id,
                        detail.title,
                        "",
                        "",
                        "",
                        ""
                    )
                )
            }

        binding.tvDetailCountry.isVisible =
            countryBadge.isNotBlank()

        binding.tvDetailCountry.text =
            countryBadge

        // ========================================================
        // Metadata
        // ========================================================

        val metaParts =
            mutableListOf<String>()

        detail.meta["year"]
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                metaParts.add(it)
            }

        if (country.isNotBlank()) {
            metaParts.add(country)
        }

        detail.meta["genre"]
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                metaParts.add(it)
            }

        detail.meta["duration"]
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                metaParts.add(it)
            }

        binding.tvDetailMeta.text =
            if (metaParts.isNotEmpty()) {

                metaParts.joinToString(
                    " • "
                )

            } else {

                if (isMovie) {
                    "Movie • HD Sub Indo"
                } else {
                    "Series • Sub Indo"
                }
            }

        // ========================================================
        // Poster
        // ========================================================

        val localPoster =
            detail.poster
                .trim()

        if (localPoster.isNotBlank()) {

            binding.ivBackdrop.loadPoster(
                localPoster
            )
        }

        // ========================================================
        // Episode Grid
        // ========================================================

        gridLayoutManager?.spanCount =
            if (isMovie) {
                1
            } else {
                4
            }

        // ========================================================
        // Servers
        // ========================================================

        if (detail.servers.isNotEmpty()) {

            selectedServerIdx =
                selectedServerIdx.coerceIn(
                    0,
                    detail.servers.lastIndex
                )

            val selectedServer =
                detail.servers[
                    selectedServerIdx
                ]

            val watchedSet =
                storage.getWatchedEpisodes(
                    movieId
                )

            val lastWatched =
                storage.getLastWatched(
                    movieId
                )

            serverAdapter.submitList(
                detail.servers,
                selectedServerIdx
            )

            episodeAdapter.submitList(
                selectedServer.episodes,
                watchedSet,
                lastWatched?.nid ?: -1
            )

            binding.tvActionQuality.text =
                selectedServer.serverName
                    .take(8)

        } else {

            serverAdapter.submitList(
                emptyList(),
                0
            )

            episodeAdapter.submitList(
                emptyList(),
                emptySet(),
                -1
            )

            binding.tvActionQuality.text =
                "N/A"
        }
    }
}