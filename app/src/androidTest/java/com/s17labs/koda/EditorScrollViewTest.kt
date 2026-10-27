package com.s17labs.koda

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.os.Parcelable
import android.os.Parcel
import android.util.SparseArray
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.text.InputType
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorScrollViewTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var viewport: EditorScrollView
    private lateinit var editor: EditText
    private val document = (1..120).joinToString("\n") { "Line $it: " + "abcdefghij ".repeat(35) }

    private fun ui(action: () -> Unit) {
        instrumentation.runOnMainSync(action)
        instrumentation.waitForIdleSync()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    @Before fun openEditor() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("koda_prefs", 0).edit()
            .putBoolean("open_new", true).putBoolean("open_last", false)
            .putBoolean("auto_save", false).commit()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ui {
            assertTrue(activity.findViewById<View>(R.id.editorScrollView) is EditorScrollView)
            viewport = EditorScrollView(activity)
            editor = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                showSoftInputOnFocus = false
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setText(document)
            }
            viewport.addView(editor, FrameLayout.LayoutParams(-1, -2))
            viewport.wrapLines = false
            val root = FrameLayout(activity)
            root.addView(viewport, FrameLayout.LayoutParams(dp(280), dp(240)))
            activity.setContentView(root)
        }
        ui { viewport.resetScroll() }
    }

    @After fun closeEditor() {
        ui { activity.finish() }
    }

    private fun resize(width: Int, height: Int) = ui {
        viewport.layoutParams = (viewport.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = dp(width)
            this.height = dp(height)
        }
    }

    private fun drag(dx: Float, dy: Float) = ui {
        val start = SystemClock.uptimeMillis()
        val x = dp(220).toFloat()
        val y = dp(190).toFloat()
        for (step in 0..6) {
            val event = MotionEvent.obtain(start, start + step * 20L,
                if (step == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE,
                x - dx * step / 6, y - dy * step / 6, 0)
            viewport.dispatchTouchEvent(event)
            event.recycle()
        }
        val cancel = MotionEvent.obtain(start, start + 140, MotionEvent.ACTION_CANCEL, x - dx, y - dy, 0)
        viewport.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    @Test fun verticalHorizontalAndDiagonalDragsReachBothAxes() {
        drag(dp(100).toFloat(), 0f)
        ui { assertTrue(viewport.scrollX > 0); assertEquals(0, viewport.scrollY); viewport.resetScroll() }
        drag(0f, dp(100).toFloat())
        ui { assertEquals(0, viewport.scrollX); assertTrue(viewport.scrollY > 0); viewport.resetScroll() }
        drag(dp(100).toFloat(), dp(100).toFloat())
        ui { assertTrue(viewport.scrollX > 0); assertTrue(viewport.scrollY > 0) }
    }

    @Test fun wrappingReflowsAndDisablesHorizontalPanning() {
        ui { viewport.scrollTo(dp(150), dp(100)); viewport.wrapLines = true }
        ui {
            assertEquals(viewport.width, editor.width)
            assertTrue(editor.lineCount > 120)
            assertEquals(0, viewport.scrollX)
        }
        drag(dp(100).toFloat(), dp(60).toFloat())
        ui { assertEquals(0, viewport.scrollX); assertTrue(viewport.scrollY > 0); viewport.wrapLines = false }
        ui { assertTrue(editor.width > viewport.width); assertEquals(120, editor.lineCount) }
    }

    @Test fun emptyAndShortFilesHaveNoPhantomScrollAfterResize() {
        for (text in listOf("", "hello")) {
            ui { editor.setText(text) }
            for (height in listOf(120, 300, 160)) {
                resize(260, height)
                ui {
                    assertEquals(viewport.width, editor.width)
                    assertEquals(viewport.height, editor.height)
                    viewport.scrollTo(100000, 100000)
                    assertEquals(0, viewport.scrollX)
                    assertEquals(0, viewport.scrollY)
                }
            }
        }
    }

    @Test fun shortWideDocumentsKeepTheirHorizontalRange() {
        ui { editor.setText("a very long line ".repeat(150)) }
        ui {
            assertTrue(editor.width > viewport.width)
            assertEquals(viewport.height, editor.height)
            viewport.scrollTo(1000000, 1000000)
            assertEquals(editor.width - viewport.width, viewport.scrollX)
            assertEquals(0, viewport.scrollY)
        }
    }

    @Test fun resizingAndFontChangesKeepDocumentBoundsReachable() {
        for (wrap in listOf(false, true)) {
            ui { viewport.wrapLines = wrap; editor.textSize = 22f }
            for ((width, height) in listOf(240 to 140, 300 to 280)) {
                resize(width, height)
                ui {
                    viewport.scrollTo(1000000, 1000000)
                    assertEquals(if (wrap) 0 else editor.width - viewport.width, viewport.scrollX)
                    assertEquals(editor.height - viewport.height, viewport.scrollY)
                }
            }
        }
    }

    @Test fun shrinkingDocumentClampsBothScrollOffsets() {
        ui { viewport.scrollTo(1000000, 1000000); editor.setText("short") }
        ui { assertEquals(0, viewport.scrollX); assertEquals(0, viewport.scrollY) }
    }

    @Test fun caretRectangleIsVisibleOnBothAxesAfterKeyboardResize() {
        resize(260, 120)
        ui {
            val caret = Rect(editor.width - dp(24), editor.height - dp(24),
                editor.width - dp(20), editor.height - dp(4))
            assertTrue(viewport.requestChildRectangleOnScreen(editor, caret, true))
            assertTrue(caret.right <= viewport.scrollX + viewport.width)
            assertTrue(caret.left >= viewport.scrollX)
            assertTrue(caret.bottom <= viewport.scrollY + viewport.height)
            assertTrue(caret.top >= viewport.scrollY)
        }
    }

    @Test fun movingTheCaretToTheEndRevealsTheLastLongLine() {
        ui {
            editor.requestFocus()
            editor.setSelection(editor.length())
            editor.bringPointIntoView(editor.length())
        }
        ui {
            assertTrue(viewport.scrollX > 0)
            assertTrue(viewport.scrollY > 0)
            assertEquals(editor.length(), editor.selectionEnd)
        }
    }

    @Test fun resettingForAnotherTabStopsTheFlingAndResetsBothAxes() {
        ui { viewport.scrollTo(dp(100), dp(100)); viewport.fling(3000) }
        SystemClock.sleep(100)
        ui { assertTrue(viewport.scrollY > dp(100)); viewport.resetScroll() }
        SystemClock.sleep(100)
        ui { assertEquals(0, viewport.scrollX); assertEquals(0, viewport.scrollY) }
    }

    @Test fun pointerSwitchDoesNotJumpOrLoseTheDrag() {
        ui {
            val start = SystemClock.uptimeMillis()
            fun send(action: Int, positions: List<Triple<Int, Float, Float>>, time: Long) {
                val properties = positions.map { MotionEvent.PointerProperties().apply {
                    id = it.first; toolType = MotionEvent.TOOL_TYPE_FINGER
                } }.toTypedArray()
                val coords = positions.map { MotionEvent.PointerCoords().apply {
                    x = it.second; y = it.third; pressure = 1f; size = 1f
                } }.toTypedArray()
                val event = MotionEvent.obtain(start, start + time, action, positions.size, properties, coords,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                viewport.dispatchTouchEvent(event)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, listOf(Triple(0, 210f, 180f)), 0)
            send(MotionEvent.ACTION_MOVE, listOf(Triple(0, 160f, 130f)), 20)
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(Triple(0, 160f, 130f), Triple(1, 150f, 120f)), 40)
            send(MotionEvent.ACTION_POINTER_UP, listOf(Triple(0, 160f, 130f), Triple(1, 150f, 120f)), 60)
            val beforeX = viewport.scrollX
            val beforeY = viewport.scrollY
            send(MotionEvent.ACTION_MOVE, listOf(Triple(1, 120f, 90f)), 80)
            assertEquals(30, viewport.scrollX - beforeX)
            assertEquals(30, viewport.scrollY - beforeY)
            send(MotionEvent.ACTION_CANCEL, listOf(Triple(1, 120f, 90f)), 100)
        }
    }

    @Test fun mouseAndShiftWheelPanTheIntendedAxes() {
        ui {
            fun wheel(horizontal: Float, vertical: Float, shift: Boolean = false) {
                val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
                val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
                    x = 100f; y = 100f
                    setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
                    setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
                })
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates,
                    if (shift) KeyEvent.META_SHIFT_ON else 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                viewport.dispatchGenericMotionEvent(event)
                event.recycle()
            }
            wheel(2f, -2f)
            assertTrue(viewport.scrollX > 0)
            assertTrue(viewport.scrollY > 0)
            viewport.resetScroll()
            wheel(0f, -2f, true)
            assertTrue(viewport.scrollX > 0)
            assertEquals(0, viewport.scrollY)
        }
    }

    @Test fun accessibilityCanScrollHorizontallyWithoutLosingVerticalPosition() {
        ui {
            viewport.scrollTo(0, dp(100))
            assertTrue(viewport.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id, null))
        }
        SystemClock.sleep(400)
        ui {
            assertTrue(viewport.scrollX > 0)
            assertEquals(dp(100), viewport.scrollY)
            viewport.wrapLines = true
        }
        ui { assertFalse(viewport.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id, null)) }
    }

    @Test fun viewStateRestoresBothScrollAxes() {
        val saved = SparseArray<Parcelable>()
        var savedX = 0
        var savedY = 0
        val viewId = View.generateViewId()
        ui {
            viewport.id = viewId
            viewport.scrollTo(dp(100), dp(200))
            savedX = viewport.scrollX
            savedY = viewport.scrollY
            viewport.saveHierarchyState(saved)
            val parent = viewport.parent as FrameLayout
            parent.removeView(viewport)
            viewport = EditorScrollView(activity).apply { id = viewId }
            editor = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                showSoftInputOnFocus = false
                setText(document)
            }
            viewport.addView(editor, FrameLayout.LayoutParams(-1, -2))
            viewport.wrapLines = false
            parent.addView(viewport, FrameLayout.LayoutParams(dp(280), dp(240)))
            val parcel = Parcel.obtain()
            parcel.writeSparseArray(saved)
            parcel.setDataPosition(0)
            val restored = parcel.readSparseArray<Parcelable>(EditorScrollView::class.java.classLoader)!!
            parcel.recycle()
            viewport.restoreHierarchyState(restored)
        }
        ui { assertEquals(savedX, viewport.scrollX); assertEquals(savedY, viewport.scrollY) }
    }

    @Test fun selectionCanRetainTheTouchGesture() {
        ui {
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 200f, 160f, 0)
            viewport.dispatchTouchEvent(down)
            down.recycle()
            viewport.requestDisallowInterceptTouchEvent(true)
            val move = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_MOVE, 80f, 40f, 0)
            viewport.dispatchTouchEvent(move)
            move.recycle()
            assertEquals(0, viewport.scrollX)
            assertEquals(0, viewport.scrollY)
            val cancel = MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_CANCEL, 80f, 40f, 0)
            viewport.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
    }
}
