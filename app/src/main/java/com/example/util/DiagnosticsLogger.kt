package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val id: Long,
    val timestamp: Long,
    val tag: String,
    val level: String, // "I", "D", "W", "E"
    val message: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun toLogLine(): String = "[${formattedTime()}] [$level/$tag] $message"
}

object DiagnosticsLogger {
    private const val MAX_LOGS = 1000
    private var nextId = 1L
    private val lock = Any()
    private val buffer = mutableListOf<DiagnosticLogEntry>()

    private val _logsFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<DiagnosticLogEntry>> = _logsFlow.asStateFlow()

    private fun addEntry(tag: String, level: String, message: String) {
        synchronized(lock) {
            val entry = DiagnosticLogEntry(
                id = nextId++,
                timestamp = System.currentTimeMillis(),
                tag = tag,
                level = level,
                message = message
            )
            buffer.add(entry)
            if (buffer.size > MAX_LOGS) {
                buffer.removeAt(0)
            }
            _logsFlow.value = buffer.toList()
        }
    }

    fun d(tag: String, message: String) {
        try { Log.d(tag, message) } catch (_: Throwable) {}
        addEntry(tag, "D", message)
    }

    fun i(tag: String, message: String) {
        try { Log.i(tag, message) } catch (_: Throwable) {}
        addEntry(tag, "I", message)
    }

    fun w(tag: String, message: String) {
        try { Log.w(tag, message) } catch (_: Throwable) {}
        addEntry(tag, "W", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        } catch (_: Throwable) {}

        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message ?: throwable.javaClass.simpleName}"
        } else {
            message
        }
        addEntry(tag, "E", fullMessage)
    }

    fun getAllLogs(): List<DiagnosticLogEntry> {
        synchronized(lock) {
            return buffer.toList()
        }
    }

    fun getFormattedLogs(): String {
        synchronized(lock) {
            return buffer.joinToString(separator = "\n") { it.toLogLine() }
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _logsFlow.value = emptyList()
        }
    }
}
