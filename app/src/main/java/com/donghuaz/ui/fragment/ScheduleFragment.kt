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
import com.donghuaz.ui.adapter.AnimeAdapter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private lateinit var animeAdapter: AnimeAdapter
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

        animeAdapter = AnimeAdapter { anime ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("ANIME_ID", anime.id)
                putExtra("ANIME_TITLE", anime.title)
                putExtra("ANIME_POSTER", anime.poster)
            }
            startActivity(intent)
        }

        binding.rvSchedule.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = animeAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(25)
        }

        setupDayTabs()
        loadSchedule()
    }

    private fun setupDayTabs() {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        for (day in days) {
            binding.tabDays.addTab(binding.tabDays.newTab().setText(day))
        }

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

                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val currentDayIdx = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2

                binding.tabDays.getTabAt(currentDayIdx.coerceIn(0, 6))?.select()
                displayScheduleForDay(currentDayIdx.coerceIn(0, 6))
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
        animeAdapter.submitList(items)

        binding.tvEmptySchedule.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSchedule.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
