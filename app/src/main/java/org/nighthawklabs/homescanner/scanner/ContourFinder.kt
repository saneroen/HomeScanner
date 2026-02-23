package org.nighthawklabs.homescanner.scanner

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sqrt

data class Contour(val points: List<PointF>)

object ContourFinder {

    fun findContours(binary: IntArray, w: Int, h: Int): List<Contour> {
        val contours = mutableListOf<Contour>()
        val visited = BooleanArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (binary[idx] == 255 && !visited[idx]) {
                    val boundary = traceBoundary(binary, visited, w, h, x, y)
                    if (boundary.size >= 4) {
                        contours.add(Contour(boundary))
                    }
                }
            }
        }
        return contours
    }

    private fun traceBoundary(
        binary: IntArray,
        visited: BooleanArray,
        w: Int,
        h: Int,
        startX: Int,
        startY: Int
    ): List<PointF> {
        val boundary = mutableListOf<PointF>()
        val dirs = arrayOf(
            intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
            intArrayOf(0, 1), intArrayOf(-1, 1), intArrayOf(-1, 0), intArrayOf(-1, -1)
        )
        var x = startX
        var y = startY
        var dir = 0
        val maxSteps = w * h
        var steps = 0
        do {
            boundary.add(PointF(x.toFloat(), y.toFloat()))
            visited[y * w + x] = true
            var found = false
            for (i in 0 until 8) {
                val d = (dir + i) % 8
                val nx = x + dirs[d][0]
                val ny = y + dirs[d][1]
                if (nx in 0 until w && ny in 0 until h && binary[ny * w + nx] == 255) {
                    x = nx
                    y = ny
                    dir = (d + 5) % 8
                    found = true
                    break
                }
            }
            if (!found) break
            steps++
        } while ((x != startX || y != startY) && steps < maxSteps)
        return boundary
    }

    fun simplifyRDP(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size < 3) return points
        var maxDist = 0f
        var maxIdx = 0
        val first = points.first()
        val last = points.last()
        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], first, last)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }
        if (maxDist < epsilon) return listOf(first, last)
        val left = simplifyRDP(points.subList(0, maxIdx + 1), epsilon)
        val right = simplifyRDP(points.subList(maxIdx, points.size), epsilon)
        return left.dropLast(1) + right
    }

    private fun perpendicularDistance(p: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len == 0f) return hypot((p.x - lineStart.x).toDouble(), (p.y - lineStart.y).toDouble()).toFloat()
        val t = ((p.x - lineStart.x) * dx + (p.y - lineStart.y) * dy) / (len * len)
        val projX = lineStart.x + t * dx
        val projY = lineStart.y + t * dy
        return hypot((p.x - projX).toDouble(), (p.y - projY).toDouble()).toFloat()
    }

    fun approximateQuad(contour: Contour): List<PointF>? {
        val simplified = simplifyRDP(contour.points, 2f)
        if (simplified.size < 4) return null
        if (simplified.size == 4) return orderCorners(simplified)
        if (simplified.size <= 8) {
            val quad = selectBest4Points(simplified)
            return quad?.let { orderCorners(it) }
        }
        val sampled = sampleToN(simplified, 8)
        return selectBest4Points(sampled)?.let { orderCorners(it) }
    }

    private fun sampleToN(points: List<PointF>, n: Int): List<PointF> {
        if (points.size <= n) return points
        val step = (points.size - 1).toFloat() / (n - 1)
        return (0 until n).map { i ->
            val idx = (i * step).toInt().coerceIn(0, points.size - 1)
            points[idx]
        }
    }

    private fun selectBest4Points(points: List<PointF>): List<PointF>? {
        if (points.size < 4) return null
        var bestQuad: List<PointF>? = null
        var bestScore = -1.0
        val indices = points.indices.toList()
        for (a in indices) {
            for (b in indices) {
                if (b == a) continue
                for (c in indices) {
                    if (c == a || c == b) continue
                    for (d in indices) {
                        if (d == a || d == b || d == c) continue
                        val quad = listOf(points[a], points[b], points[c], points[d])
                        val ordered = orderCorners(quad)
                        if (ordered != null) {
                            val area = polygonArea(ordered)
                            if (area > 100) {
                                val rectScore = rectangularityScore(ordered)
                                val score = area * rectScore
                                if (score > bestScore) {
                                    bestScore = score
                                    bestQuad = ordered
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestQuad
    }

    internal fun orderCorners(points: List<PointF>): List<PointF>? {
        if (points.size != 4) return null
        val cx = points.map { it.x }.average().toFloat()
        val cy = points.map { it.y }.average().toFloat()
        val sorted = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        return sorted
    }

    fun polygonArea(points: List<PointF>): Double {
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return kotlin.math.abs(area) / 2
    }

    private fun rectangularityScore(points: List<PointF>): Double {
        if (points.size != 4) return 0.0
        var score = 1.0
        for (i in 0..3) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val v1x = b.x - a.x
            val v1y = b.y - a.y
            val v2x = c.x - b.x
            val v2y = c.y - b.y
            val dot = (v1x * v2x + v1y * v2y).toDouble()
            val len1 = sqrt((v1x * v1x + v1y * v1y).toDouble())
            val len2 = sqrt((v2x * v2x + v2y * v2y).toDouble())
            if (len1 < 1e-6 || len2 < 1e-6) return 0.0
            val cos = (dot / (len1 * len2)).coerceIn(-1.0, 1.0)
            val angleDeg = kotlin.math.acos(cos) * 180 / Math.PI
            val dev = kotlin.math.abs(90 - angleDeg)
            score *= (1 - dev / 90).coerceIn(0.0, 1.0)
        }
        return score
    }
}
