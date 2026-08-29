package com.donghuaz.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.donghuaz.data.local.StorageManager
import com.donghuaz.databinding.LayoutBottomNavSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomNavSettingsBottomSheet(
    private val onStyleChanged: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutBottomNavSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutBottomNavSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val storage = StorageManager.getInstance(requireContext())
        val currentStyle = storage.getBottomNavStyle()

        updateSelection(currentStyle)

        binding.cardStyleTraditional.setOnClickListener {
            selectStyle(StorageManager.NAV_STYLE_TRADITIONAL)
        }
        binding.cardStyleSliding.setOnClickListener {
            selectStyle(StorageManager.NAV_STYLE_SLIDING)
        }
        binding.cardStyleCurved.setOnClickListener {
            selectStyle(StorageManager.NAV_STYLE_CURVED)
        }
        binding.cardStyleBubble.setOnClickListener {
            selectStyle(StorageManager.NAV_STYLE_BUBBLE)
        }
    }

    private fun selectStyle(style: String) {
        val storage = StorageManager.getInstance(requireContext())
        storage.setBottomNavStyle(style)
        updateSelection(style)
        onStyleChanged(style)
        dismiss()
    }

    private fun updateSelection(style: String) {
        binding.rbTraditional.isChecked = style == StorageManager.NAV_STYLE_TRADITIONAL
        binding.rbSliding.isChecked = style == StorageManager.NAV_STYLE_SLIDING
        binding.rbCurved.isChecked = style == StorageManager.NAV_STYLE_CURVED
        binding.rbBubble.isChecked = style == StorageManager.NAV_STYLE_BUBBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BottomNavSettingsBottomSheet"
    }
}
