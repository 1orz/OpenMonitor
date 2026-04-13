package com.cloudorz.openmonitor.core.model.identity

enum class ActivationPlan(val code: Int) {
    NONE(0),
    BASIC(1),
    PRO(2),
    ULTIMATE(3);

    companion object {
        fun fromCode(code: Int): ActivationPlan =
            entries.find { it.code == code } ?: NONE
    }
}
