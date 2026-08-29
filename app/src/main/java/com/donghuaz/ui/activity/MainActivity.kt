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
        setupFragments()
        setupBottomNav()
        setupSearch()
        setupLogoStyle()
    }

    private fun setupLogoStyle() {
        binding.tvLogo.post {
            val text = binding.tvLogo.text.toString()
            val textWidth = binding.tvLogo.paint.measureText(text)
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
                binding.tvLogo.paint.shader = shader
                binding.tvLogo.invalidate()
            }
        }
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

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    binding.searchContainer.visibility = View.VISIBLE
                    true
                }
                R.id.nav_schedule -> {
                    switchFragment(scheduleFragment)
                    binding.searchContainer.visibility = View.GONE
                    true
                }
                R.id.nav_ranking -> {
                    switchFragment(rankingFragment)
                    binding.searchContainer.visibility = View.GONE
                    true
                }
                R.id.nav_library -> {
                    switchFragment(libraryFragment)
                    binding.searchContainer.visibility = View.GONE
                    true
                }
                else -> false
            }
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

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    if (activeFragment != homeFragment) {
                        binding.bottomNav.selectedItemId = R.id.nav_home
                    }
                    homeFragment.search(query)
                }
                true
            } else {
                false
            }
        }

        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            if (query.isEmpty()) {
                homeFragment.clearSearch()
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            homeFragment.clearSearch()
        }
    }
}
