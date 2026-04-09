package com.cloudorz.openmonitor.data.repository

interface ThemeSettingsRepository {
    var uiMode: String
    var colorMode: Int
    var miuixMonet: Boolean
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var enableBlur: Boolean
    var enableFloatingBottomBar: Boolean
    var pageScale: Float
}
