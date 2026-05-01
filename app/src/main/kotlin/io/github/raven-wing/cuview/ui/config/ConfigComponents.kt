package io.github.raven_wing.cuview.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.raven_wing.cuview.widget.WidgetTheme

internal fun buildBreadcrumb(vararg parts: String): String = parts.joinToString(" › ")

@Composable
internal fun BreadcrumbBar(parts: List<String>, onCrumbClick: List<() -> Unit>) {
    require(parts.isNotEmpty()) { "BreadcrumbBar needs at least 1 part" }
    require(onCrumbClick.size == parts.size) {
        "onCrumbClick must have ${parts.size} entries for ${parts.size} parts, got ${onCrumbClick.size}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onCrumbClick[0]),
        )
        parts.forEachIndexed { i, part ->
            Text(" › ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i < parts.size - 1) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onCrumbClick[i]),
                )
            } else {
                Text(text = part, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun BrowseItem(
    text: String,
    selected: Boolean,
    drillDown: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (drillDown) "›" else if (selected) "✓" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected && !drillDown) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LoadingRow() {
    Spacer(Modifier.height(12.dp))
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
}

// ── Theme picker ──────────────────────────────────────────────────────────────

@Composable
internal fun ThemePickerSection(selectedTheme: WidgetTheme, onThemeChange: (WidgetTheme) -> Unit) {
    SectionLabel("Widget theme")
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetTheme.entries.forEach { theme ->
            FilterChip(
                selected = theme == selectedTheme,
                onClick = { onThemeChange(theme) },
                label = { Text(theme.displayName) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(theme.colors.bg)
                            .border(1.5.dp, theme.colors.accent, CircleShape),
                    )
                },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    WidgetMiniPreview(selectedTheme)
}

@Composable
private fun WidgetMiniPreview(theme: WidgetTheme) {
    val c = theme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(c.bg)
            .padding(10.dp),
    ) {
        Column {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CU", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(" View", color = c.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.tileBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("↻", color = c.accent, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Two fake task tiles mirroring the widget's accent-stripe layout
            repeat(2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(3.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(c.tileBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (it == 0) "Fix login redirect bug" else "Update onboarding copy",
                            color = c.text,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("›", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
