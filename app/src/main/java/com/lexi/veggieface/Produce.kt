package com.lexi.veggieface

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun hex(v: Long): Color = Color(0xFF000000L or v)

/**
 * A fruit or veggie. [faceX]/[faceY] is where the face goes and [faceSize] how wide it is,
 * all as fractions of the picture (0..1). [tint] is used for the "veggie color" effect.
 */
enum class Produce(val title: String, val emoji: String, val faceX: Float, val faceY: Float, val faceSize: Float, val tint: Color) {
    Tomato("Tomato", "🍅", 0.5f, 0.58f, 0.46f, hex(0xFF5A4A)),
    Potato("Potato", "🥔", 0.5f, 0.55f, 0.42f, hex(0xD9A86C)),
    Celery("Celery", "🥬", 0.5f, 0.6f, 0.3f, hex(0x9BD872)),
    Cucumber("Cucumber", "🥒", 0.5f, 0.5f, 0.32f, hex(0x5FAE4C)),
    Eggplant("Eggplant", "🍆", 0.5f, 0.63f, 0.4f, hex(0x7B4BB3)),
    Carrot("Carrot", "🥕", 0.5f, 0.43f, 0.32f, hex(0xFF9330)),
    Apple("Apple", "🍎", 0.5f, 0.58f, 0.46f, hex(0xF2434F)),
    Orange("Orange", "🍊", 0.5f, 0.56f, 0.48f, hex(0xFF9A2E)),
    Strawberry("Strawberry", "🍓", 0.5f, 0.52f, 0.4f, hex(0xF23F58)),
    Kiwi("Kiwi", "🥝", 0.5f, 0.5f, 0.38f, hex(0x8FD14F)),
    Pear("Pear", "🍐", 0.5f, 0.64f, 0.4f, hex(0xC8DB5A)),
    Pumpkin("Pumpkin", "🎃", 0.5f, 0.58f, 0.42f, hex(0xFF8A24)),
}

/** Drawing helpers on a grid where 1 unit = the whole picture width ([s] pixels). */
private class Pen(val ds: DrawScope, val s: Float) {
    fun oval(c: Color, cx: Float, cy: Float, w: Float, h: Float) =
        ds.drawOval(c, Offset((cx - w / 2) * s, (cy - h / 2) * s), Size(w * s, h * s))

    fun oval(b: Brush, cx: Float, cy: Float, w: Float, h: Float) =
        ds.drawOval(b, Offset((cx - w / 2) * s, (cy - h / 2) * s), Size(w * s, h * s))

    fun circle(c: Color, cx: Float, cy: Float, d: Float) = ds.drawCircle(c, d / 2 * s, Offset(cx * s, cy * s))

    fun rrect(b: Brush, cx: Float, cy: Float, w: Float, h: Float, r: Float) =
        ds.drawRoundRect(b, Offset((cx - w / 2) * s, (cy - h / 2) * s), Size(w * s, h * s), CornerRadius(r * s))

    fun rrect(c: Color, cx: Float, cy: Float, w: Float, h: Float, r: Float) =
        ds.drawRoundRect(c, Offset((cx - w / 2) * s, (cy - h / 2) * s), Size(w * s, h * s), CornerRadius(r * s))

    fun rotated(deg: Float, cx: Float, cy: Float, block: Pen.() -> Unit) =
        ds.rotate(deg, Offset(cx * s, cy * s)) { this@Pen.block() }

    fun pt(x: Float, y: Float) = Offset(x * s, y * s)
    fun radial(colors: List<Color>, cx: Float, cy: Float, r: Float) = Brush.radialGradient(colors, pt(cx, cy), r * s)
    fun vertical(colors: List<Color>, y0: Float, y1: Float) = Brush.verticalGradient(colors, y0 * s, y1 * s)

    fun path(b: Brush, build: Path.() -> Unit) = ds.drawPath(Path().apply(build), b)
    fun path(c: Color, build: Path.() -> Unit) = ds.drawPath(Path().apply(build), c)
    fun line(c: Color, w: Float, build: Path.() -> Unit) = ds.drawPath(Path().apply(build), c, style = Stroke(w * s, cap = StrokeCap.Round))

