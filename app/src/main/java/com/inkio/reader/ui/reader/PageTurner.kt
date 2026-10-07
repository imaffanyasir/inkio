package com.inkio.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.inkio.reader.data.PageAnim
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** An in-progress page turn. [dir] = +1 forward, -1 back. */
private data class Turn(val dir: Int, val cornerY: Float, val point: Offset, val t: Float)

/**
 * Shows the current page and turns pages like a paper book.
 * Tap the right / left edge or swipe to turn; tap the middle for the menu; double-tap to zoom.
 */
@Composable
fun PageTurner(
    index: Int,
    count: Int,
    anim: PageAnim,
    paper: Color,
    generation: Int,
    ghost: ImageBitmap?,
    loadPage: suspend (Int) -> ImageBitmap?,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    turnRequests: Flow<Int>,
    modifier: Modifier = Modifier,
) {
    val pages = remember { mutableStateMapOf<Int, ImageBitmap>() }
    var turn by remember { mutableStateOf<Turn?>(null) }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }
    val idx by rememberUpdatedState(index)
    val pageChanged by rememberUpdatedState(onPageChanged)
    val mode by rememberUpdatedState(anim)

    LaunchedEffect(generation) { pages.clear() }
    LaunchedEffect(index, generation, count) {
        for (i in listOf(index, index + 1, index - 1, index + 2)) {
            if (i !in 0 until count || pages.containsKey(i)) continue
            loadPage(i)?.let { pages[i] = it }
        }
        pages.keys.filter { it !in (index - 2)..(index + 2) }.forEach { pages.remove(it) }
    }

    var size by remember { mutableStateOf(Offset(1f, 1f)) }

    fun canTurn(dir: Int) = turn == null && (idx + dir) in 0 until count && pages.containsKey(idx + dir)

    fun finish(dir: Int) {
        pageChanged(idx + dir)
        turn = null
    }

    /** Animated turn triggered by a tap or volume key. */
    fun autoTurn(dir: Int) {
        if (!canTurn(dir)) {
            // page not ready yet: load it then turn
            if ((idx + dir) in 0 until count && turn == null) scope.launch {
                loadPage(idx + dir)?.let { pages[idx + dir] = it; autoTurnNow(dir, size, mode, { turn = it }, { finish(dir) }) }
            }
            return
        }
        animJob = scope.launch { autoTurnNow(dir, size, mode, { turn = it }, { finish(dir) }) }
    }

    LaunchedEffect(Unit) { turnRequests.collect { autoTurn(it) } }

    // drag gesture state
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(0f) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(count) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.x
                        when {
                            pos.x > w * 0.68f -> autoTurn(1)
                            pos.x < w * 0.32f -> autoTurn(-1)
                            else -> onCenterTap()
                        }
                    },
                    onDoubleTap = { onDoubleTap(it) },
                )
            }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = { pos -> dragStart = pos; dragTotal = 0f },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragTotal += dx
                        val w = size.x; val h = size.y
                        var t = turn
                        if (t == null) {
                            val dir = if (dragTotal < 0) 1 else -1
                            if (!canTurn(dir) || animJob?.isActive == true) return@detectHorizontalDragGestures
                            val cy = if (dragStart.y > h * 0.5f) h else 0f
                            t = Turn(dir, cy, if (dir > 0) Offset(w, cy) else Offset(-w, cy), 0f)
                        }
                        val dy = (change.position.y - dragStart.y) * 0.5f
                        val prog = (abs(dragTotal) / w).coerceIn(0f, 1f)
                        val point = if (t.dir > 0) Offset(w + dragTotal * 1.6f, t.cornerY + dy)
                        else Offset(-w + dragTotal * 2f, t.cornerY + dy)
                        turn = t.copy(point = constrain(point, t.cornerY, w, h), t = prog)
                    },
                    onDragEnd = {
                        val t = turn ?: return@detectHorizontalDragGestures
                        val w = size.x
                        val complete = (t.dir > 0 && dragTotal < -w * 0.15f) || (t.dir < 0 && dragTotal > w * 0.15f)
                        animJob = scope.launch { settle(t, complete, size) { turn = it }; if (complete) finish(t.dir) else turn = null }
                    },
                    onDragCancel = {
                        val t = turn ?: return@detectHorizontalDragGestures
                        animJob = scope.launch { settle(t, false, size) { turn = it }; turn = null }
                    },
                )
            },
    ) {
        size = Offset(this.size.width, this.size.height)
        drawRect(paper)
        val cur = pages[index]?.asAndroidBitmap()
        val t = turn
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            if (t == null || cur == null) {
                cur?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            } else {
                val other = pages[index + t.dir]?.asAndroidBitmap()
                when (anim) {
                    PageAnim.CURL -> {
                        // forward: current page curls away revealing next.
                        // back: previous page curls back on top of current.
                        val top = if (t.dir > 0) cur else other
                        val under = if (t.dir > 0) other else cur
                        if (top != null) drawCurl(canvas, top, under, t.point, Offset(size.x, t.cornerY), paper.toArgb())
                        else canvas.drawBitmap(cur, 0f, 0f, null)
                    }
                    PageAnim.SLIDE -> {
                        val w = size.x
                        val off = t.t * w * -t.dir
                        canvas.drawBitmap(cur, off, 0f, null)
                        other?.let { canvas.drawBitmap(it, off + w * t.dir, 0f, null) }
                    }
                    PageAnim.FADE, PageAnim.NONE -> {
                        canvas.drawBitmap(cur, 0f, 0f, null)
                        other?.let { canvas.drawBitmap(it, 0f, 0f, Paint().apply { alpha = (t.t * 255).toInt() }) }
                    }
                }
            }
            if (ghost != null && t == null) {
                canvas.drawBitmap(ghost.asAndroidBitmap(), 0f, 0f, Paint().apply { alpha = 14 })
            }
        }
    }
}

