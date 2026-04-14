package com.cloudorz.openmonitor.core.common

/**
 * Abstraction for executing shell commands at various privilege levels.
 * Each implementation corresponds to a specific [PrivilegeMode] and handles
 * command execution and file reading accordingly.
 */
interface ShellExecutor {

    /**
     * Executes the given shell command and returns the result.
     *
     * @param command The shell command string to execute.
     * @return A [CommandResult] containing exit code, stdout, and stderr.
     */
    suspend fun execute(command: String): CommandResult

    /**
     * Executes the given shell command with root privileges.
     * For executors that already operate as root, this behaves identically to [execute].
     * For non-root executors, this may fail or fall back to [execute].
     *
     * @param command The shell command string to execute as root.
     * @return A [CommandResult] containing exit code, stdout, and stderr.
     */
    suspend fun executeAsRoot(command: String): CommandResult

    /**
     * Reads the contents of a file at the given path.
     *
     * @param path Absolute path to the file to read.
     * @return The file contents as a string, or null if the file cannot be read.
     */
    suspend fun readFile(path: String): String?

    /**
     * Checks whether this executor's underlying mechanism is currently available.
     *
     * @return True if the executor can be used, false otherwise.
     */
    suspend fun isAvailable(): Boolean

    /**
     * The [PrivilegeMode] associated with this executor.
     */
    val mode: PrivilegeMode
}
