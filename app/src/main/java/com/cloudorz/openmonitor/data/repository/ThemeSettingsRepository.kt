package com.cloudorz.openmonitor.data.repository

interface ThemeSettingsRepository {
    var colorMode: Int
    var keyColor: Int
    var colorStyle: String
    var colorSpec: String
    var pageScale: Float
}
