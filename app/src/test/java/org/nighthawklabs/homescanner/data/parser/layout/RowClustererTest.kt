package org.nighthawklabs.homescanner.data.parser.layout

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RowClustererTest {

    private fun line(text: String, top: Float, bottom: Float, left: Float, right: Float): OcrLine {
        val box = RectF(left, top, right, bottom)
        val token = OcrToken(text, box, null)
        return OcrLine(text, box, listOf(token))
    }

    @Test
    fun clusterRows_groupsOverlappingLines() {
        val lines = listOf(
            line("Milk", 10f, 30f, 0f, 50f),
            line("2.99", 12f, 28f, 200f, 250f),
            line("Bread", 50f, 70f, 0f, 50f)
        )
        val rows = RowClusterer.clusterRows(lines, 1000)
        assertEquals(2, rows.size)
        assertEquals("Milk 2.99", rows[0].text)
        assertEquals("Bread", rows[1].text)
    }

    @Test
    fun clusterRows_separatesDistantLines() {
        val lines = listOf(
            line("Line1", 10f, 30f, 0f, 50f),
            line("Line2", 100f, 120f, 0f, 50f)
        )
        val rows = RowClusterer.clusterRows(lines, 500)
        assertEquals(2, rows.size)
    }
}
