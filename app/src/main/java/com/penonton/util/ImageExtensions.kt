package com.penonton.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.penonton.R

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val defaultPosterOptions = RequestOptions()
    .diskCacheStrategy(DiskCacheStrategy.ALL)
    .format(DecodeFormat.PREFER_RGB_565)
    .placeholder(R.drawable.bg_shimmer_card)
    .error(R.drawable.bg_card)
    .centerCrop()

fun ImageView.loadPoster(url: String?) {
    if (url.isNullOrEmpty()) {
        setImageResource(R.drawable.bg_card)
        return
    }

    val referer = when {
        url.contains("doubanio.com") -> "https://movie.douban.com/"
        url.contains("donghuafun.com") -> "https://donghuafun.com/"
        else -> "https://donghuafun.com/"
    }

    val glideUrl = GlideUrl(
        url,
        LazyHeaders.Builder()
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", referer)
            .build()
    )

    Glide.with(context)
        .load(glideUrl)
        .apply(defaultPosterOptions)
        .transition(DrawableTransitionOptions.withCrossFade(150))
        .into(this)
}
