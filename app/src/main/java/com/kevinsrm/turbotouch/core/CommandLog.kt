package com.kevinsrm.turbotouch.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandEntry(
    val time: String,
    val command: String,
    val exitCode: Int,
    val output: String
)

/** Historico circular dos comandos executados, mostrado na tela de diagnostico. */
object CommandLog {

    private const val MAX_ENTRIES = 200
    private const val TAG = "TurboTouch"

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _entries = MutableStateFlow<List<CommandEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun record(command: String, result: ShellResult) {
        val entry = CommandEntry(
            time = formatter.format(Date()),
            command = command,
            exitCode = result.exitCode,
            output = result.combined().take(400)
        )
        _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        if (result.ok) {
            Log.d(TAG, "$command -> ${result.exitCode}")
        } else {
            Log.w(TAG, "$command -> ${result.exitCode} ${result.combined()}")
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
