package com.cloudorz.openmonitor.core.common

/**
 * Represents the result of executing a shell command, capturing the exit code
 * and both standard output and standard error streams.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    /**
     * Returns true if the command completed successfully (exit code 0).
     */
    val isSuccess: Boolean
        get() = exitCode == 0

    /**
     * Returns [stdout] if the command succeeded, otherwise returns [stderr].
     * If both are blank, returns an empty string.
     */
    val outputOrError: String
        get() = if (isSuccess) stdout else stderr

    companion object {
        /**
         * Creates a successful [CommandResult] with the given output.
         */
        fun success(stdout: String): CommandResult =
            CommandResult(exitCode = 0, stdout = stdout, stderr = "")

        /**
         * Creates a failed [CommandResult] with the given error message and exit code.
         */
        fun failure(stderr: String, exitCode: Int = 1): CommandResult =
            CommandResult(exitCode = exitCode, stdout = "", stderr = stderr)
    }
}