    fun Path.m(x: Float, y: Float) = moveTo(x * s, y * s)
    fun Path.l(x: Float, y: Float) = lineTo(x * s, y * s)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * s, cy * s, x * s, y * s)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * s, y1 * s, x2 * s, y2 * s, x * s, y * s)

    fun shine(cx: Float, cy: Float, w: Float, h: Float, deg: Float = -30f) =
        rotated(deg, cx, cy) { oval(Color.White.copy(alpha = 0.45f), cx, cy, w, h) }

    fun leaf(cx: Float, cy: Float, w: Float, h: Float, deg: Float, c: Color = hex(0x5CC46B)) =
        rotated(deg, cx, cy) { oval(c, cx, cy, w, h) }
}

/** Draws the produce filling a square [size] × [size] box at the current origin. */
fun DrawScope.drawProduce(p: Produce, size: Float = this.size.minDimension) {
    val pen = Pen(this, size)
    with(pen) {
        when (p) {
            Produce.Tomato -> {
                oval(radial(listOf(hex(0xFF7A6A), hex(0xE0302A)), 0.38f, 0.42f, 0.6f), 0.5f, 0.57f, 0.88f, 0.76f)
                shine(0.27f, 0.4f, 0.14f, 0.07f)
                for (i in 0 until 5) {
                    val a = -PI / 2 + i * 2 * PI / 5
                    val x = (0.5 + 0.09 * cos(a)).toFloat(); val y = (0.22 + 0.04 * sin(a)).toFloat()
                    leaf(x, y, 0.2f, 0.06f, Math.toDegrees(a).toFloat(), hex(0x4FAE5C))
                }
                rrect(hex(0x3F8F4A), 0.5f, 0.15f, 0.04f, 0.1f, 0.02f)
            }
            Produce.Potato -> {
                rotated(-8f, 0.5f, 0.55f) {
                    path(radial(listOf(hex(0xE8C08A), hex(0xB98546)), 0.4f, 0.45f, 0.55f)) {
                        m(0.1f, 0.52f)
                        c(0.08f, 0.3f, 0.3f, 0.22f, 0.5f, 0.24f)
                        c(0.75f, 0.2f, 0.94f, 0.34f, 0.92f, 0.55f)
                        c(0.9f, 0.78f, 0.7f, 0.86f, 0.48f, 0.85f)
                        c(0.24f, 0.86f, 0.11f, 0.72f, 0.1f, 0.52f)
                        close()
                    }
                }
                for ((x, y) in listOf(0.2f to 0.4f, 0.8f to 0.45f, 0.25f to 0.72f, 0.75f to 0.74f, 0.6f to 0.3f))
                    oval(hex(0x9A6B3A).copy(alpha = 0.6f), x, y, 0.04f, 0.025f)
                shine(0.3f, 0.36f, 0.12f, 0.05f)
            }
            Produce.Celery -> {
                // leafy top
                for ((x, y, d) in listOf(Triple(0.32f, 0.18f, 0.2f), Triple(0.5f, 0.12f, 0.24f), Triple(0.68f, 0.18f, 0.2f), Triple(0.41f, 0.24f, 0.18f), Triple(0.6f, 0.24f, 0.18f)))
                    circle(hex(0x6CC24A), x, y, d)
                // stalks
                for ((x, w, c) in listOf(Triple(0.3f, 0.16f, hex(0x9FD87C)), Triple(0.7f, 0.16f, hex(0x9FD87C))))
                    rrect(Brush.horizontalGradient(listOf(c, hex(0x7FC25B)), (x - w / 2) * s, (x + w / 2) * s), x, 0.62f, w, 0.72f, 0.08f)
                rrect(Brush.horizontalGradient(listOf(hex(0xB5E59A), hex(0x8CCB68)), 0.33f * s, 0.67f * s), 0.5f, 0.6f, 0.34f, 0.78f, 0.12f)
                for (x in listOf(0.42f, 0.58f)) line(hex(0x7DBB5A).copy(alpha = 0.6f), 0.008f) { m(x, 0.3f); l(x, 0.95f) }
            }
            Produce.Cucumber -> {
                rotated(-28f, 0.5f, 0.5f) {
                    rrect(vertical(listOf(hex(0x6FC05A), hex(0x2F7D33)), 0.34f, 0.66f), 0.5f, 0.5f, 0.9f, 0.4f, 0.2f)
                    for (y in listOf(0.42f, 0.5f, 0.58f)) line(hex(0x8ED67A).copy(alpha = 0.5f), 0.012f) { m(0.12f, y); l(0.88f, y) }
                    for (i in 0 until 9) circle(hex(0x2A6A2C).copy(alpha = 0.5f), 0.14f + i * 0.09f, if (i % 2 == 0) 0.35f else 0.65f, 0.018f)
                    rrect(hex(0xC9E07A), 0.06f, 0.5f, 0.04f, 0.12f, 0.02f)
                }
            }
            Produce.Eggplant -> {
                path(radial(listOf(hex(0x9A6AD1), hex(0x4A2470)), 0.4f, 0.55f, 0.55f)) {
                    m(0.5f, 0.22f)
                    c(0.64f, 0.22f, 0.66f, 0.38f, 0.74f, 0.5f)
                    c(0.86f, 0.66f, 0.84f, 0.95f, 0.5f, 0.95f)
                    c(0.16f, 0.95f, 0.14f, 0.66f, 0.26f, 0.5f)
                    c(0.34f, 0.38f, 0.36f, 0.22f, 0.5f, 0.22f)
                    close()
                }
                shine(0.32f, 0.6f, 0.06f, 0.18f, 10f)
                for (i in -2..2) leaf(0.5f + i * 0.06f, 0.25f, 0.1f, 0.16f, i * 22f, hex(0x4E9A47))
                rrect(hex(0x3F7F3A), 0.5f, 0.12f, 0.05f, 0.14f, 0.025f)
            }
            Produce.Carrot -> {
                for (i in -1..1) leaf(0.5f + i * 0.07f, 0.13f, 0.08f, 0.22f, i * 25f, hex(0x5CC46B))
                path(Brush.horizontalGradient(listOf(hex(0xFFA94D), hex(0xF07A1A)), 0.24f * s, 0.76f * s)) {
                    m(0.24f, 0.3f)
                    q(0.24f, 0.22f, 0.36f, 0.22f)
                    l(0.64f, 0.22f)
                    q(0.76f, 0.22f, 0.76f, 0.3f)
                    q(0.66f, 0.7f, 0.52f, 0.96f)
                    q(0.5f, 0.99f, 0.48f, 0.96f)
                    q(0.34f, 0.7f, 0.24f, 0.3f)
                    close()
                }
                for ((y, x0, x1) in listOf(Triple(0.62f, 0.33f, 0.43f), Triple(0.7f, 0.58f, 0.66f), Triple(0.8f, 0.43f, 0.5f)))
                    line(hex(0xD9661A).copy(alpha = 0.7f), 0.012f) { m(x0, y); l(x1, y) }
            }
            Produce.Apple -> {
                path(radial(listOf(hex(0xFF6A72), hex(0xD32A36)), 0.36f, 0.4f, 0.62f)) {
                    m(0.5f, 0.3f)
                    c(0.62f, 0.2f, 0.92f, 0.22f, 0.92f, 0.52f)
                    c(0.92f, 0.8f, 0.7f, 0.95f, 0.58f, 0.93f)
                    c(0.54f, 0.92f, 0.46f, 0.92f, 0.42f, 0.93f)
                    c(0.3f, 0.95f, 0.08f, 0.8f, 0.08f, 0.52f)
                    c(0.08f, 0.22f, 0.38f, 0.2f, 0.5f, 0.3f)
                    close()
                }
                shine(0.24f, 0.42f, 0.12f, 0.06f)
                line(hex(0x7A4A2A), 0.03f) { m(0.5f, 0.31f); q(0.5f, 0.2f, 0.55f, 0.13f) }
                leaf(0.63f, 0.17f, 0.17f, 0.08f, -25f)
            }
            Produce.Orange -> {
                oval(radial(listOf(hex(0xFFC56B), hex(0xFF8A1F)), 0.32f, 0.38f, 0.75f), 0.5f, 0.55f, 0.84f, 0.84f)
                for (i in 0 until 24) {
                    val a = i * 2.4; val r = 0.15 + (i % 5) * 0.06
                    circle(hex(0xE87A12).copy(alpha = 0.35f), (0.5 + r * cos(a)).toFloat(), (0.55 + r * sin(a)).toFloat(), 0.012f)
                }
                shine(0.3f, 0.36f, 0.14f, 0.07f)
                circle(hex(0x8A5A3A), 0.5f, 0.14f, 0.04f)
                leaf(0.6f, 0.12f, 0.2f, 0.09f, -20f)
            }
            Produce.Strawberry -> {
                path(vertical(listOf(hex(0xFF6B7F), hex(0xE8344E)), 0.15f, 0.97f)) {
                    m(0.5f, 0.97f)
                    c(0.2f, 0.84f, 0.06f, 0.58f, 0.06f, 0.38f)
                    c(0.06f, 0.16f, 0.3f, 0.18f, 0.5f, 0.22f)
                    c(0.7f, 0.18f, 0.94f, 0.16f, 0.94f, 0.38f)
                    c(0.94f, 0.58f, 0.8f, 0.84f, 0.5f, 0.97f)
                    close()
                }
                for ((x, y) in listOf(0.2f to 0.4f, 0.8f to 0.4f, 0.22f to 0.6f, 0.78f to 0.6f, 0.33f to 0.8f, 0.67f to 0.8f, 0.5f to 0.9f, 0.35f to 0.3f, 0.65f to 0.3f))
                    oval(hex(0xFFE38A), x, y, 0.025f, 0.04f)
                for (i in -2..2) leaf(0.5f + i * 0.07f, 0.2f, 0.09f, 0.2f, i * 35f, hex(0x5CC46B))
            }
            Produce.Kiwi -> {
                circle(hex(0x9B7448), 0.5f, 0.5f, 0.9f)
                oval(radial(listOf(hex(0xD6F28F), hex(0x6CC13F)), 0.5f, 0.5f, 0.42f), 0.5f, 0.5f, 0.8f, 0.8f)
                oval(hex(0xF4F7DC), 0.5f, 0.5f, 0.42f, 0.42f)
                for (i in 0 until 16) {
                    val a = i / 16.0 * 2 * PI
                    val x = (0.5 + 0.27 * cos(a)).toFloat(); val y = (0.5 + 0.27 * sin(a)).toFloat()
                    rotated(Math.toDegrees(a + PI / 2).toFloat(), x, y) { oval(hex(0x2B2B2B), x, y, 0.02f, 0.045f) }
                }
            }
            Produce.Pear -> {
                path(radial(listOf(hex(0xE6EE8A), hex(0xA9C23E)), 0.4f, 0.55f, 0.6f)) {
                    m(0.5f, 0.18f)
                    c(0.62f, 0.18f, 0.64f, 0.36f, 0.72f, 0.46f)
                    c(0.9f, 0.62f, 0.88f, 0.96f, 0.5f, 0.96f)
                    c(0.12f, 0.96f, 0.1f, 0.62f, 0.28f, 0.46f)
                    c(0.36f, 0.36f, 0.38f, 0.18f, 0.5f, 0.18f)
                    close()
                }
                shine(0.32f, 0.58f, 0.06f, 0.16f, 15f)
                line(hex(0x7A4A2A), 0.03f) { m(0.5f, 0.19f); q(0.5f, 0.1f, 0.54f, 0.06f) }
                leaf(0.62f, 0.1f, 0.16f, 0.07f, -25f)
            }
            Produce.Pumpkin -> {
                val body = listOf(hex(0xFFAA4A), hex(0xE8701A))
                for ((x, w) in listOf(0.22f to 0.4f, 0.78f to 0.4f, 0.35f to 0.44f, 0.65f to 0.44f, 0.5f to 0.46f))
                    oval(radial(body, x - 0.08f, 0.45f, 0.4f), x, 0.58f, w, 0.72f)
                for (x in listOf(0.35f, 0.65f)) line(hex(0xD9661A).copy(alpha = 0.5f), 0.01f) { m(x, 0.28f); q(x + (x - 0.5f) * 0.4f, 0.58f, x, 0.9f) }
                rrect(hex(0x6B8E3A), 0.5f, 0.17f, 0.07f, 0.14f, 0.03f)
                leaf(0.62f, 0.2f, 0.18f, 0.08f, 15f)
            }
        }
    }
}

/** Little round thumbnail size helper for pickers. */
fun DrawScope.drawProduceThumb(p: Produce) = drawProduce(p, min(size.width, size.height))
