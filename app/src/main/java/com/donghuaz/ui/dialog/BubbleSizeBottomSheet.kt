package com.donghuaz.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.donghuaz.data.local.StorageManager
import com.donghuaz.databinding.LayoutBubbleSizeDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BubbleSizeBottomSheet(
    private val onSizeChanged: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutBubbleSizeDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutBubbleSizeDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val storage = StorageManager.getInstance(requireContext())
        val currentSize = storage.getBubbleSize()

        binding.sliderBubbleSize.value = currentSize.toFloat().coerceIn(40f, 90f)
        binding.tvSizeValue.text = "$currentSize dp"

        binding.sliderBubbleSize.addOnChangeListener { _, value, _ ->
            val size = value.toInt()
            binding.tvSizeValue.text = "$size dp"
            storage.setBubbleSize(size)
            onSizeChanged(size)
        }

        binding.btnPresetSmall.setOnClickListener {
            applyPreset(50)
        }

        binding.btnPresetDefault.setOnClickListener {
            applyPreset(60)
        }

        binding.btnPresetLarge.setOnClickListener {
            applyPreset(75)
        }
    }

    private fun applyPreset(size: Int) {
        binding.sliderBubbleSize.value = size.toFloat()
        binding.tvSizeValue.text = "$size dp"
        StorageManager.getInstance(requireContext()).setBubbleSize(size)
        onSizeChanged(size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BubbleSizeBottomSheet"
    }
}
