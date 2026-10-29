package io.github.charlesforsyth.fieldjournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin line icons (24-unit grid, 1.4 stroke), drawn here so no icon font or extra dependency is needed. */
object LineIcons {
    /**
     * Engraved instrument icon: primary outline at [main] weight plus fine "engraving" detail at [fine] weight and lower
     * alpha, like plate-engraved line art. All four nav icons use the same two weights.
     */
    private fun engraved(name: String, main: List<String>, fine: List<String>, mainW: Float = 1.35f, fineW: Float = 0.85f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        main.forEach { b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = mainW, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        fine.forEach { b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeAlpha = 0.85f, strokeLineWidth = fineW, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        return b.build()
    }

    private fun icon(name: String, vararg paths: String, stroke: Float = 1.4f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }

    /** Lens (nav): objective lens ring with a reticle - outer bezel, inner glass, cross-hair ticks and a centre dot. */
    val lens = engraved("lens",
        main = listOf("M12 3.5a8.5 8.5 0 1 0 0.01 0z", "M12 7.2a4.8 4.8 0 1 0 0.01 0z"),
        fine = listOf("M12 1.8v3.2", "M12 19v3.2", "M1.8 12h3.2", "M19 12h3.2", "M12 9.6v1.2", "M12 13.2v1.2", "M9.6 12h1.2", "M13.2 12h1.2",
            "M12 11.6a0.4 0.4 0 1 0 0.01 0z"))
    /** Map (nav): folded three-panel survey sheet with a contour loop and a spot height. */
    val map = engraved("map",
        main = listOf("M3 6.2l5.8-2.2 6.4 2.2 5.8-2.2v13.8l-5.8 2.2-6.4-2.2-5.8 2.2z"),
        fine = listOf("M8.8 4v13.8", "M15.2 6.2V20", "M5.4 13.2c1.2-2.8 3.6-3.6 5.8-2.6s3.4 3.4 6.2 1.6",
            "M6.6 15.6c1.4-1.6 3-1.8 4.4-1.2s2.8 1.6 4.6 0.6", "M17.6 8.6h0.01"))
    /** Journal (nav): bound field notebook - stitched spine, elastic band and ruled lines. */
    val journal = engraved("journal",
        main = listOf("M6.5 3.2h11a1.5 1.5 0 0 1 1.5 1.5v14.6a1.5 1.5 0 0 1-1.5 1.5h-11z", "M6.5 3.2a1.8 1.8 0 0 0-1.8 1.8v14a1.8 1.8 0 0 0 1.8 1.8"),
        fine = listOf("M9 6.2v1.4", "M9 9.6v1.4", "M9 13v1.4", "M9 16.4v1.4", "M11 7.4h5.2", "M11 10h5.2", "M11 12.6h3.6", "M16.4 3.2v17.6"))
    /** Settings (nav): drafting dividers / calipers - hinge, two legs and a graduated arc. */
    val settings = engraved("settings",
        main = listOf("M12 4.6a1.6 1.6 0 1 0 0.01 0z", "M11 7.6L6.4 20.4", "M13 7.6l4.6 12.8"),
        fine = listOf("M12 2v1", "M7.4 15.2a7.8 7.8 0 0 0 9.2 0", "M8.6 16l-0.4 0.9", "M12 16.9v1", "M15.4 16l0.4 0.9"))
    val mic = icon("mic", "M12 3.5a2.5 2.5 0 0 1 2.5 2.5v5.5a2.5 2.5 0 0 1-5 0V6A2.5 2.5 0 0 1 12 3.5z", "M6.5 11a5.5 5.5 0 0 0 11 0", "M12 16.5v4", "M9 20.5h6")
    val search = icon("search", "M10.5 4.5a6 6 0 1 0 0.01 0z", "M15 15l5 5")
    val more = icon("more", "M5.5 12h0.01", "M12 12h0.01", "M18.5 12h0.01", stroke = 2.4f)
    val close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val export = icon("export", "M12 14.5V3.5", "M8 7.5l4-4 4 4", "M5 11.5v8h14v-8")
    val locate = icon("locate", "M12 6.5a5.5 5.5 0 1 0 0.01 0z", "M12 2.5v3", "M12 18.5v3", "M2.5 12h3", "M18.5 12h3")
    val home = icon("home", "M4 11l8-6.5 8 6.5", "M6.5 9.5v10h11v-10", "M10.5 19.5v-5h3v5")
    val flag = icon("flag", "M6 21V4", "M6 4.5h11l-2.5 3.5 2.5 3.5H6")
    val pin = icon("pin", "M12 21s-6-5.6-6-10.5a6 6 0 0 1 12 0C18 15.4 12 21 12 21z", "M12 8.5a2 2 0 1 0 0.01 0z")
    val trash = icon("trash", "M5 7h14", "M9.5 7V4.5h5V7", "M7 7l1 13h8l1-13")
    val layers = icon("layers", "M12 4l8.5 4.5L12 13 3.5 8.5z", "M3.5 12.5L12 17l8.5-4.5", "M3.5 16.5L12 21l8.5-4.5")
    val chevronDown = icon("chev", "M7 10l5 5 5-5")
    val chevronRight = icon("chevr", "M10 7l5 5-5 5")
    val record = icon("record", "M12 7a5 5 0 1 0 0.01 0z")
    val stop = icon("stop", "M7.5 7.5h9v9h-9z")
    val ray = icon("ray", "M4 20L20 4", "M14 4h6v6")
    val ghost = icon("ghost", "M3.5 7.5h11v9h-11z", "M9.5 4.5h11v9", stroke = 1.3f)
    val tag = icon("tag", "M3.5 12.5V4.5h8l9 9-8 8z", "M8 8h0.01", stroke = 1.4f)
}
