package org.nighthawklabs.homescanner.scanner

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sqrt

data class AutoCaptureDecision(
    val shouldCapture: Boolean,
    val reason: String,
    val stableScore: Float,
    val blurScore: Float,
    val areaRatio: Float,
    val framesPresent: Int
)

class AutoCaptureController {

    private val window = ArrayDeque<FrameResult>(AutoCaptureTuning.WINDOW_N)
    private var lastCaptureAt = 0L

    fun onFrame(frame: FrameResult): AutoCaptureDecision {
        val now = System.currentTimeMillis()
        if (now - lastCaptureAt < AutoCaptureTuning.COOLDOWN_MS) {
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "Cooldown",
                stableScore = 0f,
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = window.size
            )
        }

        if (frame.corners.size != 4) {
            window.clear()
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "No quad",
                stableScore = 0f,
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = 0
            )
        }

        window.addLast(frame)
        if (window.size > AutoCaptureTuning.WINDOW_N) {
            window.removeFirst()
        }

        val framesPresent = window.size
        if (framesPresent < AutoCaptureTuning.MIN_PRESENT) {
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "Hold steady",
                stableScore = stabilityScore(),
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = framesPresent
            )
        }

        if (frame.areaRatio < AutoCaptureTuning.MIN_AREA_RATIO) {
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "Move closer",
                stableScore = stabilityScore(),
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = framesPresent
            )
        }

        if (frame.blurScore < AutoCaptureTuning.MIN_BLUR_SCORE) {
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "Hold steady",
                stableScore = stabilityScore(),
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = framesPresent
            )
        }

        val stable = stabilityScore()
        if (stable < 0f || averageCornerJitter() > AutoCaptureTuning.MAX_AVG_CORNER_JITTER) {
            return AutoCaptureDecision(
                shouldCapture = false,
                reason = "Hold steady",
                stableScore = stable,
                blurScore = frame.blurScore,
                areaRatio = frame.areaRatio,
                framesPresent = framesPresent
            )
        }

        lastCaptureAt = now
        return AutoCaptureDecision(
            shouldCapture = true,
            reason = "Captured",
            stableScore = stable,
            blurScore = frame.blurScore,
            areaRatio = frame.areaRatio,
            framesPresent = framesPresent
        )
    }

    private fun stabilityScore(): Float {
        if (window.size < 2) return 1f
        val jitter = averageCornerJitter()
        return (1f - jitter / AutoCaptureTuning.MAX_AVG_CORNER_JITTER).coerceIn(0f, 1f)
    }

    private fun averageCornerJitter(): Float {
        if (window.size < 2) return 0f
        var totalDist = 0f
        var count = 0
        var prev = window.first().corners
        for (i in 1 until window.size) {
            val curr = window.elementAt(i).corners
            if (prev.size != 4 || curr.size != 4) continue
            for (j in 0..3) {
                totalDist += hypot(
                    (curr[j].x - prev[j].x).toDouble(),
                    (curr[j].y - prev[j].y).toDouble()
                ).toFloat()
            }
            count += 4
            prev = curr
        }
        return if (count > 0) totalDist / count else 0f
    }

    fun reset() {
        window.clear()
    }

    fun markCaptured() {
        lastCaptureAt = System.currentTimeMillis()
    }
}
