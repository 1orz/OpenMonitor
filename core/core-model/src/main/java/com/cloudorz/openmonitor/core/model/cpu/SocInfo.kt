package com.cloudorz.openmonitor.core.model.cpu

data class SocInfo(
    val vendor: String = "",
    val name: String = "",
    val fab: String = "",
    val cpuDescription: String = "",
    val memoryType: String = "",
    val bandwidth: String = "",
    val channels: String = "",
    val hardwareId: String = "",
    val abi: String = "",
    val architecture: String = "",
) {
    val hasData: Boolean
        get() = name.isNotBlank()
}
