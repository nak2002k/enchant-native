package org.enchant.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Enchant Icon Family — Lucide geometry (ISC license), 24x24 grid,
 * 2dp strokes with round caps/joins, tinted at use site.
 */
object EnchantIcons {

    private val cache = mutableMapOf<String, ImageVector>()

    private fun icon(name: String, paths: List<String>): ImageVector =
        cache.getOrPut(name) {
            val b = ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            paths.forEach { d ->
                b.path(
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineMiter = 4f,
                    pathFillType = PathFillType.NonZero,
                ) {
                    tracePath(this, d)
                }
            }
            b.build()
        }

    /** Filled (solid) icon variant for Signal's filled symbols (save, payment, star, etc). */
    private fun filledIcon(name: String, paths: List<String>): ImageVector =
        cache.getOrPut(name) {
            val b = ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            paths.forEach { d ->
                b.path(
                    fill = SolidColor(Color.White),
                    stroke = null,
                    pathFillType = PathFillType.NonZero,
                ) {
                    tracePath(this, d)
                }
            }
            b.build()
        }

    private fun tracePath(b: PathBuilder, d: String) {
        var i = 0
        val n = d.length
        var cmd = 'M'
        var curX = 0f; var curY = 0f
        var startX = 0f; var startY = 0f
        var lastCmd = ' '

        fun readNum(): Float {
            while (i < n && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++
            if (i >= n) return 0f
            var sign = 1f
            if (d[i] == '-') { sign = -1f; i++ } else if (d[i] == '+') { i++ }
            var v = 0f
            var seenDot = false
            var exp = 0
            var expSign = 1
            var inExp = false
            while (i < n) {
                val c = d[i]
                if (c in '0'..'9') {
                    if (inExp) { exp = exp * 10 + (c - '0') } else if (seenDot) { v = v * 10f + (c - '0'); exp-- } else { v = v * 10f + (c - '0') }
                    i++
                } else if (c == '.') { seenDot = true; i++ }
                else if (c == 'e' || c == 'E') { inExp = true; i++; if (i < n && (d[i] == '-' || d[i] == '+')) { if (d[i] == '-') expSign = -1; i++ } }
                else break
            }
            if (exp != 0) v *= Math.pow(10.0, (exp * expSign).toDouble()).toFloat()
            return sign * v
        }

        fun nextCmd(): Char {
            while (i < n && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++
            if (i >= n) return ' '
            val c = d[i]
            if (c.isLetter()) { i++; return c }
            return cmd
        }

        while (i < n) {
            val c = nextCmd()
            if (c == ' ') break
            cmd = c
            val rel = cmd in 'a'..'z'
            when (cmd.uppercaseChar()) {
                'M' -> { val x = readNum(); val y = readNum(); curX = if (rel) curX + x else x; curY = if (rel) curY + y else y; startX = curX; startY = curY; b.moveTo(curX, curY) }
                'L' -> { val x = readNum(); val y = readNum(); curX = if (rel) curX + x else x; curY = if (rel) curY + y else y; b.lineTo(curX, curY) }
                'H' -> { val x = readNum(); curX = if (rel) curX + x else x; b.lineTo(curX, curY) }
                'V' -> { val y = readNum(); curY = if (rel) curY + y else y; b.lineTo(curX, curY) }
                'C' -> {
                    val x1 = readNum(); val y1 = readNum(); val x2 = readNum(); val y2 = readNum(); val x3 = readNum(); val y3 = readNum()
                    val c1x = if (rel) curX + x1 else x1; val c1y = if (rel) curY + y1 else y1
                    val c2x = if (rel) curX + x2 else x2; val c2y = if (rel) curY + y2 else y2
                    val ex = if (rel) curX + x3 else x3; val ey = if (rel) curY + y3 else y3
                    b.curveTo(c1x, c1y, c2x, c2y, ex, ey)
                    curX = ex; curY = ey
                }
                'S' -> {
                    val x2 = readNum(); val y2 = readNum(); val x3 = readNum(); val y3 = readNum()
                    val c2x = if (rel) curX + x2 else x2; val c2y = if (rel) curY + y2 else y2
                    val ex = if (rel) curX + x3 else x3; val ey = if (rel) curY + y3 else y3
                    b.curveTo(curX, curY, c2x, c2y, ex, ey)
                    curX = ex; curY = ey
                }
                'Q' -> {
                    val x1 = readNum(); val y1 = readNum(); val x2 = readNum(); val y2 = readNum()
                    val c1x = if (rel) curX + x1 else x1; val c1y = if (rel) curY + y1 else y1
                    val ex = if (rel) curX + x2 else x2; val ey = if (rel) curY + y2 else y2
                    b.quadTo(c1x, c1y, ex, ey)
                    curX = ex; curY = ey
                }
                'A' -> {
                    val rx = readNum(); val ry = readNum(); readNum()
                    val largeArc = readNum(); val sweep = readNum()
                    val x = readNum(); val y = readNum()
                    val ex = if (rel) curX + x else x; val ey = if (rel) curY + y else y
                    arcTo(b, curX, curY, ex, ey, rx, ry, largeArc > 0, sweep > 0)
                    curX = ex; curY = ey
                }
                'Z' -> { b.close(); curX = startX; curY = startY }
            }
            lastCmd = cmd
        }
    }

    private fun arcTo(b: PathBuilder, x0: Float, y0: Float, x1: Float, y1: Float, rx: Float, ry: Float, largeArc: Boolean, sweep: Boolean) {
        val dx = (x1 - x0) / 2f; val dy = (y1 - y0) / 2f
        val midX = (x0 + x1) / 2f; val midY = (y0 + y1) / 2f
        b.quadTo(midX + (if (sweep) -dy else dy) * 0.55f, midY + (if (sweep) dx else -dx) * 0.55f, x1, y1)
    }


    val addressBook: ImageVector by lazy {
        icon("address-book", listOf(
            """M16 2v2""",
            """M17.915 22a6 6 0 0 0-12 0""",
            """M8 2v2""",
            """M8.0 12.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M3.0 4.0 h18.0 v18.0 h-18.0 Z"""
        ))
    }
    val alertCircle: ImageVector by lazy {
        icon("alertCircle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val archive: ImageVector by lazy {
        icon("archive", listOf(
            """M2.0 3.0 h20.0 v5.0 h-20.0 Z""",
            """M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8""",
            """M10 12h4"""
        ))
    }
    val arrowDown: ImageVector by lazy {
        icon("arrow-down", listOf(
            """M12 5v14""",
            """m19 12-7 7-7-7"""
        ))
    }
    val arrowLeft: ImageVector by lazy {
        icon("arrow-left", listOf(
            """m12 19-7-7 7-7""",
            """M19 12H5"""
        ))
    }
    val arrowRight: ImageVector by lazy {
        icon("arrow-right", listOf(
            """M5 12h14""",
            """m12 5 7 7-7 7"""
        ))
    }
    val arrowRotateCw: ImageVector by lazy {
        icon("arrow-rotate-cw", listOf(
            """M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8""",
            """M21 3v5h-5"""
        ))
    }
    val arrowUpRight: ImageVector by lazy {
        icon("arrow-up-right", listOf(
            """M7 7h10v10""",
            """M7 17 17 7"""
        ))
    }
    val arrowUp: ImageVector by lazy {
        icon("arrow-up", listOf(
            """m5 12 7-7 7 7""",
            """M12 19V5"""
        ))
    }
    val arrowBigUp: ImageVector by lazy {
        icon("arrowBigUp", listOf(
            """m5 12 7-7 7 7""",
            """M12 19V5"""
        ))
    }
    val arrowUpDown: ImageVector by lazy {
        icon("arrowUpDown", listOf(
            """m21 16-4 4-4-4""",
            """M17 20V4""",
            """m3 8 4-4 4 4""",
            """M7 4v16"""
        ))
    }
    val atSign: ImageVector by lazy {
        icon("atSign", listOf(
            """M8.0 12.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8"""
        ))
    }
    val audioLines: ImageVector by lazy {
        icon("audio-lines", listOf(
            """M2 10v3""",
            """M6 6v11""",
            """M10 3v18""",
            """M14 8v7""",
            """M18 5v13""",
            """M22 10v3"""
        ))
    }
    val ban: ImageVector by lazy {
        icon("ban", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """m4.9 4.9 14.2 14.2"""
        ))
    }
    val bell: ImageVector by lazy {
        icon("bell", listOf(
            """M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9""",
            """M10.3 21a1.94 1.94 0 0 0 3.4 0"""
        ))
    }
    val bolt: ImageVector by lazy {
        icon("bolt", listOf(
            """M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z""",
            """M8.0 12.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z"""
        ))
    }
    val calendar: ImageVector by lazy {
        icon("calendar", listOf(
            """M8 2v4""",
            """M16 2v4""",
            """M3.0 4.0 h18.0 v18.0 h-18.0 Z""",
            """M3 10h18"""
        ))
    }
    val call: ImageVector by lazy {
        icon("call", listOf(
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z""",
            """M14.05 2a9 9 0 0 1 8 7.94""",
            """M14.05 6A5 5 0 0 1 18 10"""
        ))
    }
    val cameraOff: ImageVector by lazy {
        icon("camera-off", listOf(
            """M0.0 0.0 L0.0 0.0""",
            """M7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16""",
            """M9.5 4h5L17 7h3a2 2 0 0 1 2 2v7.5""",
            """M14.121 15.121A3 3 0 1 1 9.88 10.88"""
        ))
    }
    val cameraRetro: ImageVector by lazy {
        icon("camera-retro", listOf(
            """M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z""",
            """M9.0 13.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z"""
        ))
    }
    val camera: ImageVector by lazy {
        icon("camera", listOf(
            """M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z""",
            """M9.0 13.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z"""
        ))
    }
    val chatBubbleText: ImageVector by lazy {
        icon("chat-bubble-text", listOf(
            """M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z""",
            """M13 8H7""",
            """M17 12H7"""
        ))
    }
    val chatBubble: ImageVector by lazy {
        icon("chatBubble", listOf(
            """M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"""
        ))
    }
    val checkCheck: ImageVector by lazy {
        icon("check-check", listOf(
            """M18 6 7 17l-5-5""",
            """m22 10-7.5 7.5L13 16"""
        ))
    }
    val checkCircle2: ImageVector by lazy {
        icon("check-circle-2", listOf(
            """M21.801 10A10 10 0 1 1 17 3.335""",
            """m9 11 3 3L22 4"""
        ))
    }
    val checkCircle: ImageVector by lazy {
        icon("check-circle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """m9 12 2 2 4-4"""
        ))
    }
    val check: ImageVector by lazy {
        icon("check", listOf(
            """M20 6 9 17l-5-5"""
        ))
    }
    val chevronLeft: ImageVector by lazy {
        icon("chevron-left", listOf(
            """m15 18-6-6 6-6"""
        ))
    }
    val chevronRight: ImageVector by lazy {
        icon("chevronRight", listOf(
            """m9 18 6-6-6-6"""
        ))
    }
    val circleX: ImageVector by lazy {
        icon("circle-x", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """m15 9-6 6""",
            """m9 9 6 6"""
        ))
    }
    val circlePlay: ImageVector by lazy {
        icon("circlePlay", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M10.0 8.0 16.0 12.0 10.0 16.0 10.0 8.0 Z"""
        ))
    }
    val clock: ImageVector by lazy {
        icon("clock", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M12.0 6.0 12.0 12.0 16.5 12.0"""
        ))
    }
    val close: ImageVector by lazy {
        icon("close", listOf(
            """M18 6 6 18""",
            """m6 6 12 12"""
        ))
    }
    val compass: ImageVector by lazy {
        icon("compass", listOf(
            """m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z""",
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z"""
        ))
    }
    val copy: ImageVector by lazy {
        icon("copy", listOf(
            """M8.0 8.0 h14.0 v14.0 h-14.0 Z""",
            """M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"""
        ))
    }
    val database: ImageVector by lazy {
        icon("database", listOf(
            """M3.0 5.0 a9.0 3.0 0 1 0 18.0 0 a9.0 3.0 0 1 0 -18.0 0 Z""",
            """M3 5V19A9 3 0 0 0 21 19V5""",
            """M3 12A9 3 0 0 0 21 12"""
        ))
    }
    val deviceDesktop: ImageVector by lazy {
        icon("deviceDesktop", listOf(
            """M2.0 3.0 h20.0 v14.0 h-20.0 Z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val download: ImageVector by lazy {
        icon("download", listOf(
            """M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4""",
            """M7.0 10.0 12.0 15.0 17.0 10.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val ellipsisVertical: ImageVector by lazy {
        icon("ellipsisVertical", listOf(
            """M11.0 12.0 a1.0 1.0 0 1 0 2.0 0 a1.0 1.0 0 1 0 -2.0 0 Z""",
            """M11.0 5.0 a1.0 1.0 0 1 0 2.0 0 a1.0 1.0 0 1 0 -2.0 0 Z""",
            """M11.0 19.0 a1.0 1.0 0 1 0 2.0 0 a1.0 1.0 0 1 0 -2.0 0 Z"""
        ))
    }
    val eyeOff: ImageVector by lazy {
        icon("eye-off", listOf(
            """M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49""",
            """M14.084 14.158a3 3 0 0 1-4.242-4.242""",
            """M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143""",
            """m2 2 20 20"""
        ))
    }
    val eye: ImageVector by lazy {
        icon("eye", listOf(
            """M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0""",
            """M9.0 12.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z"""
        ))
    }
    val file: ImageVector by lazy {
        icon("file", listOf(
            """M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z""",
            """M14 2v4a2 2 0 0 0 2 2h4"""
        ))
    }
    val flag: ImageVector by lazy {
        icon("flag", listOf(
            """M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val folder: ImageVector by lazy {
        icon("folder", listOf(
            """M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"""
        ))
    }
    val forward: ImageVector by lazy {
        icon("forward", listOf(
            """M15.0 17.0 20.0 12.0 15.0 7.0""",
            """M4 18v-2a4 4 0 0 1 4-4h12"""
        ))
    }
    val gift: ImageVector by lazy {
        icon("gift", listOf(
            """M3.0 8.0 h18.0 v4.0 h-18.0 Z""",
            """M12 8v13""",
            """M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7""",
            """M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5"""
        ))
    }
    val grid2x2: ImageVector by lazy {
        icon("grid-2x2", listOf(
            """M3.0 3.0 h18.0 v18.0 h-18.0 Z""",
            """M3 12h18""",
            """M12 3v18"""
        ))
    }
    val hand: ImageVector by lazy {
        icon("hand", listOf(
            """M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2""",
            """M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2""",
            """M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8""",
            """M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"""
        ))
    }
    val handshake: ImageVector by lazy {
        icon("handshake", listOf(
            """m11 17 2 2a1 1 0 1 0 3-3""",
            """m14 14 2.5 2.5a1 1 0 1 0 3-3l-3.88-3.88a3 3 0 0 0-4.24 0l-.88.88a1 1 0 1 1-3-3l2.81-2.81a5.79 5.79 0 0 1 7.06-.87l.47.28a2 2 0 0 0 1.42.25L21 4""",
            """m21 3 1 11h-2""",
            """M3 3 2 14l6.5 6.5a1 1 0 1 0 3-3""",
            """M3 4h8"""
        ))
    }
    val headphones: ImageVector by lazy {
        icon("headphones", listOf(
            """M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3"""
        ))
    }
    val heartPulse: ImageVector by lazy {
        icon("heart-pulse", listOf(
            """M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z""",
            """M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27"""
        ))
    }
    val heart: ImageVector by lazy {
        icon("heart", listOf(
            """M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"""
        ))
    }
    val imagePlus: ImageVector by lazy {
        icon("image-plus", listOf(
            """M16 5h6""",
            """M19 2v6""",
            """M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5""",
            """m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21""",
            """M7.0 9.0 a2.0 2.0 0 1 0 4.0 0 a2.0 2.0 0 1 0 -4.0 0 Z"""
        ))
    }
    val image: ImageVector by lazy {
        icon("image", listOf(
            """M3.0 3.0 h18.0 v18.0 h-18.0 Z""",
            """M7.0 9.0 a2.0 2.0 0 1 0 4.0 0 a2.0 2.0 0 1 0 -4.0 0 Z""",
            """m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"""
        ))
    }
    val info: ImageVector by lazy {
        icon("info", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M12 16v-4""",
            """M12 8h.01"""
        ))
    }
    val link: ImageVector by lazy {
        icon("link", listOf(
            """M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71""",
            """M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"""
        ))
    }
    val lockKeyhole: ImageVector by lazy {
        icon("lock-keyhole", listOf(
            """M11.0 16.0 a1.0 1.0 0 1 0 2.0 0 a1.0 1.0 0 1 0 -2.0 0 Z""",
            """M3.0 10.0 h18.0 v12.0 h-18.0 Z""",
            """M7 10V7a5 5 0 0 1 10 0v3"""
        ))
    }
    val lock: ImageVector by lazy {
        icon("lock", listOf(
            """M3.0 11.0 h18.0 v11.0 h-18.0 Z""",
            """M7 11V7a5 5 0 0 1 10 0v4"""
        ))
    }
    val lockOpen: ImageVector by lazy {
        icon("lockOpen", listOf(
            """M3.0 11.0 h18.0 v11.0 h-18.0 Z""",
            """M7 11V7a5 5 0 0 1 9.9-1"""
        ))
    }
    val logOut: ImageVector by lazy {
        icon("logOut", listOf(
            """M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4""",
            """M16.0 17.0 21.0 12.0 16.0 7.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val mapPin: ImageVector by lazy {
        icon("map-pin", listOf(
            """M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0""",
            """M9.0 10.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z"""
        ))
    }
    val messageCircleMore: ImageVector by lazy {
        icon("message-circle-more", listOf(
            """M7.9 20A9 9 0 1 0 4 16.1L2 22Z""",
            """M8 12h.01""",
            """M12 12h.01""",
            """M16 12h.01"""
        ))
    }
    val messageCircle: ImageVector by lazy {
        icon("message-circle", listOf(
            """M7.9 20A9 9 0 1 0 4 16.1L2 22Z"""
        ))
    }
    val messageSquare: ImageVector by lazy {
        icon("messageSquare", listOf(
            """M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"""
        ))
    }
    val messagesSquare: ImageVector by lazy {
        icon("messages-square", listOf(
            """M14 9a2 2 0 0 1-2 2H6l-4 4V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2z""",
            """M18 9h2a2 2 0 0 1 2 2v11l-4-4h-6a2 2 0 0 1-2-2v-1"""
        ))
    }
    val mic: ImageVector by lazy {
        icon("mic", listOf(
            """M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z""",
            """M19 10v2a7 7 0 0 1-14 0v-2""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val micOff: ImageVector by lazy {
        icon("micOff", listOf(
            """M0.0 0.0 L0.0 0.0""",
            """M18.89 13.23A7.12 7.12 0 0 0 19 12v-2""",
            """M5 10v2a7 7 0 0 0 12 5""",
            """M15 9.34V5a3 3 0 0 0-5.68-1.33""",
            """M9 9v3a3 3 0 0 0 5.12 2.12""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val microphoneSlash: ImageVector by lazy {
        icon("microphone-slash", listOf(
            """M0.0 0.0 L0.0 0.0""",
            """M18.89 13.23A7.12 7.12 0 0 0 19 12v-2""",
            """M5 10v2a7 7 0 0 0 12 5""",
            """M15 9.34V5a3 3 0 0 0-5.68-1.33""",
            """M9 9v3a3 3 0 0 0 5.12 2.12""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val minus: ImageVector by lazy {
        icon("minus", listOf(
            """M5 12h14"""
        ))
    }
    val minusCircle: ImageVector by lazy {
        icon("minusCircle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M8 12h8"""
        ))
    }
    val moon: ImageVector by lazy {
        icon("moon", listOf(
            """M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"""
        ))
    }
    val packageOpen: ImageVector by lazy {
        icon("packageOpen", listOf(
            """M12 22v-9""",
            """M15.17 2.21a1.67 1.67 0 0 1 1.63 0L21 4.57a1.93 1.93 0 0 1 0 3.36L8.82 14.79a1.655 1.655 0 0 1-1.64 0L3 12.43a1.93 1.93 0 0 1 0-3.36z""",
            """M20 13v3.87a2.06 2.06 0 0 1-1.11 1.83l-6 3.08a1.93 1.93 0 0 1-1.78 0l-6-3.08A2.06 2.06 0 0 1 4 16.87V13""",
            """M21 12.43a1.93 1.93 0 0 0 0-3.36L8.83 2.2a1.64 1.64 0 0 0-1.63 0L3 4.57a1.93 1.93 0 0 0 0 3.36l12.18 6.86a1.636 1.636 0 0 0 1.63 0z"""
        ))
    }
    val palette: ImageVector by lazy {
        icon("palette", listOf(
            """M13.0 6.5 a0.5 0.5 0 1 0 1.0 0 a0.5 0.5 0 1 0 -1.0 0 Z""",
            """M17.0 10.5 a0.5 0.5 0 1 0 1.0 0 a0.5 0.5 0 1 0 -1.0 0 Z""",
            """M8.0 7.5 a0.5 0.5 0 1 0 1.0 0 a0.5 0.5 0 1 0 -1.0 0 Z""",
            """M6.0 12.5 a0.5 0.5 0 1 0 1.0 0 a0.5 0.5 0 1 0 -1.0 0 Z""",
            """M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"""
        ))
    }
    val paperclip: ImageVector by lazy {
        icon("paperclip", listOf(
            """m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"""
        ))
    }
    val pause: ImageVector by lazy {
        icon("pause", listOf(
            """M14.0 4.0 h4.0 v16.0 h-4.0 Z""",
            """M6.0 4.0 h4.0 v16.0 h-4.0 Z"""
        ))
    }
    val pawPrint: ImageVector by lazy {
        icon("pawPrint", listOf(
            """M9.0 4.0 a2.0 2.0 0 1 0 4.0 0 a2.0 2.0 0 1 0 -4.0 0 Z""",
            """M16.0 8.0 a2.0 2.0 0 1 0 4.0 0 a2.0 2.0 0 1 0 -4.0 0 Z""",
            """M18.0 16.0 a2.0 2.0 0 1 0 4.0 0 a2.0 2.0 0 1 0 -4.0 0 Z""",
            """M9 10a5 5 0 0 1 5 5v3.5a3.5 3.5 0 0 1-6.84 1.045Q6.52 17.48 4.46 16.84A3.5 3.5 0 0 1 5.5 10Z"""
        ))
    }
    val penLine: ImageVector by lazy {
        icon("pen-line", listOf(
            """M12 20h9""",
            """M16.376 3.622a1 1 0 0 1 3.002 3.002L7.368 18.635a2 2 0 0 1-.855.506l-2.872.838a.5.5 0 0 1-.62-.62l.838-2.872a2 2 0 0 1 .506-.854z"""
        ))
    }
    val pencil: ImageVector by lazy {
        icon("pencil", listOf(
            """M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z""",
            """m15 5 4 4"""
        ))
    }
    val personAdd: ImageVector by lazy {
        icon("personAdd", listOf(
            """M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2""",
            """M5.0 7.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val phoneCall2: ImageVector by lazy {
        icon("phone-call-2", listOf(
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z""",
            """M14.05 2a9 9 0 0 1 8 7.94""",
            """M14.05 6A5 5 0 0 1 18 10"""
        ))
    }
    val phoneDisconnect: ImageVector by lazy {
        icon("phone-disconnect", listOf(
            """M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val phoneIncoming: ImageVector by lazy {
        icon("phone-incoming", listOf(
            """M16.0 2.0 16.0 8.0 22.0 8.0""",
            """M0.0 0.0 L0.0 0.0""",
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"""
        ))
    }
    val phoneMissed: ImageVector by lazy {
        icon("phone-missed", listOf(
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0""",
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"""
        ))
    }
    val phoneOutgoing: ImageVector by lazy {
        icon("phone-outgoing", listOf(
            """M22.0 8.0 22.0 2.0 16.0 2.0""",
            """M0.0 0.0 L0.0 0.0""",
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"""
        ))
    }
    val phone: ImageVector by lazy {
        icon("phone", listOf(
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"""
        ))
    }
    val phoneCall: ImageVector by lazy {
        icon("phoneCall", listOf(
            """M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z""",
            """M14.05 2a9 9 0 0 1 8 7.94""",
            """M14.05 6A5 5 0 0 1 18 10"""
        ))
    }
    val pin: ImageVector by lazy {
        icon("pin", listOf(
            """M12 17v5""",
            """M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"""
        ))
    }
    val plane: ImageVector by lazy {
        icon("plane", listOf(
            """M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z"""
        ))
    }
    val play: ImageVector by lazy {
        icon("play", listOf(
            """M6.0 3.0 20.0 12.0 6.0 21.0 6.0 3.0 Z"""
        ))
    }
    val playCircle: ImageVector by lazy {
        icon("playCircle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M10.0 8.0 16.0 12.0 10.0 16.0 10.0 8.0 Z"""
        ))
    }
    val plusCircle: ImageVector by lazy {
        icon("plus-circle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M8 12h8""",
            """M12 8v8"""
        ))
    }
    val poll: ImageVector by lazy {
        icon("poll", listOf(
            """M3 3v16a2 2 0 0 0 2 2h16""",
            """M7 16h8""",
            """M7 11h12""",
            """M7 6h3"""
        ))
    }
    val qrCode: ImageVector by lazy {
        icon("qrCode", listOf(
            """M3.0 3.0 h5.0 v5.0 h-5.0 Z""",
            """M16.0 3.0 h5.0 v5.0 h-5.0 Z""",
            """M3.0 16.0 h5.0 v5.0 h-5.0 Z""",
            """M21 16h-3a2 2 0 0 0-2 2v3""",
            """M21 21v.01""",
            """M12 7v3a2 2 0 0 1-2 2H7""",
            """M3 12h.01""",
            """M12 3h.01""",
            """M12 16v.01""",
            """M16 12h1""",
            """M21 12v.01""",
            """M12 21v-1"""
        ))
    }
    val refreshCw: ImageVector by lazy {
        icon("refresh-cw", listOf(
            """M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8""",
            """M21 3v5h-5""",
            """M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16""",
            """M8 16H3v5"""
        ))
    }
    val reply: ImageVector by lazy {
        icon("reply", listOf(
            """M9.0 17.0 4.0 12.0 9.0 7.0""",
            """M20 18v-2a4 4 0 0 0-4-4H4"""
        ))
    }
    val rotateCcw: ImageVector by lazy {
        icon("rotateCcw", listOf(
            """M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8""",
            """M3 3v5h5"""
        ))
    }
    val screenShare: ImageVector by lazy {
        icon("screen-share", listOf(
            """M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3""",
            """M8 21h8""",
            """M12 17v4""",
            """m17 8 5-5""",
            """M17 3h5v5"""
        ))
    }
    val search: ImageVector by lazy {
        icon("search", listOf(
            """M3.0 11.0 a8.0 8.0 0 1 0 16.0 0 a8.0 8.0 0 1 0 -16.0 0 Z""",
            """m21 21-4.3-4.3"""
        ))
    }
    val searchX: ImageVector by lazy {
        icon("searchX", listOf(
            """m13.5 8.5-5 5""",
            """m8.5 8.5 5 5""",
            """M3.0 11.0 a8.0 8.0 0 1 0 16.0 0 a8.0 8.0 0 1 0 -16.0 0 Z""",
            """m21 21-4.3-4.3"""
        ))
    }
    val sendHorizontal: ImageVector by lazy {
        icon("send-horizontal", listOf(
            """M3.714 3.048a.498.498 0 0 0-.683.627l2.843 7.627a2 2 0 0 1 0 1.396l-2.842 7.627a.498.498 0 0 0 .682.627l18-8.5a.5.5 0 0 0 0-.904z""",
            """M6 12h16"""
        ))
    }
    val send: ImageVector by lazy {
        icon("send", listOf(
            """M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z""",
            """m21.854 2.147-10.94 10.939"""
        ))
    }
    val settings: ImageVector by lazy {
        icon("settings", listOf(
            """M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z""",
            """M9.0 12.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z"""
        ))
    }
    val shareNetwork: ImageVector by lazy {
        icon("shareNetwork", listOf(
            """M15.0 5.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z""",
            """M3.0 12.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z""",
            """M15.0 19.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val shieldCheck: ImageVector by lazy {
        icon("shield-check", listOf(
            """M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z""",
            """m9 12 2 2 4-4"""
        ))
    }
    val smile: ImageVector by lazy {
        icon("smile", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M8 14s1.5 2 4 2 4-2 4-2""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val smileyPlus: ImageVector by lazy {
        icon("smiley-plus", listOf(
            """M22 11v1a10 10 0 1 1-9-10""",
            """M8 14s1.5 2 4 2 4-2 4-2""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0""",
            """M16 5h6""",
            """M19 2v6"""
        ))
    }
    val smileySticker: ImageVector by lazy {
        icon("smiley-sticker", listOf(
            """M15.5 3H5a2 2 0 0 0-2 2v14c0 1.1.9 2 2 2h14a2 2 0 0 0 2-2V8.5L15.5 3Z""",
            """M14 3v4a2 2 0 0 0 2 2h4""",
            """M8 13h.01""",
            """M16 13h.01""",
            """M10 16s.8 1 2 1c1.3 0 2-1 2-1"""
        ))
    }
    val sparkle: ImageVector by lazy {
        icon("sparkle", listOf(
            """M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"""
        ))
    }
    val sparkles: ImageVector by lazy {
        icon("sparkles", listOf(
            """M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z""",
            """M20 3v4""",
            """M22 5h-4""",
            """M4 17v2""",
            """M5 18H3"""
        ))
    }
    val speakerHigh: ImageVector by lazy {
        icon("speaker-high", listOf(
            """M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z""",
            """M16 9a5 5 0 0 1 0 6""",
            """M19.364 18.364a9 9 0 0 0 0-12.728"""
        ))
    }
    val star: ImageVector by lazy {
        icon("star", listOf(
            """M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"""
        ))
    }
    val store: ImageVector by lazy {
        icon("store", listOf(
            """m2 7 4.41-4.41A2 2 0 0 1 7.83 2h8.34a2 2 0 0 1 1.42.59L22 7""",
            """M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8""",
            """M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4""",
            """M2 7h20""",
            """M22 7v3a2 2 0 0 1-2 2a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 16 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 12 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 8 12a2.7 2.7 0 0 1-1.59-.63.7.7 0 0 0-.82 0A2.7 2.7 0 0 1 4 12a2 2 0 0 1-2-2V7"""
        ))
    }
    val sun: ImageVector by lazy {
        icon("sun", listOf(
            """M8.0 12.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M12 2v2""",
            """M12 20v2""",
            """m4.93 4.93 1.41 1.41""",
            """m17.66 17.66 1.41 1.41""",
            """M2 12h2""",
            """M20 12h2""",
            """m6.34 17.66-1.41 1.41""",
            """m19.07 4.93-1.41 1.41"""
        ))
    }
    val tag: ImageVector by lazy {
        icon("tag", listOf(
            """M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z""",
            """M7.0 7.5 a0.5 0.5 0 1 0 1.0 0 a0.5 0.5 0 1 0 -1.0 0 Z"""
        ))
    }
    val thumbsUp: ImageVector by lazy {
        icon("thumbsUp", listOf(
            """M7 10v12""",
            """M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"""
        ))
    }
    val trash2: ImageVector by lazy {
        icon("trash-2", listOf(
            """M3 6h18""",
            """M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6""",
            """M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val userRound: ImageVector by lazy {
        icon("user-round", listOf(
            """M7.0 8.0 a5.0 5.0 0 1 0 10.0 0 a5.0 5.0 0 1 0 -10.0 0 Z""",
            """M20 21a8 8 0 0 0-16 0"""
        ))
    }
    val user: ImageVector by lazy {
        icon("user", listOf(
            """M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2""",
            """M8.0 7.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z"""
        ))
    }
    val userMinus: ImageVector by lazy {
        icon("userMinus", listOf(
            """M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2""",
            """M5.0 7.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val userPlus: ImageVector by lazy {
        icon("userPlus", listOf(
            """M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2""",
            """M5.0 7.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val users: ImageVector by lazy {
        icon("users", listOf(
            """M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2""",
            """M5.0 7.0 a4.0 4.0 0 1 0 8.0 0 a4.0 4.0 0 1 0 -8.0 0 Z""",
            """M22 21v-2a4 4 0 0 0-3-3.87""",
            """M16 3.13a4 4 0 0 1 0 7.75"""
        ))
    }
    val utensils: ImageVector by lazy {
        icon("utensils", listOf(
            """M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2""",
            """M7 2v20""",
            """M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7"""
        ))
    }
    val videoOff: ImageVector by lazy {
        icon("video-off", listOf(
            """M10.66 6H14a2 2 0 0 1 2 2v2.5l5.248-3.062A.5.5 0 0 1 22 7.87v8.196""",
            """M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2""",
            """m2 2 20 20"""
        ))
    }
    val video: ImageVector by lazy {
        icon("video", listOf(
            """m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5""",
            """M2.0 6.0 h14.0 v12.0 h-14.0 Z"""
        ))
    }
    val volume2: ImageVector by lazy {
        icon("volume-2", listOf(
            """M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z""",
            """M16 9a5 5 0 0 1 0 6""",
            """M19.364 18.364a9 9 0 0 0 0-12.728"""
        ))
    }
    val volumeX: ImageVector by lazy {
        icon("volume-x", listOf(
            """M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z""",
            """M0.0 0.0 L0.0 0.0""",
            """M0.0 0.0 L0.0 0.0"""
        ))
    }
    val wifiOff: ImageVector by lazy {
        icon("wifi-off", listOf(
            """M12 20h.01""",
            """M8.5 16.429a5 5 0 0 1 7 0""",
            """M5 12.859a10 10 0 0 1 5.17-2.69""",
            """M19 12.859a10 10 0 0 0-2.007-1.523""",
            """M2 8.82a15 15 0 0 1 4.177-2.643""",
            """M22 8.82a15 15 0 0 0-11.288-3.764""",
            """m2 2 20 20"""
        ))
    }
    val wifi: ImageVector by lazy {
        icon("wifi", listOf(
            """M12 20h.01""",
            """M2 8.82a15 15 0 0 1 20 0""",
            """M5 12.859a10 10 0 0 1 14 0""",
            """M8.5 16.429a5 5 0 0 1 7 0"""
        ))
    }
    val wifiOff2: ImageVector by lazy {
        icon("wifiOff2", listOf(
            """M12 20h.01""",
            """M8.5 16.429a5 5 0 0 1 7 0""",
            """M5 12.859a10 10 0 0 1 5.17-2.69""",
            """M19 12.859a10 10 0 0 0-2.007-1.523""",
            """M2 8.82a15 15 0 0 1 4.177-2.643""",
            """M22 8.82a15 15 0 0 0-11.288-3.764""",
            """m2 2 20 20"""
        ))
    }
    val x: ImageVector by lazy {
        icon("x", listOf(
            """M18 6 6 18""",
            """m6 6 12 12"""
        ))
    }
    val xCircle: ImageVector by lazy {
        icon("xCircle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """m15 9-6 6""",
            """m9 9 6 6"""
        ))
    }
    val save: ImageVector by lazy {
        icon("save", listOf(
            """M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z""",
            """M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7""",
            """M7 3v4a1 1 0 0 0 1 1h7"""
        ))
    }
    val saveFilled: ImageVector by lazy {
        filledIcon("saveFilled", listOf(
            """M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8 3v4a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V3h-2v3.5h-3V3zM17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7h2v-4h6v4z"""
        ))
    }
    val formatBold: ImageVector by lazy {
        icon("formatBold", listOf(
            """M6.75 4.75h4.5a3.5 3.5 0 0 1 0 7h-4.5z""",
            """M6.75 11.75h5a3.5 3.5 0 0 1 0 7h-5z"""
        ))
    }
    val formatItalic: ImageVector by lazy {
        icon("formatItalic", listOf(
            """M19 4h-9""",
            """M14 20H5""",
            """M15 4 9 20"""
        ))
    }
    val formatStrikethrough: ImageVector by lazy {
        icon("formatStrikethrough", listOf(
            """M16 16a4 4 0 0 1-8 0""",
            """M4 12h16""",
            """M6 8a4 4 0 0 1 12 0"""
        ))
    }
    val formatMonospace: ImageVector by lazy {
        icon("formatMonospace", listOf(
            """M4 6c0 1.5.8 2 2 2s2-.5 2-2c0-2-1-3-1-4h6c0 1-1 2-1 4 0 1.5.8 2 2 2s2-.5 2-2c0-2-1-3-1-4""",
            """M6 18c1.5 0 2-.8 2-2s-.5-2-2-2c-2 0-3 1-4 1h16c-1 0-2-1-4-1-1.5 0-2 .8-2 2s.5 2 2 2c1.5 0 2-.8 2-2"""
        ))
    }
    val spoiler: ImageVector by lazy {
        icon("spoiler", listOf(
            """M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94""",
            """M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19""",
            """M14.12 14.12a3 3 0 1 1-4.24-4.24""",
            """m1 1 22 22"""
        ))
    }
    val timer: ImageVector by lazy {
        icon("timer", listOf(
            """M10 2h4""",
            """M12 14v-4""",
            """M4.93 10.93a1.4 1.4 0 0 0-1.93 1.93a9 9 0 1 0 0-1.72""",
            """M9.5 2.5h5""",
            """M12 14l2.8-2.8a1.4 1.4 0 0 0-.7-2.37"""
        ))
    }
    val invite: ImageVector by lazy {
        icon("invite", listOf(
            """M14 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2""",
            """M5.0 7.0 a3.0 3.0 0 1 0 6.0 0 a3.0 3.0 0 1 0 -6.0 0 Z""",
            """M19 15v6""",
            """M16 18h6"""
        ))
    }
    val helpCircle: ImageVector by lazy {
        icon("helpCircle", listOf(
            """M2.0 12.0 a10.0 10.0 0 1 0 20.0 0 a10.0 10.0 0 1 0 -20.0 0 Z""",
            """M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3""",
            """M12 17h.01"""
        ))
    }
    val flash: ImageVector by lazy {
        icon("flash", listOf(
            """M13 2 3 14h9l-1 8 10-12h-9l1-8z"""
        ))
    }
    val payment: ImageVector by lazy {
        icon("payment", listOf(
            """M2.0 4.0 L22.0 4.0 L22.0 20.0 L2.0 20.0 Z""",
            """M2 10h20"""
        ))
    }
    val deviceMobile: ImageVector by lazy {
        icon("deviceMobile", listOf(
            """M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z""",
            """M11 18h2"""
        ))
    }
    val gallery: ImageVector by lazy {
        icon("gallery", listOf(
            """M2 3h20a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z""",
            """M2 13l4.5-4.5a1.5 1.5 0 0 1 2.1 0L14 14""",
            """M14.5 11.5 17 9a1.5 1.5 0 0 1 2.1 0L22 12""",
            """M6 20l4-4""",
            """M15 19l2-2 3 3"""
        ))
    }
    val downloadFilled: ImageVector by lazy {
        filledIcon("downloadFilled", listOf(
            """M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-1 13.59V7a1 1 0 0 1 2 0v8.59l2.3-2.3a1 1 0 0 1 1.4 1.42l-4 4a1 1 0 0 1-1.4 0l-4-4a1 1 0 0 1 1.4-1.42z"""
        ))
    }
    val messageCircleFill: ImageVector by lazy {
        filledIcon("messageCircleFill", listOf(
            """M12 2C6.48 2 2 6.02 2 11c0 2.6 1.14 4.94 2.98 6.6L4 22l4.65-1.68A10.2 10.2 0 0 0 12 20c5.52 0 10-4.02 10-9S17.52 2 12 2z"""
        ))
    }
    val cameraFill: ImageVector by lazy {
        filledIcon("cameraFill", listOf(
            """M9 3 7.5 5H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.5L15 3H9zm3 15a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"""
        ))
    }
    val phoneFill: ImageVector by lazy {
        filledIcon("phoneFill", listOf(
            """M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.02-.24c1.12.37 2.33.57 3.57.57a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1C10.85 21 3 13.15 3 3.5a1 1 0 0 1 1-1H7.5a1 1 0 0 1 1 1c0 1.24.2 2.45.57 3.57a1 1 0 0 1-.25 1.02l-2.2 2.2z"""
        ))
    }
    val videoFill: ImageVector by lazy {
        filledIcon("videoFill", listOf(
            """M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z"""
        ))
    }
}