// ---------------------------------------------------------------------------- animation helpers

private suspend fun autoTurnNow(dir: Int, size: Offset, anim: PageAnim, set: (Turn) -> Unit, done: () -> Unit) {
    val w = size.x; val h = size.y
    if (anim == PageAnim.NONE) { done(); return }
    val dur = if (anim == PageAnim.CURL) 520 else 300
    animate(0f, 1f, animationSpec = tween(dur, easing = FastOutSlowInEasing)) { v, _ ->
        // the corner travels in a gentle arc, as when a thumb flips a page
        val f = if (dir > 0) v else 1f - v
        val x = w - 2 * w * f
        val y = h - h * 0.16f * sin(PI * f).toFloat()
        set(Turn(dir, h, constrain(Offset(x, y), h, w, h), v))
    }
    done()
}

private suspend fun settle(t: Turn, complete: Boolean, size: Offset, set: (Turn) -> Unit) {
    val w = size.x
    val targetX = when {
        t.dir > 0 && complete -> -w
        t.dir > 0 -> w
        complete -> w
        else -> -w
    }
    val start = t.point
    val startT = t.t
    animate(0f, 1f, animationSpec = tween(320, easing = LinearOutSlowInEasing)) { v, _ ->
        val p = Offset(start.x + (targetX - start.x) * v, start.y + (t.cornerY - start.y) * v)
        set(t.copy(point = p, t = startT + ((if (complete) 1f else 0f) - startT) * v))
    }
}

/** Keeps the page attached to the spine: the corner can't move further than the page width from it. */
private fun constrain(p: Offset, cornerY: Float, w: Float, h: Float): Offset {
    val y = p.y.coerceIn(-h * 0.1f, h * 1.1f)
    val sx = 0f
    val dx = p.x - sx
    val dy = y - cornerY
    val dist = hypot(dx, dy)
    return if (dist > w) Offset(sx + dx * w / dist, cornerY + dy * w / dist) else Offset(p.x, y)
}

// ---------------------------------------------------------------------------- curl rendering

private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
private val backPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 34 }
private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

