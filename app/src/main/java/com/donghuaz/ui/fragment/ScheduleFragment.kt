package com.donghuaz.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.data.model.WeekdaySchedule
import com.donghuaz.data.parser.DonghuaParser
import com.donghuaz.databinding.FragmentScheduleBinding
import com.donghuaz.ui.activity.DetailActivity
import com.donghuaz.ui.adapter.ScheduleAdapter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter
    private var schedules = listOf<WeekdaySchedule>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleAdapter = ScheduleAdapter { anime ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("ANIME_ID", anime.id)
                putExtra("ANIME_TITLE", anime.title)
                putExtra("ANIME_POSTER", anime.poster)
            }
            startActivity(intent)
        }

        binding.rvSchedule.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = scheduleAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }

        val defaultCategories = listOf("Terbaru", "Unggulan", "Ongoing", "Complete", "Asian Series", "West Series", "Drakor")
        setupDayTabs(defaultCategories)
        loadSchedule()
    }

    private fun setupDayTabs(categoryNames: List<String>) {
        binding.tabDays.removeAllTabs()
        for (name in categoryNames) {
            binding.tabDays.addTab(binding.tabDays.newTab().setText(name))
        }

        binding.tabDays.clearOnTabSelectedListeners()
        binding.tabDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val idx = tab?.position ?: 0
                displayScheduleForDay(idx)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadSchedule() {
        if (schedules.isEmpty()) {
            binding.shimmerSchedule.startShimmer()
            binding.shimmerSchedule.visibility = View.VISIBLE
            binding.rvSchedule.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                schedules = DonghuaParser.getWeeklySchedule()
                val categoryNames = schedules.map { it.dayName }
                if (categoryNames.isNotEmpty()) {
                    setupDayTabs(categoryNames)
                    binding.tabDays.getTabAt(0)?.select()
                    displayScheduleForDay(0)
                }
            } catch (_: Exception) {
            } finally {
                binding.shimmerSchedule.stopShimmer()
                binding.shimmerSchedule.visibility = View.GONE
                binding.rvSchedule.visibility = View.VISIBLE
            }
        }
    }

    private fun displayScheduleForDay(dayIndex: Int) {
        val schedule = schedules.getOrNull(dayIndex)
        val items = schedule?.animeList ?: emptyList()
        scheduleAdapter.submitList(items)

        binding.tvEmptySchedule.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSchedule.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
