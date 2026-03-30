package com.s17labs.koda

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogActivity : AppCompatActivity() {

    private lateinit var textLog: TextView
    private lateinit var btnCopy: ImageButton
    private lateinit var btnMenu: ImageButton

    companion object {
        private const val REQUEST_EXPORT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)

        textLog = findViewById(R.id.textLog)
        btnCopy = findViewById(R.id.btnCopy)
        btnMenu = findViewById(R.id.btnMenu)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        btnCopy.setOnClickListener {
            val logs = DebugLog.exportLogs()
            if (logs.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Debug Log", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.no_logs, Toast.LENGTH_SHORT).show()
            }
        }

        btnMenu.setOnClickListener { view ->
            showMenu(view)
        }

        refreshLog()
    }

    private fun showMenu(anchorView: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_menu, null)
        val container = popupView.findViewById<LinearLayout>(R.id.customMenuContainer)

        val menuItems = listOf(
            Triple(R.drawable.ic_menu_export, getString(R.string.export)) { exportLogs() },
            Triple(R.drawable.ic_menu_clear, getString(R.string.clear)) { clearLogs() }
        )

        for ((iconRes, title, action) in menuItems) {
            val itemView = layoutInflater.inflate(R.layout.menu_item, container, false)
            itemView.findViewById<ImageView>(R.id.menuIcon).setImageResource(iconRes)
            itemView.findViewById<TextView>(R.id.menuText).text = title
            itemView.setOnClickListener {
                menuPopupWindow?.dismiss()
                action()
            }
            container.addView(itemView)
        }

        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(null)
        popupWindow.isFocusable = true
        popupWindow.elevation = 8f
        popupWindow.showAtLocation(anchorView, Gravity.TOP or Gravity.END, 16, anchorView.height + 8)
        menuPopupWindow = popupWindow
        popupWindow.setOnDismissListener { menuPopupWindow = null }
    }

    private var menuPopupWindow: PopupWindow? = null

    private fun exportLogs() {
        val logs = DebugLog.exportLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, R.string.no_logs, Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val fileName = "Koda_debug_log_$timestamp.txt"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun clearLogs() {
        DebugLog.clearLogs()
        refreshLog()
        Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val logs = DebugLog.exportLogs()
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(logs.toByteArray())
                    }
                    Toast.makeText(this, R.string.logs_exported, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    DebugLog.e("Failed to export logs", e)
                    Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {
        val logs = DebugLog.getLogs()
        if (logs.isEmpty()) {
            textLog.text = getString(R.string.no_logs)
            return
        }

        val coloredLog = logs.joinToString("\n\n") { entry ->
            val color = when (entry.type) {
                DebugLog.LogType.ERROR, DebugLog.LogType.CRASH -> "#FF6B6B"
                DebugLog.LogType.WARNING -> "#FFE66D"
                DebugLog.LogType.TOAST -> "#4ECDC4"
                DebugLog.LogType.INTENT -> "#95E1D3"
                DebugLog.LogType.FILE_OPEN -> "#A8E6CF"
                DebugLog.LogType.FILE_SAVE -> "#88D8B0"
                DebugLog.LogType.INFO -> "#AAAAAA"
            }
            "[${entry.timestamp}] [${entry.type.name}] ${entry.message}"
        }

        textLog.text = coloredLog
    }
}
