package io.github.raven_wing.cuview.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

// LintDetectorTest extends JUnit 3 TestCase: methods must be named testXxx (no @Test).
class GlanceDpDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = GlanceDpDetector()
    override fun getIssues(): MutableList<Issue> = mutableListOf(GlanceDpDetector.ISSUE)

    fun testRawIntegerInPaddingTriggersError() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.padding

                fun test() {
                    GlanceModifier.padding(8)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:5: Error: Raw integer 8 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 8.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(8)
                                           ~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testRawIntegerInHeightTriggersError() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.height

                fun test() {
                    GlanceModifier.height(4)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:5: Error: Raw integer 4 passed to Glance height() — Glance treats Int arguments as resource IDs, not dp values. Use 4.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.height(4)
                                          ~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testDpValueInPaddingIsClean() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.padding
                import androidx.compose.ui.unit.dp

                fun test() {
                    GlanceModifier.padding(8.dp)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun testRawIntegerInNonGlanceFileIsIgnored() {
        lint().files(
            kotlin(
                """
                import androidx.compose.ui.Modifier
                import androidx.compose.foundation.layout.padding

                fun test() {
                    Modifier.padding(8)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun testCornerRadiusRawIntegerTriggersError() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.appwidget.cornerRadius

                fun test() {
                    GlanceModifier.cornerRadius(20)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:5: Error: Raw integer 20 passed to Glance cornerRadius() — Glance treats Int arguments as resource IDs, not dp values. Use 20.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.cornerRadius(20)
                                                ~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testMultipleNamedArgsWithRawIntegersTriggersTwoErrors() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.padding

                fun test() {
                    GlanceModifier.padding(horizontal = 8, vertical = 4)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:5: Error: Raw integer 4 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 4.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(horizontal = 8, vertical = 4)
                                                                      ~
                src/test.kt:5: Error: Raw integer 8 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 8.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(horizontal = 8, vertical = 4)
                                                        ~
                2 errors, 0 warnings
                """.trimIndent(),
            )
    }

    // Regression test: closeParenIndex previously used source.indexOf(')') which found the first
    // ')' after the match end, not the matching paren of the outer call. When an intermediate
    // argument was a nested function call like helper(), its closing ')' was used as the scan
    // boundary, causing any raw int args that followed to be silently missed.
    fun testRawIntAfterNestedCallInMiddleArgIsDetected() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.padding

                fun helper(): Int = 0

                fun test() {
                    GlanceModifier.padding(horizontal = 8, top = helper(), bottom = 4)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:7: Error: Raw integer 4 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 4.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(horizontal = 8, top = helper(), bottom = 4)
                                                                                    ~
                src/test.kt:7: Error: Raw integer 8 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 8.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(horizontal = 8, top = helper(), bottom = 4)
                                                        ~
                2 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun testNamedVerticalArgumentWithRawIntegerTriggersError() {
        lint().files(
            kotlin(
                """
                import androidx.glance.GlanceModifier
                import androidx.glance.layout.padding

                fun test() {
                    GlanceModifier.padding(vertical = 3)
                }
                """,
            ).indented(),
        )
            .allowMissingSdk()
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                """
                src/test.kt:5: Error: Raw integer 3 passed to Glance padding() — Glance treats Int arguments as resource IDs, not dp values. Use 3.dp instead. [GlanceRawIntDimension]
                    GlanceModifier.padding(vertical = 3)
                                                      ~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }
}
