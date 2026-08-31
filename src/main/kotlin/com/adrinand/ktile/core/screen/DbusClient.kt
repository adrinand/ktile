package com.adrinand.ktile.core.screen

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.ktile.core.screen.DbusClient")

private const val PROCESS_TIMEOUT_MILLISECONDS = 2_000L
private const val GDBUS_NAME = "gdbus"
private const val QDBUS_NAME = "qdbus"
private const val GDBUS_PATH = "/usr/bin/gdbus"
private const val GDBUS_PATH_ALT = "/bin/gdbus"
private const val QDBUS_PATH = "/usr/bin/qdbus"
private const val QDBUS_PATH_ALT = "/bin/qdbus"
private const val WHICH_COMMAND = "which"

/**
 * Runs a D-Bus client command and returns its stdout, or `null` on failure.
 */
internal fun runDbusClient(
    client: String,
    args: List<String>,
    timeoutMs: Long = PROCESS_TIMEOUT_MILLISECONDS,
): String? {
    return try {
        val process =
            ProcessBuilder(listOf(client) + args)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished || process.exitValue() != 0) {
            logger.fine { "$client ${args.joinToString(" ")} failed: $output" }
            null
        } else {
            output
        }
    } catch (e: IOException) {
        logger.fine { "$client ${args.joinToString(" ")} threw: ${e.message}" }
        null
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logger.fine { "$client ${args.joinToString(" ")} interrupted" }
        null
    }
}

/**
 * Returns the absolute path to `gdbus`, or `null` if it is not available.
 */
internal fun findGdbus(): String? {
    val candidates = listOf(GDBUS_PATH, GDBUS_PATH_ALT)
    val existing = candidates.firstOrNull { File(it).exists() }
    if (existing != null) {
        return existing
    }
    return runShellCommand(listOf(WHICH_COMMAND, GDBUS_NAME))
}

/**
 * Returns the absolute path to `qdbus`, or `null` if it is not available.
 * Falls back to `gdbus` if `qdbus` is missing.
 */
internal fun findQdbusOrGdbus(): String? {
    val qdbusCandidates = listOf(QDBUS_PATH, QDBUS_PATH_ALT)
    val qdbusExisting = qdbusCandidates.firstOrNull { File(it).exists() }
    if (qdbusExisting != null) {
        return qdbusExisting
    }
    val gdbusCandidates = listOf(GDBUS_PATH, GDBUS_PATH_ALT)
    val gdbusExisting = gdbusCandidates.firstOrNull { File(it).exists() }
    if (gdbusExisting != null) {
        return gdbusExisting
    }
    return runShellCommand(listOf(WHICH_COMMAND, QDBUS_NAME))
        ?: runShellCommand(listOf(WHICH_COMMAND, GDBUS_NAME))
}

/**
 * Runs a shell command and returns its trimmed stdout, or `null` on failure.
 */
internal fun runShellCommand(command: List<String>): String? {
    return try {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(PROCESS_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
        if (process.exitValue() == 0 && output.isNotEmpty()) output else null
    } catch (e: IOException) {
        logger.fine { "command ${command.joinToString(" ")} threw: ${e.message}" }
        null
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        logger.fine { "command ${command.joinToString(" ")} interrupted" }
        null
    }
}
