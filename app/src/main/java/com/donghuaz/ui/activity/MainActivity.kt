package com.donghuaz.ui.activity

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.donghuaz.R
import com.donghuaz.databinding.ActivityMainBinding
import com.donghuaz.ui.fragment.HomeFragment
import com.donghuaz.ui.fragment.LibraryFragment
import com.donghuaz.ui.fragment.RankingFragment
import com.donghuaz.ui.fragment.ScheduleFragment
import com.donghuaz.util.GradientBackground
import com.donghuaz.util.loadPoster

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment by lazy { HomeFragment() }
    private val scheduleFragment by lazy { ScheduleFragment() }
    private val rankingFragment by lazy { RankingFragment() }
    private val libraryFragment by lazy { LibraryFragment() }

    private var activeFragment: Fragment = homeFragment

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
                            0xFF0055FF.toInt(), // Electric Blue (Icon Dominant)
                            0xFFFC6F01.toInt()  // Flame Orange (Icon Accent)
                        ),
                        floatArrayOf(0f, 1f),
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
            .add(R.id.fragmentContainer, scheduleFragment, "schedule").hide(scheduleFragment)
            .add(R.id.fragmentContainer, homeFragment, "home")
            .commit()

        activeFragment = homeFragment
    }

    private var currentNavIndex = 0

    private fun setupBottomNav() {
        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            moveSlidingIndicator(currentNavIndex, animate = false)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetIndex = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_schedule -> 1
                R.id.nav_ranking -> 2
                R.id.nav_library -> 3
                else -> -1
            }

            if (targetIndex >= 0 && targetIndex != currentNavIndex) {
                moveSlidingIndicator(targetIndex, animate = true)
                currentNavIndex = targetIndex
            }

            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    true
                }
                R.id.nav_schedule -> {
                    switchFragment(scheduleFragment)
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

    private fun moveSlidingIndicator(index: Int, animate: Boolean) {
        val navWidth = binding.bottomNav.width.toFloat()
        if (navWidth <= 0f) return

        val itemCount = 4
        val tabWidth = navWidth / itemCount
        val indicatorWidth = binding.vNavSlidingIndicator.width.toFloat().coerceAtLeast(1f)
        val targetX = (tabWidth * index) + (tabWidth - indicatorWidth) / 2f

        if (animate) {
            binding.vNavSlidingIndicator.animate()
                .translationX(targetX)
                .setDuration(350)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.15f))
                .start()

            // Subtle bounce pop on active tab icon
            val menuView = binding.bottomNav.getChildAt(0) as? android.view.ViewGroup
            val itemView = menuView?.getChildAt(index)
            itemView?.let { view ->
                view.animate()
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .setDuration(150)
                    .withEndAction {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        } else {
            binding.vNavSlidingIndicator.translationX = targetX
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
