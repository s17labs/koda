package com.s17labs.koda

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** One viewport owns both scroll axes; the editor grows to fit the document. */
class EditorScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val panScroller = OverScroller(context)
    private val configuration = ViewConfiguration.get(context)
    private var velocityTracker: VelocityTracker? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var restoredHorizontalOffset: Int? = null

    internal var selectionDragging = false

    internal fun resetFling() { panScroller.abortAnimation() }

    var wrapLines = true
        set(value) {
            field = value
            (getChildAt(0) as? TextView)?.setHorizontallyScrolling(!value)
            panScroller.abortAnimation()
            if (value) scrollTo(0, scrollY)
            requestLayout()
        }

    init {
        // Stock fillViewport remeasures a short, wide document at viewport width.
        // Minimum dimensions below fill short files without losing their width.
        isFillViewport = false
        isSmoothScrollingEnabled = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        getChildAt(0)?.let { child ->
            val lp = child.layoutParams as ViewGroup.MarginLayoutParams
            val minimumWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
                lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
            val minimumHeight = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom -
                lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
            if (child is TextView) {
                if (child.minWidth != minimumWidth) child.minWidth = minimumWidth
                if (child.minHeight != minimumHeight) child.minHeight = minimumHeight
            } else {
                if (child.minimumWidth != minimumWidth) child.minimumWidth = minimumWidth
                if (child.minimumHeight != minimumHeight) child.minimumHeight = minimumHeight
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        val lp = child.layoutParams as ViewGroup.MarginLayoutParams
        val width = (MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight -
            lp.leftMargin - lp.rightMargin - widthUsed).coerceAtLeast(0)
        child.measure(
            MeasureSpec.makeMeasureSpec(width, if (wrapLines) MeasureSpec.EXACTLY else MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
    }

    private val horizontalRange: Int
        get() = if (wrapLines) 0 else ((getChildAt(0)?.right ?: 0) + paddingRight - width).coerceAtLeast(0)
    private val verticalRange: Int
        get() = ((getChildAt(0)?.bottom ?: 0) + paddingBottom - height).coerceAtLeast(0)

    override fun computeHorizontalScrollRange(): Int = width + horizontalRange

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                pointerId = event.getPointerId(0)
                dragging = !panScroller.isFinished
                panScroller.abortAnimation()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) {
                    val dx = abs(event.getX(index) - lastX)
                    val dy = abs(event.getY(index) - lastY)
                    if ((horizontalRange > 0 && dx > configuration.scaledTouchSlop) ||
                        (verticalRange > 0 && dy > configuration.scaledTouchSlop)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> releasePointer(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        // Native text selection can still disallow interception after a long press.
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (childCount == 0) return false
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panScroller.abortAnimation()
                lastX = event.x
                lastY = event.y
                pointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                scrollTo(
                    if (wrapLines) 0 else (scrollX + lastX - x).roundToInt(),
                    (scrollY + lastY - y).roundToInt()
                )
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_POINTER_UP -> releasePointer(event)
            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
                val vx = -(velocityTracker?.getXVelocity(pointerId) ?: 0f).roundToInt()
                val vy = -(velocityTracker?.getYVelocity(pointerId) ?: 0f).roundToInt()
                if (dragging && (abs(vx) >= configuration.scaledMinimumFlingVelocity ||
                    abs(vy) >= configuration.scaledMinimumFlingVelocity)) {
                    panScroller.fling(scrollX, scrollY, if (wrapLines) 0 else vx, vy,
                        0, horizontalRange, 0, verticalRange)
                    postInvalidateOnAnimation()
                } else if (!dragging) {
                    performClick()
                }
                finishDrag()
            }
            MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    private fun releasePointer(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) == pointerId) {
            val index = if (event.actionIndex == 0) 1 else 0
            pointerId = event.getPointerId(index)
            lastX = event.getX(index)
            lastY = event.getY(index)
            velocityTracker?.clear()
        }
    }

    private fun finishDrag() {
        dragging = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishDrag()
        }
        return handled
    }

    override fun performClick(): Boolean = super.performClick()

    override fun fling(velocityY: Int) {
        panScroller.fling(scrollX, scrollY, 0, velocityY, 0, horizontalRange, 0, verticalRange)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (panScroller.computeScrollOffset()) {
            scrollTo(panScroller.currX, panScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return onGenericMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val dx = if ((event.metaState and KeyEvent.META_SHIFT_ON != 0)) -vertical else horizontal
            val dy = if ((event.metaState and KeyEvent.META_SHIFT_ON != 0)) 0f else -vertical
            val oldX = scrollX
            val oldY = scrollY
            panScroller.abortAnimation()
            scrollTo(if (wrapLines) 0 else scrollX + (dx * configuration.scaledHorizontalScrollFactor).roundToInt(),
                scrollY + (dy * configuration.scaledVerticalScrollFactor).roundToInt())
            return oldX != scrollX || oldY != scrollY
        }
        return super.onGenericMotionEvent(event)
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        // Keep the viewport at the dragged endpoint, including delayed toolbar refreshes.
        if (selectionDragging || (child is SelectionEditText && child.preservesSelectionViewport)) return false
        val rect = Rect(rectangle)
        rect.offset(child.left - child.scrollX, child.top - child.scrollY)
        val x = when {
            wrapLines -> 0
            rect.right > scrollX + width - paddingRight -> rect.right - width + paddingRight
            rect.left < scrollX + paddingLeft -> rect.left - paddingLeft
            else -> scrollX
        }.coerceIn(0, horizontalRange)
        val y = when {
            rect.bottom > scrollY + height - paddingBottom -> rect.bottom - height + paddingBottom
            rect.top < scrollY + paddingTop -> rect.top - paddingTop
            else -> scrollY
        }.coerceIn(0, verticalRange)
        if (x == scrollX && y == scrollY) return false
        panScroller.abortAnimation()
        if (immediate) {
            scrollTo(x, y)
        } else {
            panScroller.startScroll(scrollX, scrollY, x - scrollX, y - scrollY)
            postInvalidateOnAnimation()
        }
        return true
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.maxScrollX = horizontalRange
        event.maxScrollY = verticalRange
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = isEnabled && (horizontalRange > 0 || verticalRange > 0)
        if (!isEnabled) return
        if (scrollX > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
        if (scrollX < horizontalRange) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val pageWidth = width - paddingLeft - paddingRight
        val pageHeight = height - paddingTop - paddingBottom
        val (dx, dy) = when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id -> -pageWidth to 0
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id -> pageWidth to 0
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> 0 to -pageHeight
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> 0 to pageHeight
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (!isEnabled) return false
        val x = (scrollX + dx).coerceIn(0, horizontalRange)
        val y = (scrollY + dy).coerceIn(0, verticalRange)
        if (x == scrollX && y == scrollY) return false
        panScroller.abortAnimation()
        panScroller.startScroll(scrollX, scrollY, x - scrollX, y - scrollY)
        postInvalidateOnAnimation()
        return true
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState()).apply {
        horizontalOffset = scrollX
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        restoredHorizontalOffset = state.horizontalOffset
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        restoredHorizontalOffset?.let {
            scrollTo(if (wrapLines) 0 else it, scrollY)
            restoredHorizontalOffset = null
        }
    }

    class SavedState : BaseSavedState {
        var horizontalOffset = 0

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) { horizontalOffset = source.readInt() }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(horizontalOffset)
        }

        companion object {
            @JvmField val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        panScroller.abortAnimation()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    fun resetScroll() {
        panScroller.abortAnimation()
        scrollTo(0, 0)
    }

    override fun onDetachedFromWindow() {
        panScroller.abortAnimation()
        finishDrag()
        super.onDetachedFromWindow()
    }
}
