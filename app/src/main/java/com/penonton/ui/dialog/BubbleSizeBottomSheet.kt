package com.penonton.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.penonton.data.local.StorageManager
import com.penonton.databinding.LayoutBubbleSizeDialogBinding
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

        binding.sliderBubbleSize.value = currentSize.toFloat().coerceIn(40f, 95f)
        binding.tvSizeValue.text = "$currentSize dp"
        updatePreview(currentSize)

        binding.sliderBubbleSize.addOnChangeListener { _, value, _ ->
            val size = value.toInt()
            binding.tvSizeValue.text = "$size dp"
            updatePreview(size)
            storage.setBubbleSize(size)
            onSizeChanged(size)
        }

        binding.btnPresetSmall.setOnClickListener {
            applyPreset(48)
        }

        binding.btnPresetDefault.setOnClickListener {
            applyPreset(64)
        }

        binding.btnPresetLarge.setOnClickListener {
            applyPreset(85)
        }
    }

    private fun updatePreview(sizeDp: Int) {
        val density = resources.displayMetrics.density
        val widthPx = (sizeDp * density).toInt()
        val heightPx = ((32 + (sizeDp - 40) * 0.35f) * density).toInt().coerceIn((36 * density).toInt(), (50 * density).toInt())
        val cornerRadius = heightPx / 2f

        val params = binding.vPreviewBubble.layoutParams
        params.width = widthPx
        params.height = heightPx
        binding.vPreviewBubble.layoutParams = params

        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setColor(android.graphics.Color.parseColor("#38FC6F01"))
            setStroke((1.5f * density).toInt(), android.graphics.Color.parseColor("#E6FC6F01"))
        }
        binding.vPreviewBubble.background = bgDrawable
    }

    private fun applyPreset(size: Int) {
        binding.sliderBubbleSize.value = size.toFloat()
        binding.tvSizeValue.text = "$size dp"
        updatePreview(size)
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
