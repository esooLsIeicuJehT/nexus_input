package com.example.ai.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.AiHudCandidate
import com.example.model.GameGenre
import com.example.model.HudElementCategory
import kotlin.math.abs
import kotlin.math.max

/** Pixel-derived contrast proposals, not semantic recognition or a calibrated probability. */
object AiHudDetector {
    data class Detection(val candidates: List<AiHudCandidate>, val error: String? = null)

    fun detect(bitmap: Bitmap?): Detection {
        if (bitmap == null) return Detection(emptyList(), "Import or capture a screenshot first.")
        if (bitmap.isRecycled || bitmap.width < 3 || bitmap.height < 3) {
            return Detection(emptyList(), "Screenshot is unreadable or too small.")
        }
        return try {
            val step = max(1, max(bitmap.width, bitmap.height) / 320)
            val w = (bitmap.width - 1) / step
            val h = (bitmap.height - 1) / step
            val edges = BooleanArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                fun luminance(px: Int, py: Int): Float {
                    val color = bitmap.getPixel(px, py)
                    return Color.red(color) * .299f + Color.green(color) * .587f + Color.blue(color) * .114f
                }
                val px = x * step
                val py = y * step
                val center = luminance(px, py)
                edges[y * w + x] = abs(center - luminance(px + step, py)) > 30f ||
                    abs(center - luminance(px, py + step)) > 30f
            }
            val proposals = mutableListOf<AiHudCandidate>()
            val visited = BooleanArray(edges.size)
            val queue = java.util.ArrayDeque<Int>()
            for (start in edges.indices) {
                if (!edges[start] || visited[start]) continue
                queue.add(start)
                visited[start] = true
                var left = w; var top = h; var right = 0; var bottom = 0; var count = 0
                while (queue.isNotEmpty()) {
                    val index = queue.removeFirst()
                    val x = index % w; val y = index / w
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y); count++
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val next = ny * w + nx
                        if (edges[next] && !visited[next]) { visited[next] = true; queue.add(next) }
                    }
                }
                val bw = right - left + 1; val bh = bottom - top + 1
                if (count < 8 || bw < 3 || bh < 3 || bw > w * .4f || bh > h * .4f) continue
                proposals += AiHudCandidate(
                    id = "pixels_${left}_${top}_${right}_${bottom}",
                    xNorm = (left + right + 1f) * step / (2f * bitmap.width),
                    yNorm = (top + bottom + 1f) * step / (2f * bitmap.height),
                    widthNorm = bw.toFloat() * step / bitmap.width,
                    heightNorm = bh.toFloat() * step / bitmap.height,
                    confidence = count.toFloat() / (bw * bh),
                    predictedAction = "Contrast region (assign action)",
                    recommendedKey = "",
                    category = HudElementCategory.ACTION
                )
            }
            Detection(proposals.take(32), if (proposals.isEmpty()) "No bounded contrast regions found. Place controls manually." else null)
        } catch (error: Exception) {
            Detection(emptyList(), "Screenshot analysis failed: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    // Preserve call compatibility; genre never manufactures detection output.
    @Suppress("UNUSED_PARAMETER")
    fun detectHudElements(bitmap: Bitmap?, genre: GameGenre = GameGenre.FPS,
                          screenWidth: Int = 2400, screenHeight: Int = 1080): List<AiHudCandidate> = detect(bitmap).candidates
}
