package com.s17labs.koda

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private val logs = mutableListOf<LogEntry>()
    private val maxLogs = 100
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    data class LogEntry(
        val timestamp: String,
        val type: LogType,
        val message: String,
        val tag: String = "Koda"
    )

    enum class LogType {
        INFO,
        WARNING,
        ERROR,
        TOAST,
        INTENT,
        FILE_OPEN,
        FILE_SAVE,
        CRASH
    }

    fun i(message: String, tag: String = "Koda") {
        addLog(LogType.INFO, message, tag)
        Log.i(tag, message)
    }

    fun w(message: String, tag: String = "Koda") {
        addLog(LogType.WARNING, message, tag)
        Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = "Koda") {
        val fullMessage = if (throwable != null) "$message: ${throwable.message}" else message
        addLog(LogType.ERROR, fullMessage, tag)
        Log.e(tag, fullMessage, throwable)
    }

    fun toast(message: String) {
        addLog(LogType.TOAST, message)
    }

    fun intent(action: String, uri: String? = null) {
        val message = if (uri != null) "$action: $uri" else action
        addLog(LogType.INTENT, message)
    }

    fun fileOpen(uri: String, name: String) {
        addLog(LogType.FILE_OPEN, "Opened: $name ($uri)")
    }

    fun fileSave(name: String, path: String?) {
        addLog(LogType.FILE_SAVE, "Saved: $name${if (path != null) " -> $path" else ""}")
    }

    fun crash(message: String, stackTrace: String? = null) {
        val fullMessage = if (stackTrace != null) "$message\n$stackTrace" else message
        addLog(LogType.CRASH, fullMessage)
    }

    private fun addLog(type: LogType, message: String, tag: String = "Koda") {
        synchronized(logs) {
            val entry = LogEntry(
                timestamp = dateFormat.format(Date()),
                type = type,
                message = message,
                tag = tag
            )
            logs.add(entry)
            if (logs.size > maxLogs) {
                logs.removeAt(0)
            }
        }
    }

    fun getLogs(): List<LogEntry> {
        synchronized(logs) {
            return logs.toList()
        }
    }

    fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
    }

    fun exportLogs(): String {
        return synchronized(logs) {
            logs.joinToString("\n") { entry ->
                "[${entry.timestamp}] [${entry.type.name}] ${entry.message}"
            }
        }
    }
}
