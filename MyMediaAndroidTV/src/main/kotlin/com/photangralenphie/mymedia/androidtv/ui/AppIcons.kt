package com.photangralenphie.mymedia.androidtv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Pin = icon("Pin") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 2f); lineTo(17f, 2f); lineTo(17f, 4f); lineTo(16f, 4f)
            lineTo(16f, 12f); lineTo(18f, 14f); lineTo(18f, 16f); lineTo(13f, 16f)
            lineTo(13f, 22f); lineTo(11f, 22f); lineTo(11f, 16f); lineTo(6f, 16f)
            lineTo(6f, 14f); lineTo(8f, 12f); lineTo(8f, 4f); lineTo(7f, 4f); close()
        }
    }
    val Eye = icon("Watched") {
        outline {
            moveTo(2.5f, 12f)
            curveTo(4.8f, 8f, 8.1f, 6f, 12f, 6f)
            curveTo(15.9f, 6f, 19.2f, 8f, 21.5f, 12f)
            curveTo(19.2f, 16f, 15.9f, 18f, 12f, 18f)
            curveTo(8.1f, 18f, 4.8f, 16f, 2.5f, 12f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 9f); curveTo(13.7f, 9f, 15f, 10.3f, 15f, 12f)
            curveTo(15f, 13.7f, 13.7f, 15f, 12f, 15f); curveTo(10.3f, 15f, 9f, 13.7f, 9f, 12f)
            curveTo(9f, 10.3f, 10.3f, 9f, 12f, 9f); close()
        }
    }
    val EyeSlash = icon("Eye slash") {
        outline {
            moveTo(2.5f, 12f); curveTo(4.8f, 8f, 8.1f, 6f, 12f, 6f)
            curveTo(15.9f, 6f, 19.2f, 8f, 21.5f, 12f)
            curveTo(19.2f, 16f, 15.9f, 18f, 12f, 18f)
            curveTo(8.1f, 18f, 4.8f, 16f, 2.5f, 12f); close()
        }
        outline { moveTo(4f, 4f); lineTo(20f, 20f) }
    }
    val MovieClapper = icon("Movie clapper") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f); lineTo(6f, 8f); lineTo(9f, 8f); lineTo(7f, 4f); lineTo(10f, 4f); lineTo(12f, 8f)
            lineTo(15f, 8f); lineTo(13f, 4f); lineTo(16f, 4f); lineTo(18f, 8f); lineTo(21f, 8f); lineTo(19f, 4f)
            lineTo(20f, 4f); curveTo(21.1f, 4f, 22f, 4.9f, 22f, 6f); lineTo(22f, 19f); curveTo(22f, 20.1f, 21.1f, 21f, 20f, 21f)
            lineTo(4f, 21f); curveTo(2.9f, 21f, 2f, 20.1f, 2f, 19f); lineTo(2f, 6f); curveTo(2f, 4.9f, 2.9f, 4f, 4f, 4f); close()
        }
    }
    val CollectionStack = icon("Collections") {
        outline { moveTo(8f, 4f); lineTo(19f, 4f); curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f); lineTo(21f, 15f) }
        outline { moveTo(5f, 7f); lineTo(16f, 7f); curveTo(17.1f, 7f, 18f, 7.9f, 18f, 9f); lineTo(18f, 18f); curveTo(18f, 19.1f, 17.1f, 20f, 16f, 20f); lineTo(5f, 20f); curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f); lineTo(3f, 9f); curveTo(3f, 7.9f, 3.9f, 7f, 5f, 7f) }
        path(fill = SolidColor(Color.Black)) { moveTo(10.5f, 10f); lineTo(11.6f, 12.3f); lineTo(14f, 12.6f); lineTo(12.2f, 14.3f); lineTo(12.7f, 17f); lineTo(10.5f, 15.7f); lineTo(8.3f, 17f); lineTo(8.8f, 14.3f); lineTo(7f, 12.6f); lineTo(9.4f, 12.3f); close() }
    }
    val Tv = icon("TV") {
        outline { moveTo(4f, 6f); lineTo(20f, 6f); curveTo(21.1f, 6f, 22f, 6.9f, 22f, 8f); lineTo(22f, 18f); curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f); lineTo(4f, 20f); curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f); lineTo(2f, 8f); curveTo(2f, 6.9f, 2.9f, 6f, 4f, 6f) }
        outline { moveTo(8f, 2f); lineTo(12f, 6f); lineTo(16f, 2f) }
    }
    val TheaterMasks = icon("Theater masks") {
        outline { moveTo(3f, 5f); curveTo(7f, 3.5f, 10f, 4f, 12f, 5f); lineTo(11f, 13f); curveTo(9f, 17f, 5f, 16f, 4f, 12f); close() }
        outline { moveTo(12f, 8f); curveTo(15f, 6.5f, 18f, 7f, 21f, 8f); lineTo(20f, 16f); curveTo(18f, 20f, 14f, 19f, 12f, 16f) }
        outline { moveTo(6f, 9f); lineTo(7f, 9f); moveTo(9f, 8.5f); lineTo(10f, 8.5f); moveTo(6f, 12f); curveTo(7.5f, 13.5f, 9f, 13.5f, 10f, 12f) }
        outline { moveTo(15f, 12f); lineTo(16f, 12f); moveTo(18f, 11.5f); lineTo(19f, 11.5f); moveTo(15f, 16f); curveTo(16f, 14.8f, 18f, 14.8f, 19f, 16f) }
    }
    val Map = icon("Map") { outline { moveTo(3f, 5f); lineTo(9f, 3f); lineTo(15f, 5f); lineTo(21f, 3f); lineTo(21f, 19f); lineTo(15f, 21f); lineTo(9f, 19f); lineTo(3f, 21f); close(); moveTo(9f, 3f); lineTo(9f, 19f); moveTo(15f, 5f); lineTo(15f, 21f) } }
    val Artwork = icon("Artwork") { outline { moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close(); moveTo(5f, 17f); lineTo(10f, 12f); lineTo(13f, 15f); lineTo(16f, 11f); lineTo(21f, 17f) }; path(fill = SolidColor(Color.Black)) { moveTo(7f, 7f); curveTo(8.1f, 7f, 9f, 7.9f, 9f, 9f); curveTo(9f, 10.1f, 8.1f, 11f, 7f, 11f); curveTo(5.9f, 11f, 5f, 10.1f, 5f, 9f); curveTo(5f, 7.9f, 5.9f, 7f, 7f, 7f); close() } }
    val Wand = icon("Wand") { outline { moveTo(5f, 19f); lineTo(16f, 8f); lineTo(19f, 11f); lineTo(8f, 22f); close(); moveTo(15f, 3f); lineTo(15f, 6f); moveTo(20f, 5f); lineTo(18f, 7f); moveTo(21f, 11f); lineTo(23f, 11f); moveTo(10f, 4f); lineTo(12f, 6f) } }
    val MusicNote = icon("Music") { path(fill = SolidColor(Color.Black)) { moveTo(10f, 4f); lineTo(20f, 2f); lineTo(20f, 15f); curveTo(20f, 17.2f, 18.2f, 19f, 16f, 19f); curveTo(13.8f, 19f, 12f, 17.7f, 12f, 16f); curveTo(12f, 14.3f, 13.8f, 13f, 16f, 13f); curveTo(16.8f, 13f, 17.5f, 13.2f, 18f, 13.5f); lineTo(18f, 7f); lineTo(12f, 8.2f); lineTo(12f, 17f); curveTo(12f, 19.2f, 10.2f, 21f, 8f, 21f); curveTo(5.8f, 21f, 4f, 19.7f, 4f, 18f); curveTo(4f, 16.3f, 5.8f, 15f, 8f, 15f); curveTo(8.8f, 15f, 9.5f, 15.2f, 10f, 15.5f); close() } }
    val Atom = icon("Atom") { outline { moveTo(12f, 2f); curveTo(16f, 2f, 19f, 6.5f, 19f, 12f); curveTo(19f, 17.5f, 16f, 22f, 12f, 22f); curveTo(8f, 22f, 5f, 17.5f, 5f, 12f); curveTo(5f, 6.5f, 8f, 2f, 12f, 2f); close(); moveTo(3.3f, 7f); curveTo(5.3f, 3.5f, 10.6f, 4f, 15.5f, 7f); curveTo(20.4f, 10f, 22.7f, 14.7f, 20.7f, 18f); curveTo(18.7f, 21.5f, 13.4f, 20f, 8.5f, 17f); curveTo(3.6f, 14f, 1.3f, 10.3f, 3.3f, 7f); close() }; path(fill = SolidColor(Color.Black)) { moveTo(12f, 10f); curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f); curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f); curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f); curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f); close() } }
    val Leaf = icon("Leaf") { outline { moveTo(20f, 4f); curveTo(12f, 4f, 5f, 7f, 5f, 14f); curveTo(5f, 18f, 8f, 20f, 11f, 20f); curveTo(18f, 20f, 20f, 12f, 20f, 4f); close(); moveTo(5f, 20f); curveTo(8f, 15f, 12f, 11f, 18f, 7f) } }
    val SportsBall = icon("Sports") { outline { moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f); curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); close(); moveTo(9f, 9f); lineTo(15f, 9f); lineTo(17f, 14f); lineTo(12f, 17f); lineTo(7f, 14f); close() } }
    val Scope = icon("Scope") { outline { moveTo(12f, 3f); lineTo(12f, 7f); moveTo(12f, 17f); lineTo(12f, 21f); moveTo(3f, 12f); lineTo(7f, 12f); moveTo(17f, 12f); lineTo(21f, 12f); moveTo(12f, 7f); curveTo(14.8f, 7f, 17f, 9.2f, 17f, 12f); curveTo(17f, 14.8f, 14.8f, 17f, 12f, 17f); curveTo(9.2f, 17f, 7f, 14.8f, 7f, 12f); curveTo(7f, 9.2f, 9.2f, 7f, 12f, 7f) } }
    val Download = icon("Download") {
        outline {
            moveTo(12f, 3f); lineTo(12f, 15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 19f); lineTo(20f, 19f)
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.outline(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
}
