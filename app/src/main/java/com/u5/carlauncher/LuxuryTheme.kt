package com.u5.carlauncher

import androidx.compose.ui.graphics.Color

enum class LuxuryThemeType(
    val id: String,
    val displayName: String,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val accentGlow: Color,
    val accentSecondary: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color
) {
    OBSIDIAN_GOLD(
        id = "obsidian_gold",
        displayName = "Obsidian & Champagne Gold",
        bgTop = Color(0xFF0A0B0E),
        bgBottom = Color(0xFF14120F),
        surface = Color(0xFF16181F),
        surfaceElevated = Color(0xFF20232D),
        accent = Color(0xFFD4AF37),
        accentGlow = Color(0xFFF6E29F),
        accentSecondary = Color(0xFF8C6D1F),
        border = Color(0x44D4AF37),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3ADA0)
    ),
    TITANIUM_ICE(
        id = "titanium_ice",
        displayName = "Brushed Titanium & Ice Blue",
        bgTop = Color(0xFF090D12),
        bgBottom = Color(0xFF111924),
        surface = Color(0xFF141C27),
        surfaceElevated = Color(0xFF1D2838),
        accent = Color(0xFF38BDF8),
        accentGlow = Color(0xFFBAE6FD),
        accentSecondary = Color(0xFF0284C7),
        border = Color(0x4438BDF8),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9FB0C5)
    ),
    EMERALD_BRASS(
        id = "emerald_brass",
        displayName = "Emerald & Satin Brass",
        bgTop = Color(0xFF070F0C),
        bgBottom = Color(0xFF0E1B16),
        surface = Color(0xFF12211B),
        surfaceElevated = Color(0xFF1A2F27),
        accent = Color(0xFF10B981),
        accentGlow = Color(0xFFD4B475),
        accentSecondary = Color(0xFF047857),
        border = Color(0x44C9A86A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF97BAA9)
    ),
    CARBON_CRIMSON(
        id = "carbon_crimson",
        displayName = "Carbon & Crimson",
        bgTop = Color(0xFF0C090A),
        bgBottom = Color(0xFF1A0E12),
        surface = Color(0xFF1A1316),
        surfaceElevated = Color(0xFF261C21),
        accent = Color(0xFFFF2D55),
        accentGlow = Color(0xFFFF8A9E),
        accentSecondary = Color(0xFF9F1239),
        border = Color(0x44FF2D55),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFBCA1A7)
    );

    companion object {
        fun fromId(id: String?): LuxuryThemeType =
            entries.firstOrNull { it.id == id } ?: TITANIUM_ICE
    }
}

enum class ButtonFinish(val id: String, val displayName: String) {
    SCULPTED_METAL("metal", "Sculpted Metal"),
    SMOKED_GLASS("glass", "Smoked Glass"),
    AMBIENT_EDGE("ambient", "Ambient Edge");

    companion object {
        fun fromId(id: String?): ButtonFinish =
            entries.firstOrNull { it.id == id } ?: SMOKED_GLASS
    }
}
