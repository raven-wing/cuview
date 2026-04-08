package io.github.raven_wing.cuview.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * Raw color palette for a widget theme.
 *
 * Values are defined once as [Color] and used in two ways:
 * - Directly (as regular Compose [Color]) in the config screen theme picker / mini-preview.
 * - Via [toWidgetColors] (as Glance [ColorProvider]) in the widget composables.
 *
 * This is the single source of truth: adding a new theme means adding one [WidgetTheme]
 * entry with a [ThemeColors] instance — no other file needs updating.
 */
data class ThemeColors(
    val bg: Color,
    val title: Color,
    val text: Color,
    val textSecondary: Color = Color(0xFF6B7280),
    val error: Color,
    val tileBg: Color,
    val tileBgPressed: Color,
    val accent: Color,
) {
    fun toWidgetColors() = WidgetColors(
        background = ColorProvider(bg),
        title = ColorProvider(title),
        text = ColorProvider(text),
        textSecondary = ColorProvider(textSecondary),
        error = ColorProvider(error),
        tileBg = ColorProvider(tileBg),
        tileBgPressed = ColorProvider(tileBgPressed),
        accent = ColorProvider(accent),
    )
}

/** Glance [ColorProvider] wrappers — used exclusively inside [CUViewWidget] composables. */
data class WidgetColors(
    val background: ColorProvider,
    val title: ColorProvider,
    val text: ColorProvider,
    val textSecondary: ColorProvider,
    val error: ColorProvider,
    val tileBg: ColorProvider,
    val tileBgPressed: ColorProvider,
    val accent: ColorProvider,
)

enum class WidgetTheme(val id: String, val displayName: String, val colors: ThemeColors) {

    DARK(
        id = "dark",
        displayName = "Dark",
        colors = ThemeColors(
            bg           = Color(0xFF12122A),
            title        = Color(0xFFFFFFFF),
            text         = Color(0xFFE2E2F0),
            error        = Color(0xFFF87171),
            tileBg       = Color(0xFF1C1C2E),
            tileBgPressed = Color(0xFF28283E),
            accent       = Color(0xFF818CF8),
        ),
    ),

    LIGHT(
        id = "light",
        displayName = "Light",
        colors = ThemeColors(
            bg           = Color(0xFFF3F4FF),
            title        = Color(0xFF1E1E50),
            text         = Color(0xFF2D2D5A),
            error        = Color(0xFFDC2626),
            tileBg       = Color(0xFFEAEAFF),
            tileBgPressed = Color(0xFFD8D8F8),
            accent       = Color(0xFF4F46E5),
        ),
    ),

    NEO(
        id = "neo",
        displayName = "Neo",
        colors = ThemeColors(
            bg            = Color(0xFFFFFFFF),
            title         = Color(0xFF000000),
            text          = Color(0xFF000000),
            textSecondary = Color(0xFF333333),
            error         = Color(0xFFFF0000),
            tileBg        = Color(0xFFFFE600),
            tileBgPressed = Color(0xFFF5D800),
            accent        = Color(0xFF000000),
        ),
    );

    companion object {
        val DEFAULT = DARK
        fun fromId(id: String?): WidgetTheme = entries.find { it.id == id } ?: DEFAULT
    }
}
