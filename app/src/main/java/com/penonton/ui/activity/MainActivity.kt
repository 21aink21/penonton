package com.penonton.ui.activity

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.penonton.R
import com.penonton.data.local.StorageManager
import com.penonton.databinding.ActivityMainBinding
import com.penonton.ui.dialog.BubbleSizeBottomSheet
import com.penonton.ui.fragment.HomeFragment
import com.penonton.ui.fragment.LibraryFragment
import com.penonton.ui.fragment.RankingFragment
import com.penonton.ui.fragment.ScheduleFragment
import com.penonton.util.GradientBackground
import com.penonton.util.loadPoster

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val seriesFragment by lazy { ScheduleFragment() }
    private val moviesFragment by lazy { HomeFragment() }
    private val rankingFragment by lazy { RankingFragment() }
    private val libraryFragment by lazy { LibraryFragment() }

    private var activeFragment: Fragment = seriesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        GradientBackground.apply(this)
        setupAmbientBackdrop()
        setupFragments()
        setupBottomNav()
        setupLogoStyle()
    }

    private fun setupAmbientBackdrop() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            binding.ivDynamicHomeBackdrop.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    30f, 30f, android.graphics.Shader.TileMode.CLAMP
                )
            )
            binding.vBottomNavGlassBlur.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    15f, 15f, android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
    }

    fun updateAmbientBackdrop(posterUrl: String) {
        if (posterUrl.isNotEmpty()) {
            binding.ivDynamicHomeBackdrop.loadPoster(posterUrl)
        }
    }

    private fun setupLogoStyle() {
        val applyGradient = { textView: android.widget.TextView ->
            textView.post {
                val text = textView.text.toString()
                val textWidth = textView.paint.measureText(text)
                if (textWidth > 0) {
                    val shader = android.graphics.LinearGradient(
                        0f, 0f, textWidth, 0f,
                        intArrayOf(
                            0xFFFB9E0C.toInt(), // Amber Gold / Orange (Icon Left)
                            0xFFF34390.toInt(), // Vibrant Rose Pink (Icon Center)
                            0xFF9425EE.toInt(), // Royal Violet / Purple (Icon Right)
                            0xFF049CFC.toInt()  // Electric Cyan Blue (Icon Accent)
                        ),
                        floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    textView.paint.shader = shader
                    textView.invalidate()
                }
            }
        }
        applyGradient(binding.tvLogo)
        applyGradient(binding.tvWatermarkText)
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, libraryFragment, "library").hide(libraryFragment)
            .add(R.id.fragmentContainer, rankingFragment, "ranking").hide(rankingFragment)
            .add(R.id.fragmentContainer, moviesFragment, "movies").hide(moviesFragment)
            .add(R.id.fragmentContainer, seriesFragment, "series")
            .commit()

        activeFragment = seriesFragment
    }

    private var currentNavIndex = 0

    private fun setupBottomNav() {
        binding.bottomNav.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED

        val storage = StorageManager.getInstance(this)
        applyBubbleSize(storage.getBubbleSize())

        binding.btnBubbleSize.setOnClickListener {
            showBubbleSizeDialog()
        }

        binding.bottomNavContainer.setOnLongClickListener {
            showBubbleSizeDialog()
            true
        }

        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            moveBubbleIndicator(currentNavIndex, animate = false)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetIndex = when (item.itemId) {
                R.id.nav_series -> 0
                R.id.nav_movies -> 1
                R.id.nav_ranking -> 2
                R.id.nav_library -> 3
                else -> -1
            }

            if (targetIndex >= 0 && targetIndex != currentNavIndex) {
                moveBubbleIndicator(targetIndex, animate = true)
                currentNavIndex = targetIndex
            }

            when (item.itemId) {
                R.id.nav_series -> {
                    switchFragment(seriesFragment)
                    true
                }
                R.id.nav_movies -> {
                    switchFragment(moviesFragment)
                    true
                }
                R.id.nav_ranking -> {
                    switchFragment(rankingFragment)
                    true
                }
                R.id.nav_library -> {
                    switchFragment(libraryFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun applyBubbleSize(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val widthPx = (sizeDp * density).toInt()
        val heightPx = ((32 + (sizeDp - 40) * 0.35f) * density).toInt().coerceIn((36 * density).toInt(), (50 * density).toInt())
        val cornerRadius = heightPx / 2f

        val params = binding.vNavBubbleIndicator.layoutParams as? android.widget.FrameLayout.LayoutParams
            ?: android.widget.FrameLayout.LayoutParams(widthPx, heightPx)
        params.width = widthPx
        params.height = heightPx
        params.gravity = android.view.Gravity.CENTER_VERTICAL
        binding.vNavBubbleIndicator.layoutParams = params

        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(android.graphics.Color.parseColor("#38FC6F01"))
            setStroke((1.5f * density).toInt(), android.graphics.Color.parseColor("#E6FC6F01"))
        }
        binding.vNavBubbleIndicator.background = bgDrawable

        val navWidth = binding.bottomNav.width.toFloat()
        if (navWidth > 0f) {
            val tabWidth = navWidth / 4f
            val targetX = (tabWidth * currentNavIndex) + (tabWidth - widthPx.toFloat()) / 2f
            binding.vNavBubbleIndicator.translationX = targetX
        }
    }

    private fun showBubbleSizeDialog() {
        BubbleSizeBottomSheet { newSize ->
            applyBubbleSize(newSize)
        }.show(supportFragmentManager, BubbleSizeBottomSheet.TAG)
    }

    private fun moveBubbleIndicator(index: Int, animate: Boolean) {
        val navWidth = binding.bottomNav.width.toFloat()
        if (navWidth <= 0f) return

        val itemCount = 4
        val tabWidth = navWidth / itemCount
        val density = resources.displayMetrics.density
        val indicatorWidth = binding.vNavBubbleIndicator.layoutParams?.width?.toFloat()
            ?.takeIf { it > 0 } ?: (StorageManager.getInstance(this).getBubbleSize() * density)
        val targetX = (tabWidth * index) + (tabWidth - indicatorWidth) / 2f

        if (animate) {
            binding.vNavBubbleIndicator.animate()
                .translationX(targetX)
                .setDuration(350)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()

            // Dynamic bouncy tactile pop on active bubble tab icon
            val menuView = binding.bottomNav.getChildAt(0) as? android.view.ViewGroup
            val itemView = menuView?.getChildAt(index)
            itemView?.let { view ->
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(160)
                    .withEndAction {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(160)
                            .start()
                    }
                    .start()
            }
        } else {
            binding.vNavBubbleIndicator.translationX = targetX
        }
    }

    private fun switchFragment(target: Fragment) {
        if (activeFragment == target) return

        val tx = supportFragmentManager.beginTransaction()
        tx.hide(activeFragment)
        if (!target.isAdded) {
            tx.add(R.id.fragmentContainer, target)
        } else {
            tx.show(target)
        }
        tx.commit()
        activeFragment = target
    }
}
