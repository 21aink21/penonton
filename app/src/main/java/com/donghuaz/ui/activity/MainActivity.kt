package com.donghuaz.ui.activity

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.donghuaz.R
import com.donghuaz.data.local.StorageManager
import com.donghuaz.databinding.ActivityMainBinding
import com.donghuaz.ui.dialog.BottomNavSettingsBottomSheet
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
        val storage = StorageManager.getInstance(this)
        applyNavStyle(storage.getBottomNavStyle())

        binding.btnNavSettings.setOnClickListener {
            BottomNavSettingsBottomSheet { newStyle ->
                applyNavStyle(newStyle)
            }.show(supportFragmentManager, BottomNavSettingsBottomSheet.TAG)
        }

        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val style = StorageManager.getInstance(this).getBottomNavStyle()
            if (style == StorageManager.NAV_STYLE_CURVED) {
                moveCurvedIndicator(currentNavIndex, animate = false)
            } else if (style == StorageManager.NAV_STYLE_SLIDING || style == StorageManager.NAV_STYLE_BUBBLE) {
                moveSlidingIndicator(currentNavIndex, animate = false)
            }
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
                val style = StorageManager.getInstance(this).getBottomNavStyle()
                when (style) {
                    StorageManager.NAV_STYLE_CURVED -> moveCurvedIndicator(targetIndex, animate = true)
                    StorageManager.NAV_STYLE_SLIDING, StorageManager.NAV_STYLE_BUBBLE -> moveSlidingIndicator(targetIndex, animate = true)
                    else -> bounceActiveIcon(targetIndex)
                }
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

    private fun applyNavStyle(style: String) {
        when (style) {
            StorageManager.NAV_STYLE_TRADITIONAL -> {
                binding.vNavSlidingIndicator.visibility = View.GONE
                binding.vNavCurved.visibility = View.GONE
                binding.vBottomNavGlassBlur.visibility = View.VISIBLE
                binding.bottomNav.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
            }
            StorageManager.NAV_STYLE_SLIDING -> {
                binding.vNavSlidingIndicator.visibility = View.VISIBLE
                binding.vNavSlidingIndicator.setBackgroundResource(R.drawable.bg_nav_sliding_pill)
                binding.vNavCurved.visibility = View.GONE
                binding.vBottomNavGlassBlur.visibility = View.VISIBLE
                binding.bottomNav.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
                moveSlidingIndicator(currentNavIndex, animate = false)
            }
            StorageManager.NAV_STYLE_CURVED -> {
                binding.vNavSlidingIndicator.visibility = View.GONE
                binding.vNavCurved.visibility = View.VISIBLE
                binding.vBottomNavGlassBlur.visibility = View.GONE
                binding.bottomNav.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
                moveCurvedIndicator(currentNavIndex, animate = false)
            }
            StorageManager.NAV_STYLE_BUBBLE -> {
                binding.vNavSlidingIndicator.visibility = View.VISIBLE
                binding.vNavSlidingIndicator.setBackgroundResource(R.drawable.bg_nav_bubble_pill)
                binding.vNavCurved.visibility = View.GONE
                binding.vBottomNavGlassBlur.visibility = View.VISIBLE
                binding.bottomNav.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED
                moveSlidingIndicator(currentNavIndex, animate = false)
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

            bounceActiveIcon(index)
        } else {
            binding.vNavSlidingIndicator.translationX = targetX
        }
    }

    private fun moveCurvedIndicator(index: Int, animate: Boolean) {
        val navWidth = binding.bottomNav.width.toFloat()
        if (navWidth <= 0f) return

        val itemCount = 4
        val tabWidth = navWidth / itemCount
        val targetCenterX = (tabWidth * index) + (tabWidth / 2f)

        if (animate) {
            binding.vNavCurved.animateTo(targetCenterX, 350)
            bounceActiveIcon(index)
        } else {
            binding.vNavCurved.activeCenterX = targetCenterX
        }
    }

    private fun bounceActiveIcon(index: Int) {
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