/**
 * Classic page curl: the fold line is the perpendicular bisector between the page
 * corner [corner] and the dragged point [p]. Everything past the fold is removed from
 * the top page (revealing [under]) and drawn mirrored on top as the page's back side.
 */
private fun drawCurl(
    canvas: android.graphics.Canvas,
    top: Bitmap,
    under: Bitmap?,
    p: Offset,
    corner: Offset,
    paperColor: Int,
) {
    val w = top.width.toFloat()
    val h = top.height.toFloat()
    val dX = corner.x - p.x
    val dY = corner.y - p.y
    val len = hypot(dX, dY)
    if (len < 1f) { canvas.drawBitmap(top, 0f, 0f, bitmapPaint); return }
    val mx = (p.x + corner.x) / 2f
    val my = (p.y + corner.y) / 2f
    val side = { x: Float, y: Float -> (x - mx) * dX + (y - my) * dY } // > 0 = folded part

    val rect = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))
    val keep = clip(rect) { -side(it.x, it.y) }
    val fold = clip(rect) { side(it.x, it.y) }

    // 1. page underneath
    if (under != null) canvas.drawBitmap(under, 0f, 0f, bitmapPaint) else canvas.drawColor(paperColor)

    // 2. shadow cast on the page underneath, along the fold
    if (fold.size >= 3) {
        val ux = dX / len; val uy = dY / len
        val sw = (len * 0.22f).coerceIn(8f, w * 0.12f)
        shadePaint.shader = LinearGradient(mx, my, mx + ux * sw, my + uy * sw, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.save(); canvas.clipPath(path(fold)); canvas.drawPaint(shadePaint); canvas.restore()
    }

    // 3. remaining flat part of the top page
    if (keep.size >= 3) {
        canvas.save(); canvas.clipPath(path(keep)); canvas.drawBitmap(top, 0f, 0f, bitmapPaint); canvas.restore()
    }

    // 4. back of the curled-over part (mirror image across the fold line)
    if (fold.size >= 3) {
        val ux = -dY / len; val uy = dX / len // fold line direction
        val a = 2 * ux * ux - 1; val b = 2 * ux * uy; val e = 2 * uy * uy - 1
        val m = Matrix().apply {
            setValues(floatArrayOf(a, b, mx - (a * mx + b * my), b, e, my - (b * mx + e * my), 0f, 0f, 1f))
        }
        val flap = Path(path(fold)).apply { transform(m) }

        // soft shadow of the flap onto the page below it
        edgePaint.color = 0x22000000; edgePaint.strokeWidth = 10f
        canvas.drawPath(flap, edgePaint)
        edgePaint.color = 0x18000000; edgePaint.strokeWidth = 4f
        canvas.drawPath(flap, edgePaint)

        canvas.save()
        canvas.clipPath(flap)
        canvas.drawColor(paperColor)
        canvas.save(); canvas.concat(m); canvas.drawBitmap(top, 0f, 0f, backPaint); canvas.restore()
        // curvature shading: darker right at the fold, lighter towards the flap edge
        val nx = -dX / len; val ny = -dY / len
        val sw = (len * 0.5f).coerceAtLeast(10f)
        shadePaint.shader = LinearGradient(
            mx, my, mx + nx * sw, my + ny * sw,
            intArrayOf(0x40000000, 0x10000000, 0x00FFFFFF, 0x18000000),
            floatArrayOf(0f, 0.2f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPaint(shadePaint)
        canvas.restore()
    }
}

/** Sutherland–Hodgman clip of a polygon against the half-plane where [f] >= 0. */
private fun clip(poly: List<PointF>, f: (PointF) -> Float): List<PointF> {
    val out = ArrayList<PointF>()
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[(i + 1) % poly.size]
        val fa = f(a); val fb = f(b)
        if (fa >= 0) out += a
        if ((fa >= 0) != (fb >= 0)) {
            val t = fa / (fa - fb)
            out += PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }
    return out
}

private fun path(pts: List<PointF>) = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    close()
}
