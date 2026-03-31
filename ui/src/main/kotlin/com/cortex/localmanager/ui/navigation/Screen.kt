package com.cortex.localmanager.ui.navigation

enum class Screen(val title: String, val label: String, val icon: String) {
    DASHBOARD("Dashboard", "Overview", "\u2302"),
    DETECTIONS("Detections & Alerts", "Alerts", "\u26A0"),
    HUNTING("Threat Hunting", "Hunting", "\u2315"),
    EXCEPTIONS("Exceptions", "SUEX", "\u2696")
}
