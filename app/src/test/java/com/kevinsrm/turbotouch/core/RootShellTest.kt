package com.kevinsrm.turbotouch.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellTest {
    @Test
    fun adbModeWithoutConnectionPortFailsClosed() = runBlocking {
        val shell = RootShell(ExecutionMode.ADB_WIFI) { null }
        val result = shell.exec("id")
        assertFalse(result.ok)
        assertTrue(result.stderr.contains("porta de conexão ADB"))
    }

    @Test
    fun standardModeRefusesPrivilegedExecution() = runBlocking {
        val shell = RootShell(ExecutionMode.STANDARD)
        assertFalse(shell.hasRoot())
        val result = shell.exec("echo should-not-run")
        assertFalse(result.ok)
        assertTrue(result.stdout.isEmpty())
    }
}
