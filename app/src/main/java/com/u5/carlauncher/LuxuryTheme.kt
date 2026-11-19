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
        surface = Color(0xFF14161D),
        surfaceElevated = Color(0xFF1D202A),
        accent = Color(0xFFD4AF37),
        accentGlow = Color(0xFFF6E29F),
        accentSecondary = Color(0xFF8C6D1F),
        border = Color(0x44D4AF37),
        textPrimary = Color(0xFFF8F6F0),
        textSecondary = Color(0xFFA8A295)
    ),
    TITANIUM_ICE(
        id = "titanium_ice",
        displayName = "Brushed Titanium & Ice Blue",
        bgTop = Color(0xFF090D12),
        bgBottom = Color(0xFF111924),
        surface = Color(0xFF131B26),
        surfaceElevated = Color(0xFF1C2736),
        accent = Color(0xFF38BDF8),
        accentGlow = Color(0xFFBAE6FD),
        accentSecondary = Color(0xFF0284C7),
        border = Color(0x4438BDF8),
        textPrimary = Color(0xFFF0F9FF),
        textSecondary = Color(0xFF94A3B8)
    ),
    EMERALD_BRASS(
        id = "emerald_brass",
        displayName = "Emerald & Satin Brass",
        bgTop = Color(0xFF070F0C),
        bgBottom = Color(0xFF0E1B16),
        surface = Color(0xFF111F1A),
        surfaceElevated = Color(0xFF192D26),
        accent = Color(0xFF10B981),
        accentGlow = Color(0xFFD4B475),
        accentSecondary = Color(0xFF047857),
        border = Color(0x44C9A86A),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF8FB3A4)
    ),
    CARBON_CRIMSON(
        id = "carbon_crimson",
        displayName = "Carbon & Crimson",
        bgTop = Color(0xFF0C090A),
        bgBottom = Color(0xFF1A0E12),
        surface = Color(0xFF181215),
        surfaceElevated = Color(0xFF241A1F),
        accent = Color(0xFFE11D48),
        accentGlow = Color(0xFFFDA4AF),
        accentSecondary = Color(0xFF9F1239),
        border = Color(0x44E11D48),
        textPrimary = Color(0xFFFFF1F2),
        textSecondary = Color(0xFFB0949A)
    );

    companion object {
        fun fromId(id: String?): LuxuryThemeType =
            entries.firstOrNull { it.id == id } ?: OBSIDIAN_GOLD
    }
}

enum class ButtonFinish(val id: String, val displayName: String) {
    SCULPTED_METAL("metal", "Sculpted Metal"),
    SMOKED_GLASS("glass", "Smoked Glass"),
    AMBIENT_EDGE("ambient", "Ambient Edge");

    companion object {
        fun fromId(id: String?): ButtonFinish =
            entries.firstOrNull { it.id == id } ?: SCULPTED_METAL
    }
}
