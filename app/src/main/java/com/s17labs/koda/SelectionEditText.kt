package com.s17labs.koda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.text.Selection
import android.text.Spanned
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs
import kotlin.math.roundToInt

/** Selection handles scroll the visible viewport, rather than the full document view. */
class SelectionEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    private companion object {
        // Application above-sub-panel layer, also used by the platform floating toolbar.
        // Keep our touch targets above the transparent native selection-handle windows.
        const val HANDLE_WINDOW_TYPE = 1005
    }

    private var selectionMode = false
    private var selectionActionMode: ActionMode? = null
    private var startHandle: SelectionHandle? = null
    private var endHandle: SelectionHandle? = null
    private var activeHandle: SelectionHandle? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var lastFrame = 0L
    private var directSelectionGesture = false
    private var directAnchorStart = -1
    private var directAnchorEnd = -1
    private var fixedOffset = 0
    private var pendingSelectionChange = false
    private var selectionViewportOwned = false
    internal val preservesSelectionViewport get() = selectionViewportOwned && hasSelection()
    private var lastToolbarHide = 0L
    private val location = IntArray(2)
    private val visible = Rect()
    private val windowOnScreen = IntArray(2)
    private val windowInWindow = IntArray(2)
    private val viewport get() = parent as? EditorScrollView
    private val density get() = resources.displayMetrics.density
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (activeHandle == null) updateHandles()
        true
    }

    init {
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                selectionMode = true
                selectionActionMode = mode
                if (activeHandle != null) mode.hide(-1)
                post { updateHandles() }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) {
                selectionMode = false
                selectionActionMode = null
                // Android rebuilds action mode when its offscreen start handle is hidden.
                // Keep the pointer and edge scrolling alive throughout that rebuild.
                if (activeHandle == null) hideHandles()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        stopSelectionDrag()
        hideHandles()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            stopSelectionDrag()
            hideHandles()
        }
    }

    private fun hideHandles() {
        startHandle?.popup?.dismiss()
        endHandle?.popup?.dismiss()
    }

    private fun updateHandles() {
        if ((!selectionMode && activeHandle == null) || !hasSelection() || !hasWindowFocus() || !isShown || layout == null) {
            hideHandles()
            return
        }
        if (startHandle == null) startHandle = SelectionHandle(true)
        if (endHandle == null) endHandle = SelectionHandle(false)
        startHandle?.position()
        endHandle?.position()
    }

    private fun caretPosition(start: Boolean): Pair<Float, Float> {
        val offset = (if (start) selectionStart else selectionEnd).coerceIn(0, text?.length ?: 0)
        val line = layout.getLineForOffset(offset)
        return layout.getPrimaryHorizontal(offset) + totalPaddingLeft - scrollX to
            layout.getLineBottom(line).toFloat() + totalPaddingTop - scrollY
    }

    private inner class SelectionHandle(val start: Boolean) : View(context) {
        private val leftDrawable = AppCompatResources.getDrawable(context,
            androidx.appcompat.R.drawable.abc_text_select_handle_left_mtrl_dark)!!.mutate().apply {
            setTint(context.getColor(R.color.kodaLightBlue))
        }
        private val rightDrawable = AppCompatResources.getDrawable(context,
            androidx.appcompat.R.drawable.abc_text_select_handle_right_mtrl_dark)!!.mutate().apply {
            setTint(context.getColor(R.color.kodaLightBlue))
        }
        init {
            contentDescription = context.getString(if (start) R.string.selection_start else R.string.selection_end)
        }
        private val touchSize = (48 * density).roundToInt()
        private var drawableLeft = 0
        private var pointsLeft = start
        private var lastLeft = Int.MIN_VALUE
        private var lastTop = Int.MIN_VALUE
        private var lastWidth = 0
        val popup = PopupWindow(this, touchSize, touchSize).apply {
            // Stay above the transparent platform handles so they cannot capture the drag.
            windowLayoutType = HANDLE_WINDOW_TYPE
            isClippingEnabled = false
            if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
            isFocusable = false
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
        }

        override fun onDraw(canvas: Canvas) {
            val drawable = if (pointsLeft) leftDrawable else rightDrawable
            drawable.setBounds(drawableLeft, 0, drawableLeft + drawable.intrinsicWidth,
                drawable.intrinsicHeight)
            drawable.draw(canvas)
        }

        fun position() {
            val (x, y) = caretPosition(start)
            this@SelectionEditText.getLocationOnScreen(location)
            val screenX = location[0] + x
            val screenY = location[1] + y
            if (viewport?.getGlobalVisibleRect(visible) != true ||
                (activeHandle !== this && !visible.contains(screenX.toInt(), (screenY - 1).toInt()))) {
                popup.dismiss()
                return
            }
            val (otherX, otherY) = caretPosition(!start)
            val otherScreenX = location[0] + otherX
            val otherScreenY = location[1] + otherY
            // Use the same teardrop geometry and caret hotspot as Android's handles.
            // Direction follows the endpoint's text run, never the other endpoint's x.
            val offset = (if (start) selectionStart else selectionEnd).coerceIn(0, length())
            val rtl = layout.isRtlCharAt((if (start) offset else offset - 1).coerceAtLeast(0))
            val facesLeft = start != rtl
            val drawable = if (facesLeft) leftDrawable else rightDrawable
            val hotspot = drawable.intrinsicWidth * if (facesLeft) 3 / 4f else 1 / 4f
            val idealLeft = screenX - hotspot
            val size = touchSize.coerceAtMost(visible.width())
            var left = (idealLeft - (size - drawable.intrinsicWidth) / 2f).roundToInt()
                .coerceIn(visible.left, visible.right - size)
            var right = left + size
            var top = screenY.roundToInt().coerceIn(visible.top,
                (visible.bottom - touchSize).coerceAtLeast(visible.top))
            val otherTop = otherScreenY.roundToInt().coerceIn(visible.top,
                (visible.bottom - touchSize).coerceAtLeast(visible.top))
            val otherVisible = activeHandle?.start == !start ||
                visible.contains(otherScreenX.toInt(), (otherScreenY - 1).toInt())
            if (otherVisible && top < otherTop + touchSize && otherTop < top + touchSize) {
                val onLeft = screenX < otherScreenX || (screenX == otherScreenX && start)
                val split = ((screenX + otherScreenX) / 2).roundToInt()
                    .coerceIn(visible.left + 1, visible.right - 1)
                if (onLeft) right = minOf(right, split) else left = maxOf(left, split)
            }
            val popupWidth = (right - left).coerceAtLeast(1)
            val newDrawableLeft = (idealLeft - left).roundToInt()
                .coerceIn(0, (popupWidth - drawable.intrinsicWidth).coerceAtLeast(0))
            if (newDrawableLeft != drawableLeft || facesLeft != pointsLeft) {
                drawableLeft = newDrawableLeft
                pointsLeft = facesLeft
                invalidate()
            }
            if (Build.VERSION.SDK_INT < 29) {
                // Older PopupWindow versions position relative to the application window.
                this@SelectionEditText.rootView.getLocationOnScreen(windowOnScreen)
                this@SelectionEditText.rootView.getLocationInWindow(windowInWindow)
                left -= windowOnScreen[0] - windowInWindow[0]
                top -= windowOnScreen[1] - windowInWindow[1]
            }
            if (popup.isShowing) {
                if (left != lastLeft || top != lastTop || popupWidth != lastWidth) {
                    popup.update(left, top, popupWidth, touchSize)
                }
            } else {
                popup.width = popupWidth
                popup.showAtLocation(this@SelectionEditText, Gravity.NO_GRAVITY, left, top)
            }
            lastLeft = left
            lastTop = top
            lastWidth = popupWidth
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeHandle = this
                    selectionViewportOwned = true
                    fixedOffset = if (start) selectionEnd else selectionStart
                    viewport?.selectionDragging = true
                    viewport?.resetFling()
                    val (x, y) = caretPosition(start)
                    this@SelectionEditText.getLocationOnScreen(location)
                    touchOffsetX = event.rawX - location[0] - x
                    touchOffsetY = event.rawY - location[1] - y + lineHeight / 2f
                    pointerX = event.rawX
                    pointerY = event.rawY
                    lastFrame = SystemClock.uptimeMillis()
                    lastToolbarHide = 0L
                    removeCallbacks(selectionFrame)
                    postOnAnimation(selectionFrame)
                }
                MotionEvent.ACTION_MOVE -> {
                    pointerX = event.rawX
                    pointerY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    extendSelection()
                    stopSelectionDrag()
                    updateHandles()
                    performClick()
                }
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()
    }

    override fun performClick(): Boolean = super.performClick()

    override fun performLongClick(): Boolean {
        val handled = super.performLongClick()
        if (handled && hasSelection()) {
            directSelectionGesture = true
            directAnchorStart = selectionStart
            directAnchorEnd = selectionEnd
        }
        return handled
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        selectionViewportOwned = false
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            selectionViewportOwned = false
            directSelectionGesture = !selectionMode
            directAnchorStart = -1
            directAnchorEnd = -1
        }
        // Once our long-press drag owns selection, do not also run Android's
        // word-selection drag handler on every MOVE.
        val handled = if (directSelectionGesture && activeHandle != null &&
            event.actionMasked == MotionEvent.ACTION_MOVE) true else super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hasSelection()) {
            directAnchorStart = selectionStart
            directAnchorEnd = selectionEnd
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && directSelectionGesture &&
            hasSelection()) {
            if (activeHandle == null) {
                if (startHandle == null) startHandle = SelectionHandle(true)
                if (endHandle == null) endHandle = SelectionHandle(false)
                if (directAnchorStart < 0) {
                    directAnchorStart = selectionStart
                    directAnchorEnd = selectionEnd
                }
                selectionViewportOwned = true
                viewport?.selectionDragging = true
                viewport?.resetFling()
                touchOffsetX = 0f
                touchOffsetY = 0f
                lastFrame = SystemClock.uptimeMillis()
                lastToolbarHide = 0L
                postOnAnimation(selectionFrame)
            }
            activeHandle = if (getOffsetForPosition(event.x, event.y) < directAnchorStart)
                startHandle else endHandle
            fixedOffset = if (activeHandle === startHandle) directAnchorEnd else directAnchorStart
            pointerX = event.rawX
            pointerY = event.rawY
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (directSelectionGesture) stopSelectionDrag()
            directSelectionGesture = false
        }
        return handled
    }

    private fun extendSelection() {
        val handle = activeHandle ?: return
        if (!hasSelection()) return stopSelectionDrag()
        getLocationOnScreen(location)
        val parent = viewport ?: return stopSelectionDrag()
        parent.getGlobalVisibleRect(visible)
        val offset = when {
            handle.start && pointerY <= visible.top + 8 * density &&
                !parent.canScrollVertically(-1) -> 0
            !handle.start && pointerY >= visible.bottom - 8 * density &&
                !parent.canScrollVertically(1) -> length()
            else -> getOffsetForPosition(pointerX - touchOffsetX - location[0],
                pointerY - touchOffsetY - location[1])
        }
        val anchor = fixedOffset.coerceIn(0, length())
        val start = if (handle.start) offset.coerceAtMost(anchor - 1).coerceAtLeast(0) else anchor
        val end = if (handle.start) anchor else offset.coerceAtLeast(anchor + 1).coerceAtMost(length())
        if (start != selectionStart || end != selectionEnd) {
            // As in Selection.setSelection, mark the in-progress spans as intermediate.
            // Defer the final notification until release, keeping Android from rebuilding
            // its menu on every frame when the original start handle is offscreen.
            text?.let {
                val flags = Spanned.SPAN_POINT_POINT or Spanned.SPAN_INTERMEDIATE
                it.setSpan(Selection.SELECTION_START, start, start, flags)
                it.setSpan(Selection.SELECTION_END, end, end, flags)
                pendingSelectionChange = true
            }
        }
    }

    private fun edgeSpeed(coordinate: Float, low: Int, high: Int): Float {
        val edge = (48 * density).coerceAtMost((high - low) / 3f)
        if (edge <= 0) return 0f
        val penetration = when {
            coordinate < low + edge -> (coordinate - low - edge) / edge
            coordinate > high - edge -> (coordinate - high + edge) / edge
            else -> 0f
        }.coerceIn(-1f, 1f)
        return penetration * abs(penetration) * 1800 * density
    }

    private val selectionFrame = object : Runnable {
        override fun run() {
            if (activeHandle == null) return
            val parent = viewport ?: return stopSelectionDrag()
            if (!parent.getGlobalVisibleRect(visible)) return stopSelectionDrag()
            val now = SystemClock.uptimeMillis()
            if (now - lastToolbarHide >= 1000) {
                selectionActionMode?.hide(-1)
                lastToolbarHide = now
            }
            val elapsed = (now - lastFrame).coerceIn(1, 100) / 1000f
            lastFrame = now
            val dx = if (parent.wrapLines) 0 else
                (edgeSpeed(pointerX, visible.left, visible.right) * elapsed).roundToInt()
            val dy = (edgeSpeed(pointerY, visible.top, visible.bottom) * elapsed).roundToInt()
            if (dx != 0 || dy != 0) {
                parent.scrollBy(dx, dy)
            }
            // Consume the newest pointer once per display frame. Updating selection
            // and two popup windows for every input sample caused redundant work.
            extendSelection()
            updateHandles()
            postOnAnimation(this)
        }
    }

    private fun stopSelectionDrag() {
        if (pendingSelectionChange) {
            pendingSelectionChange = false
            text?.let {
                val end = selectionEnd
                if (end >= 0 && (it.getSpanFlags(Selection.SELECTION_END) and Spanned.SPAN_INTERMEDIATE) != 0) {
                    it.setSpan(Selection.SELECTION_END, end, end, Spanned.SPAN_POINT_POINT)
                }
            }
        }
        if (activeHandle != null) selectionActionMode?.hide(0)
        activeHandle = null
        viewport?.selectionDragging = false
        removeCallbacks(selectionFrame)
        if (!selectionMode) hideHandles()
    }
}
