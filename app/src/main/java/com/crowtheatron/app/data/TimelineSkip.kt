package com.crowtheatron.app.data

/** A segment of the video timeline to be automatically skipped during playback. */
data class TimelineSkip(
    val id: Long = 0L,
    val videoId: Long,
    val startMs: Long,
    val endMs: Long,
    val label: String = "Skip",
    val createdAt: Long = System.currentTimeMillis()
)
