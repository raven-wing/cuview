package io.github.raven_wing.cuview.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbTest {

    @Test
    fun twoParts_joinedWithSeparator() {
        assertEquals("Space › View", buildBreadcrumb("Space", "View"))
    }

    @Test
    fun threeParts_joinedWithSeparator() {
        assertEquals("Space › Folder › View", buildBreadcrumb("Space", "Folder", "View"))
    }

    @Test
    fun singlePart_noSeparator() {
        assertEquals("Space", buildBreadcrumb("Space"))
    }
}
