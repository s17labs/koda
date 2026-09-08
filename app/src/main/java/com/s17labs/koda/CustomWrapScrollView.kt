package com.s17labs.koda

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView

/**
 * HorizontalScrollView with a switchable width constraint for its child.
 *
 * A stock HorizontalScrollView always measures its child with an UNSPECIFIED
 * width, so an EditText inside it grows to its longest line and never wraps.
 * When [constrainToViewport] is true the child is instead measured against
 * the viewport width (FrameLayout-style), so long lines wrap; when false the
 * child can grow beyond the screen and this view pans it with fling momentum.
 */
class CustomWrapScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var constrainToViewport: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
        if (!constrainToViewport || lp == null) {
            super.measureChildWithMargins(
                child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed
            )
            return
        }
        // Bound the child by the viewport width so TextView wraps its lines
        // instead of growing past the screen edge.
        val childWidthMeasureSpec = getChildMeasureSpec(
            parentWidthMeasureSpec,
            paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + widthUsed,
            lp.width
        )
        val childHeightMeasureSpec = getChildMeasureSpec(
            parentHeightMeasureSpec,
            paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed,
            lp.height
        )
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }
}
