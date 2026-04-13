package com.cloudorz.openmonitor.core.model.identity

/**
 * 本地缓存的激活状态。
 *
 * token 是服务端签发的 Ed25519 签名令牌 (Base64)，
 * 每次读取时用公钥重新验签，防篡改。
 */
data class ActivationState(
    val activated: Boolean = false,
    val plan: ActivationPlan = ActivationPlan.NONE,
    val activatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val token: String = "",
)
