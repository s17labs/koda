package com.s17labs.koda

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout

class CustomToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var thumb: View
    private lateinit var track: View
    private var _isChecked = false
    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    var isChecked: Boolean
        get() = _isChecked
        set(value) {
            if (_isChecked != value) {
                _isChecked = value
                updateTrackBackground()
                updateThumbPosition(true)
                onCheckedChangeListener?.invoke(value)
            }
        }

    init {
        isClickable = true
        isFocusable = true

        LayoutInflater.from(context).inflate(R.layout.view_custom_toggle, this, true)
        thumb = findViewById(R.id.toggleThumb)
        track = findViewById(R.id.toggleTrack)

        track.setOnClickListener { isChecked = !isChecked }
        thumb.setOnClickListener { isChecked = !isChecked }

        updateTrackBackground()
        updateThumbPosition(false)
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }

    private fun updateTrackBackground() {
        track.setBackgroundResource(
            if (_isChecked) R.drawable.toggle_on else R.drawable.toggle_off
        )
    }

    private fun updateThumbPosition(animate: Boolean) {
        post {
            val trackWidth = track.width
            val thumbWidth = thumb.width
            val startMargin = (thumb.layoutParams as? MarginLayoutParams)?.marginStart ?: 0
            val endMargin = trackWidth - thumbWidth - startMargin
            val maxTranslation = (endMargin - startMargin).toFloat()

            if (animate) {
                thumb.animate()
                    .translationX(if (_isChecked) maxTranslation else 0f)
                    .setDuration(200)
                    .start()
            } else {
                thumb.translationX = if (_isChecked) maxTranslation else 0f
            }
        }
    }
}
