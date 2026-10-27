package com.kevinsrm.turbotouch.core

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val ok: Boolean get() = exitCode == 0

    fun combined(): String = listOf(stdout, stderr)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
}
