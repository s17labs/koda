package com.s17labs.koda

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Keep short actions side by side, but give long or enlarged labels their own rows. */
class DialogActionLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val actions = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        val gap = (8 * resources.displayMetrics.density).toInt()
        var requiredWidth = gap * (actions.size - 1).coerceAtLeast(0)
        actions.forEach { action ->
            action.layoutParams.width = LayoutParams.WRAP_CONTENT
            measureChild(action, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
            requiredWidth += action.measuredWidth
        }
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val stacked = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            requiredWidth > availableWidth
        orientation = if (stacked) VERTICAL else HORIZONTAL
        actions.forEachIndexed { index, action ->
            (action.layoutParams as LayoutParams).apply {
                width = if (stacked) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
                marginEnd = if (!stacked && index < actions.lastIndex) gap else 0
                topMargin = if (stacked && index > 0) gap else 0
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
