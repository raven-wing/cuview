package io.github.raven_wing.cuview.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import java.util.EnumSet

/**
 * Flags raw integer literals passed to Glance layout dimension functions.
 *
 * In Glance, `padding(8)` / `height(4)` treat the Int as an Android resource ID,
 * not a dp value — causing a Resources$NotFoundException at runtime.
 * The fix is always `padding(8.dp)` / `height(4.dp)`.
 */
class GlanceDpDetector : Detector(), Detector.UastScanner {

    // No UAST visitor needed — detection is text-based in beforeCheckFile.
    override fun getApplicableUastTypes(): List<Class<out UElement>> = emptyList()

    override fun beforeCheckFile(context: Context) {
        if (context.file.extension != "kt") return

        val source = context.getContents()?.toString() ?: return
        if (!source.contains("androidx.glance")) return

        CALL_PATTERN.findAll(source).forEach { match ->
            val functionName = match.groupValues[1]
            val firstInt = match.groupValues[2]

            // Report the first (or only) raw int argument
            val firstIntStart = match.range.first +
                match.value.indexOf(firstInt, functionName.length + 1)
            report(context, source, functionName, firstInt, firstIntStart)

            // Scan for additional raw int named args within the same call
            // e.g. padding(horizontal = 8.dp, vertical = 4) — catch the second arg too.
            // Use matchingCloseParen() rather than indexOf(')') to correctly handle nested
            // function calls like padding(horizontal = 8, top = foo(), bottom = 4).
            val argsStart = match.range.last + 1
            val openParenIdx = match.range.first + match.value.indexOf('(')
            val closeParenIndex = matchingCloseParen(source, openParenIdx + 1)
            if (closeParenIndex > argsStart) {
                EXTRA_NAMED_ARG_PATTERN.findAll(source, argsStart).forEach { extra ->
                    if (extra.range.first >= closeParenIndex) return@forEach
                    val intValue = extra.groupValues[1]
                    val intStart = extra.range.first + extra.value.lastIndexOf(intValue)
                    report(context, source, functionName, intValue, intStart)
                }
            }
        }
    }

    /**
     * Returns the index of the `)` that matches the opening `(` whose content starts at
     * [afterOpenParen]. Correctly handles nested calls. Returns -1 if unbalanced.
     */
    private fun matchingCloseParen(source: String, afterOpenParen: Int): Int {
        var depth = 1
        var i = afterOpenParen
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun report(context: Context, source: String, functionName: String, intValue: String, intStart: Int) {
        val location = Location.create(context.file, source, intStart, intStart + intValue.length)
        context.report(
            ISSUE,
            location,
            "Raw integer $intValue passed to Glance $functionName() — " +
                "Glance treats Int arguments as resource IDs, not dp values. " +
                "Use $intValue.dp instead.",
        )
    }

    companion object {
        /**
         * Matches: `.padding(8)`, `.height(4)`, `.cornerRadius(20)`, `.padding(vertical = 3)` etc.
         * Handles optional whitespace inside parens (lint's WHITESPACE test mode).
         * Group 1 = function name, Group 2 = the raw integer.
         * Negative lookahead `(?!\s*\.\s*dp)` ensures we don't flag `8.dp`.
         */
        private val CALL_PATTERN = Regex(
            """\.(?<fn>padding|height|width|size|absolutePadding|cornerRadius)\s*\(\s*(?:[a-zA-Z]+\s*=\s*)?\s*(?<int>\d+)\s*(?!\s*\.\s*dp)\b""",
        )

        /**
         * Matches subsequent named args after the first: `, name = 42` (no `.dp` following).
         * Used to catch multi-arg calls like `padding(horizontal = 8, vertical = 4)`.
         * Group 1 = the raw integer value.
         */
        private val EXTRA_NAMED_ARG_PATTERN = Regex(
            """,\s*[a-zA-Z]+\s*=\s*(\d+)(?!\s*\.\s*dp)\b""",
        )

        val ISSUE: Issue = Issue.create(
            id = "GlanceRawIntDimension",
            briefDescription = "Raw integer passed to Glance dimension function",
            explanation = """
                Glance layout functions like `padding()` and `height()` have overloads that \
                accept an `@DimenRes Int` (a resource ID). Passing a raw integer literal such \
                as `padding(8)` compiles fine but crashes at runtime with \
                `Resources\${'$'}NotFoundException` because `8` is not a valid resource ID.

                Always use `Dp` values: `padding(8.dp)`, `height(4.dp)`, etc.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                GlanceDpDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE),
            ),
        )
    }
}
