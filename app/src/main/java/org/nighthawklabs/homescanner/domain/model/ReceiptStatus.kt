package org.nighthawklabs.homescanner.domain.model

enum class ReceiptStatus {
    CAPTURING,
    CAPTURED_RAW,
    PROCESSING_IMAGE,
    IMAGE_READY,
    PARSING,
    NEEDS_REVIEW,
    CONFIRMED,
    FAILED
}
