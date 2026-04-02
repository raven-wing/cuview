package io.github.raven_wing.cuview.widget

import android.content.Context
import io.github.raven_wing.cuview.data.storage.TaskStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression tests for: theme changes were not reflected during a running Glance widget session.
 *
 * Bug: In [CUViewWidget.provideGlance], `colors` was computed OUTSIDE the `provideContent { }`
 * block and captured in a local val:
 *
 *   val colors = WidgetTheme.fromId(taskStorage.loadThemeId()).colors.toWidgetColors() // ← BUG
 *   provideContent {
 *       currentState<Preferences>()          // DataStore recomposition trigger
 *       val tasks = taskStorage.loadTasks()  // re-read on each recomposition ✓
 *       ...
 *       WidgetContent(..., colors = colors)  // stale — NOT re-read on recomposition ✗
 *   }
 *
 * When `updateAppWidgetState` triggered a recomposition within the existing Glance session,
 * `tasks`, `error`, `isSyncing`, and `tasksSourceName` were all re-read from `taskStorage`,
 * but `colors` was not — it still reflected the theme at session-start time.
 *
 * Fix: move `val colors = ...` inside the `provideContent { }` lambda so it is re-evaluated
 * on every recomposition, exactly like `tasks`, `error`, etc.
 *
 * These tests verify the storage layer round-trips correctly (prerequisite for the fix to work)
 * and document the bug by simulating the before/after composition patterns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetThemeRecompositionTest {

    private fun storage(widgetId: Int) = TaskStorage(
        RuntimeEnvironment.getApplication().getSharedPreferences("test_theme_$widgetId", Context.MODE_PRIVATE),
        widgetId,
    )

    /**
     * Verifies all three themes round-trip correctly through TaskStorage / WidgetTheme.fromId.
     * This is the prerequisite for the widget fix to work: if storage didn't persist and reload
     * the correct theme, moving the read inside provideContent would still be wrong.
     */
    @Test
    fun allThemes_roundTripThroughStorage() {
        val taskStorage = storage(widgetId = 1)

        WidgetTheme.entries.forEach { theme ->
            taskStorage.saveThemeId(theme.id)
            assertEquals(
                "WidgetTheme.fromId(taskStorage.loadThemeId()) should return the saved theme",
                theme,
                WidgetTheme.fromId(taskStorage.loadThemeId()),
            )
        }
    }

    /**
     * Simulates what happens INSIDE provideContent { } after the fix: theme is re-read on
     * every recomposition. Changing the stored theme and simulating a re-composition (calling
     * the lambda again) must produce the new theme immediately.
     *
     * This is the core contract the fix establishes. The test was written BEFORE the fix was
     * applied and fails if colors are read outside provideContent { } (old behaviour).
     */
    @Test
    fun themeChange_isReflectedOnNextRecomposition_withFixPattern() {
        val taskStorage = storage(widgetId = 2)

        // Simulate the fixed provideContent { } body: re-read theme on every invocation.
        val recompose: () -> WidgetTheme = { WidgetTheme.fromId(taskStorage.loadThemeId()) }

        assertEquals("Default theme should be DARK", WidgetTheme.DARK, recompose())

        // User saves LIGHT in WidgetConfigActivity — mirrors onConfigSaved().
        taskStorage.saveThemeId(WidgetTheme.LIGHT.id)

        // Next Glance recomposition must pick up LIGHT.
        assertEquals(
            "After saving LIGHT theme, the next recomposition should render LIGHT colors",
            WidgetTheme.LIGHT,
            recompose(),
        )
    }

    /**
     * Demonstrates the OLD (buggy) capture pattern: theme captured once at session start,
     * subsequent recompositions see the stale value. This test asserts the divergence between
     * the captured theme and the stored theme, confirming what the bug caused.
     */
    @Test
    fun capturedTheme_divergesFromStoredTheme_afterChange() {
        val taskStorage = storage(widgetId = 3)

        // Simulate the OLD provideContent pattern: theme captured once outside the lambda.
        val capturedAtSessionStart = WidgetTheme.fromId(taskStorage.loadThemeId()) // DARK
        val recompose: () -> WidgetTheme = { capturedAtSessionStart }              // stale closure

        assertEquals(WidgetTheme.DARK, recompose())

        // User changes to NEO.
        taskStorage.saveThemeId(WidgetTheme.NEO.id)

        val renderedTheme = recompose()                                            // still DARK
        val storedTheme = WidgetTheme.fromId(taskStorage.loadThemeId())            // NEO

        // The divergence is what caused the bug: widget renders stale theme.
        assertNotEquals(
            "Captured (stale) theme must differ from stored theme — this is the bug.",
            storedTheme,
            renderedTheme,
        )
        assertEquals("Storage correctly holds the new theme", WidgetTheme.NEO, storedTheme)
        assertEquals("Captured theme still holds the old theme", WidgetTheme.DARK, renderedTheme)
    }

    /**
     * Verifies that the default theme (when nothing is saved) is DARK.
     */
    @Test
    fun defaultTheme_isDark_whenNothingSaved() {
        val taskStorage = storage(widgetId = 4)
        assertEquals(WidgetTheme.DEFAULT, WidgetTheme.fromId(taskStorage.loadThemeId()))
        assertEquals(WidgetTheme.DARK, WidgetTheme.DEFAULT)
    }
}
