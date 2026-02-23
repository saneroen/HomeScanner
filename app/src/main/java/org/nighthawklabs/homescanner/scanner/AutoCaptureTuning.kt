package org.nighthawklabs.homescanner.scanner

object AutoCaptureTuning {
    const val WINDOW_N = 12
    const val MIN_PRESENT = 10
    const val MAX_AVG_CORNER_JITTER = 3.0f
    const val MIN_AREA_RATIO = 0.20f
    /** Gradient energy threshold - higher = sharper required. Tune for device. */
    const val MIN_BLUR_SCORE = 6_000f
    const val COOLDOWN_MS = 1500L
}
