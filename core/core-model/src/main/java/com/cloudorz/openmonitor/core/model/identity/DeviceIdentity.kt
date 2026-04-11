package com.cloudorz.openmonitor.core.model.identity

/**
 * 服务端返回的设备身份信息。
 * uuid 是后端分配的唯一标识，后续所有捐助行为都关联此 UUID。
 */
data class DeviceIdentity(
    val uuid: String,
    val isNew: Boolean = false,
    val createdAt: Long = 0L,
    val lastIdentifiedAt: Long = System.currentTimeMillis(),
)
