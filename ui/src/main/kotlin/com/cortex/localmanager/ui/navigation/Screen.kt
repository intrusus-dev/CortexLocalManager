package com.cortex.localmanager.ui.navigation

enum class Screen(val title: String, val label: String, val icon: String) {
    DASHBOARD("Dashboard", "Overview", "\u25CB"),           // ○
    DETECTIONS("Detections & Alerts", "Alerts", "\u25B3"),  // △
    QUARANTINE("Quarantine", "Quarantine", "\u2610"),       // ☐
    HUNTING("Threat Hunting", "Hunting", "\u2299"),         // ⊙
    SCAN("Scan Operations", "Scan", "\u25CE"),              // ◎
    FORENSICS("Forensic Collection", "Forensics", "\u2691"), // ⚑
    AGENT_CONTROL("Agent Control", "Agent", "\u2638"),       // ☸
    INVENTORY("Endpoint Inventory", "Inventory", "\u2261"), // ≡
    EXCEPTIONS("Exceptions", "SUEX", "\u2699")              // ⚙
}
