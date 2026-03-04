package com.cloudorz.monitor.core.common

/**
 * Represents the privilege escalation mode used for executing system commands
 * and reading protected system files.
 */
enum class PrivilegeMode(
    val displayName: String,
    val description: String,
) {
    ROOT(
        displayName = "Root",
        description = "Full root access via su binary. Provides unrestricted access to all system files and commands.",
    ),
    ADB(
        displayName = "ADB Shell",
        description = "ADB-level shell access. Provides elevated permissions beyond a normal app but less than full root.",
    ),
    SHIZUKU(
        displayName = "Shizuku",
        description = "Delegated privileged access via Shizuku service. Provides ADB-level permissions without a PC connection.",
    ),
    BASIC(
        displayName = "Basic",
        description = "Standard app-level access with no privilege escalation. Limited to world-readable system files.",
    ),
}
