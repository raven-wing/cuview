package io.github.raven_wing.cuview.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registers custom lint rules for the CU View project.
 *
 * Discovered automatically by the Android lint tool via the service loader file at
 * `META-INF/services/com.android.tools.lint.client.api.IssueRegistry`.
 */
class GlanceLintRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(GlanceDpDetector.ISSUE)

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "CU View",
        identifier = "io.github.raven_wing.cuview:lint-rules",
    )
}
