package com.cyberrin.giswrap.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelSizeTest {
    private fun aspectOf(size: Pair<Int, Int>) = size.first.toFloat() / size.second

    @Test
    fun `the bitmap takes the cell's own shape`() {
        listOf(0.5f, 1f, 1.5f, 2f).forEach { wanted ->

            assertEquals(wanted, aspectOf(panelPixelSize(wanted)), 0.01f,
                "a $wanted cell should not need stretching")
        }
    }

    @Test
    fun `a tall cell is taller than it is wide`() {
        val (w, h) = panelPixelSize(0.5f)
        assertTrue(h > w, "a 1:2 cell produced ${w}x$h")
    }

    @Test
    fun `neither side runs away on an extreme cell`() {
        listOf(10f, 0.1f).forEach { extreme ->
            val (w, h) = panelPixelSize(extreme)
            assertTrue(maxOf(w, h) <= 5 * minOf(w, h) + 1, "$extreme gave ${w}x$h")
        }
    }

    @Test
    fun `the shapes the provider allows are drawn as asked`() {
        listOf(5f / 3f, 1f / 3f, 5f).forEach { allowed ->
            assertEquals(allowed, aspectOf(panelPixelSize(allowed)), 0.02f,
                "a $allowed cell was clamped when it should not have been")
        }
    }

    @Test
    fun `a size the launcher has not decided yet falls back to square`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { junk ->
            val (w, h) = panelPixelSize(junk)
            assertEquals(w, h, "$junk should give a square")
        }
    }
}

class SideBySideTest {
    @Test
    fun `a two-by-one goes side by side`() {
        assertTrue(isSideBySide(2f), "a 2:1 cell should place icon and text side by side")
    }

    @Test
    fun `a square or taller cell stacks`() {
        listOf(1f, 0.5f).forEach { aspect ->
            assertTrue(!isSideBySide(aspect), "$aspect should stack")
        }
    }

    @Test
    fun `a size the launcher has not decided yet stacks`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY).forEach { junk ->
            assertTrue(!isSideBySide(junk), "$junk should stack")
        }
    }
}

class PanelInsetTest {
    private val small = 110f
    private val large = 180f

    @Test
    fun `the outline fits inside the bitmap`() {
        val (w, h) = panelPixelSize(1f)
        val short = minOf(w, h)
        val inset = panelInset(short, small, border = true)

        assertTrue(inset >= strokeWidth(short, small) / 2f, "an inset of $inset is too small")
        assertTrue(inset > 0f, "a bordered panel needs room for its line")
    }

    @Test
    fun `an unbordered panel reaches the edge`() {
        val (w, h) = panelPixelSize(1f)

        assertEquals(0f, panelInset(minOf(w, h), small, border = false))
    }

    private fun onScreenDp(aspect: Float, cellShortDp: Float): Float {
        val (w, h) = panelPixelSize(aspect)
        val short = minOf(w, h)
        return strokeWidth(short, cellShortDp) * cellShortDp / short
    }

    @Test
    fun `the border is the same weight at every size`() {
        val smallest = onScreenDp(1f, small)
        listOf(1f to large, 2f to small, 5f to small, 0.5f to small).forEach { (aspect, cell) ->
            assertEquals(smallest, onScreenDp(aspect, cell), 0.05f,
                "a ${aspect}:1 widget on a ${cell}dp side drew a different weight")
        }
    }

    @Test
    fun `a cell the launcher has not measured yet still gets a line`() {
        assertTrue(strokeWidth(320, 0f) > 0f)
    }
}

class PanelBudgetTest {
    @Test
    fun `no shape asks for more than a megabyte`() {
        listOf(0.2f, 0.5f, 1f, 2f, 5f, 50f).forEach { aspect ->
            val (w, h) = panelPixelSize(aspect)
            val bytes = w.toLong() * h * 4
            assertTrue(bytes < 1_000_000, "a $aspect widget asked for $bytes bytes (${w}x$h)")
        }
    }

    @Test
    fun `a square panel still gets its full resolution`() {
        val (w, h) = panelPixelSize(1f)
        assertTrue(minOf(w, h) >= 320, "a square panel came out at ${w}x$h")
    }
}

class StackedFaceTest {
    private val reading = 30f

    @Test
    fun `the drawing takes four sevenths of what is left`() {
        val (icon, gap) = stackedFace(heightDp = 170f, readingDp = reading)
        assertEquals(80f, icon, 0.5f)
        assertEquals(20f, gap, 0.5f)
    }

    @Test
    fun `the drawing and the three gaps account for the whole height`() {
        listOf(110f, 170f, 250f).forEach { height ->
            val (icon, gap) = stackedFace(height, reading)
            assertEquals(height, icon + 3 * gap + reading, 0.5f,
                "a ${height}dp face did not add up")
        }
    }

    @Test
    fun `the drawing is the largest thing on the face`() {
        val (icon, gap) = stackedFace(170f, reading)
        assertTrue(icon > reading, "the drawing came out smaller than the reading")
        assertTrue(icon > gap * 3, "the gaps took more room than the drawing")
    }

    @Test
    fun `a face too short for its own reading asks for nothing`() {
        val (icon, gap) = stackedFace(heightDp = 10f, readingDp = reading)
        assertEquals(0f, icon)
        assertEquals(0f, gap)
    }
}

class IconRenderSizeTest {
    private val thinnest = 0.32f
    private val fullest = 0.65f

    @Test
    fun `the ink comes out the size that was asked for`() {
        listOf(120, 240, 380).forEach { target ->
            listOf(thinnest, fullest).forEach { fraction ->
                val ink = iconRenderSize(target, fraction) * fraction
                assertEquals(target.toFloat(), ink, target * 0.02f,
                    "a ${target}px icon at $fraction ink came out ${ink.toInt()}px")
            }
        }
    }

    @Test
    fun `the rasterised box stays inside its budget`() {
        listOf(0, 100, 1000, 10_000).forEach { target ->
            listOf(thinnest, fullest).forEach { fraction ->
                val box = iconRenderSize(target, fraction)
                assertTrue(box in 128..1536, "a ${target}px icon asked for a ${box}px box")
            }
        }
    }

    @Test
    fun `a drawing the probe could not measure still gets a box`() {
        listOf(0f, -1f, Float.NaN).forEach { junk ->
            assertTrue(iconRenderSize(240, junk) >= 128, "$junk gave no box")
        }
    }
}
