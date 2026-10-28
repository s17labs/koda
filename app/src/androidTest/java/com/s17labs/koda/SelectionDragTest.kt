package com.s17labs.koda

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionDragTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var editor: SelectionEditText
    private lateinit var viewport: EditorScrollView
    private val bounds = Rect()
    @Volatile private var modeStarted = false

    private fun ui(action: () -> Unit) {
        var dragging = false
        instrumentation.runOnMainSync {
            action()
            dragging = ::viewport.isInitialized && viewport.selectionDragging
        }
        // Edge scrolling intentionally keeps producing frames until the finger lifts.
        if (!dragging) instrumentation.waitForIdleSync()
    }

    @Before fun open() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("koda_prefs", 0).edit()
            .putBoolean("open_new", true).putBoolean("open_last", false)
            .putBoolean("wrap_lines", true).putString("language", "English").commit()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ui {
            editor = activity.findViewById(R.id.textEditor)
            viewport = activity.findViewById(R.id.editorScrollView)
            editor.showSoftInputOnFocus = false
            editor.setText((1..400).joinToString("\n") { "Line $it: selection scrolling example." })
            editor.requestFocus()
            val callback = editor.customSelectionActionModeCallback!!
            editor.customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    modeStarted = true
                    return callback.onCreateActionMode(mode, menu)
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) =
                    callback.onPrepareActionMode(mode, menu)
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
                    callback.onActionItemClicked(mode, item)
                override fun onDestroyActionMode(mode: ActionMode) {
                    modeStarted = false
                    callback.onDestroyActionMode(mode)
                }
            }
            editor.setSelection(0)
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(editor.windowToken, 0)
        }
        val location = IntArray(2)
        ui { editor.getLocationOnScreen(location) }
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, location[0] + 30f, location[1] + 25f)
        SystemClock.sleep(700)
        event(down, MotionEvent.ACTION_UP, location[0] + 30f, location[1] + 25f)
        waitForSelectionMode()
        ui {
            editor.setSelection(0, editor.layout.getLineEnd(3))
            viewport.resetScroll()
        }
    }

    @After fun close() = ui { activity.finish() }

    private fun waitForSelectionMode() {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!modeStarted && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("native selection action mode must start", modeStarted)
        instrumentation.waitForIdleSync()
    }

    private fun handlePoint(start: Boolean): Pair<Float, Float> {
        var point = 0f to 0f
        onView(withContentDescription(if (start) "Selection start" else "Selection end"))
            .inRoot(withDecorView(hasDescendant(withContentDescription(
                if (start) "Selection start" else "Selection end"))))
            .perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isDisplayed()
                override fun getDescription() = "locate selection handle"
                override fun perform(controller: UiController, view: View) {
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    point = location[0] + view.width / 2f to location[1] + view.height / 2f
                }
            })
        return point
    }

    private fun event(down: Long, action: Int, x: Float, y: Float) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        motion.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.sendPointerSync(motion)
        motion.recycle()
    }

    private fun dragAndHold(start: Boolean, downwards: Boolean) {
        val (x, y) = handlePoint(start)
        ui { viewport.getGlobalVisibleRect(bounds) }
        val targetY = if (downwards) bounds.bottom - 2f else bounds.top + 2f
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, x, y)
        try {
            ui { assertTrue("visible handle receives the drag", viewport.selectionDragging) }
            for (step in 1..10) {
                event(down, MotionEvent.ACTION_MOVE, x, y + (targetY - y) * step / 10)
                SystemClock.sleep(20)
            }
            var initialScroll = 0
            var initialOffset = 0
            ui {
                initialScroll = viewport.scrollY
                initialOffset = if (start) editor.selectionStart else editor.selectionEnd
            }
            val deadline = SystemClock.uptimeMillis() + 8000
            var advanced = false
            while (!advanced && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(200)
                ui {
                    advanced = if (downwards) viewport.scrollY > initialScroll ||
                        !viewport.canScrollVertically(1)
                    else viewport.scrollY < initialScroll || !viewport.canScrollVertically(-1)
                }
            }
            ui {
                if (downwards) {
                    assertTrue("viewport keeps scrolling while finger is stationary", viewport.scrollY > initialScroll || !viewport.canScrollVertically(1))
                    assertTrue("selection extends into newly revealed text", editor.selectionEnd > initialOffset || editor.selectionEnd == editor.length())
                } else {
                    assertTrue("viewport scrolls upwards", viewport.scrollY < initialScroll || !viewport.canScrollVertically(-1))
                    assertTrue("selection extends upwards", editor.selectionStart < initialOffset || editor.selectionStart == 0)
                }
            }
        } finally {
            event(down, MotionEvent.ACTION_UP, x, targetY)
        }
        var released = 0
        ui {
            assertFalse("lifting the finger ends the selection drag", viewport.selectionDragging)
            released = viewport.scrollY
        }
        SystemClock.sleep(200)
        ui { assertEquals("release stops auto-scroll", released, viewport.scrollY) }
    }

    @Test fun endHandleContinuouslySelectsBelowViewport() = dragAndHold(false, true)

    @Test fun startHandleContinuouslySelectsAboveViewport() {
        ui {
            editor.setSelection(editor.layout.getLineStart(100), editor.layout.getLineEnd(105))
            viewport.scrollTo(0, editor.layout.getLineTop(98))
        }
        dragAndHold(true, false)
    }

    @Test fun bottomEdgeCanSelectThroughTheLastCharacter() {
        ui { editor.setText((1..30).joinToString("\n") { "Line $it: selection test." }) }
        // Changing text ends action mode; start another real long press.
        val location = IntArray(2)
        ui { editor.setSelection(0); viewport.resetScroll(); editor.getLocationOnScreen(location) }
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, location[0] + 30f, location[1] + 25f)
        SystemClock.sleep(700)
        event(down, MotionEvent.ACTION_UP, location[0] + 30f, location[1] + 25f)
        waitForSelectionMode()
        ui { editor.setSelection(0, editor.layout.getLineEnd(2)); viewport.resetScroll() }
        dragAndHold(false, true)
        ui { assertEquals("last character is reachable", editor.length(), editor.selectionEnd) }
    }

    @Test fun initialLongPressDragKeepsSelectingWithoutLifting() {
        val location = IntArray(2)
        ui { editor.setSelection(0); viewport.resetScroll(); editor.getLocationOnScreen(location) }
        // A single tap exits the previous selection action mode.
        var down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, location[0] + 30f, location[1] + 25f)
        event(down, MotionEvent.ACTION_UP, location[0] + 30f, location[1] + 25f)
        SystemClock.sleep(500)
        ui { viewport.getGlobalVisibleRect(bounds) }
        down = SystemClock.uptimeMillis()
        val x = location[0] + 30f
        val y = location[1] + 25f
        event(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(700)
        for (step in 1..10) {
            event(down, MotionEvent.ACTION_MOVE, x, y + (bounds.bottom - 2f - y) * step / 10)
            SystemClock.sleep(20)
        }
        var before = 0
        ui { before = editor.selectionEnd; assertTrue(editor.hasSelection()) }
        SystemClock.sleep(1000)
        ui { assertTrue("held long-press drag extends selection", editor.selectionEnd > before) }
        event(down, MotionEvent.ACTION_UP, x, bounds.bottom - 2f)
    }

    @Test fun initialLongPressDragCanExtendUpwards() {
        val location = IntArray(2)
        ui {
            editor.setSelection(editor.layout.getLineStart(100))
            viewport.scrollTo(0, editor.layout.getLineTop(98))
            viewport.getGlobalVisibleRect(bounds)
            editor.getLocationOnScreen(location)
        }
        val x = location[0] + 30f
        val y = bounds.top + 70f
        var down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, x, y)
        event(down, MotionEvent.ACTION_UP, x, y)
        SystemClock.sleep(500)
        down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, x, y)
        SystemClock.sleep(700)
        for (step in 1..10) {
            event(down, MotionEvent.ACTION_MOVE, x, y + (bounds.top + 2f - y) * step / 10)
            SystemClock.sleep(20)
        }
        var before = 0
        var anchor = 0
        ui { before = editor.selectionStart; anchor = editor.selectionEnd; assertTrue(editor.hasSelection()) }
        SystemClock.sleep(1000)
        ui {
            assertTrue("held long-press drag extends upwards", editor.selectionStart < before)
            assertEquals("opposite end stays anchored", anchor, editor.selectionEnd)
        }
        event(down, MotionEvent.ACTION_UP, x, bounds.top + 2f)
    }

    @Test fun selectionScrollUsesResizedViewportWithKeyboardOpen() {
        var fullHeight = 0
        ui {
            fullHeight = viewport.height
            editor.showSoftInputOnFocus = true
            val ime = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.restartInput(editor)
            ime.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
        }
        val deadline = SystemClock.uptimeMillis() + 10000
        var resized = false
        while (!resized && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            ui { resized = viewport.height < fullHeight }
        }
        ui {
            assertTrue("keyboard must resize editor for this test", resized)
            editor.setSelection(0, editor.layout.getLineEnd(2))
            viewport.resetScroll()
        }
        dragAndHold(false, true)
    }
}
