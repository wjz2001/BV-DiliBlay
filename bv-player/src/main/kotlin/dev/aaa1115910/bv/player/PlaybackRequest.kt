package dev.aaa1115910.bv.player

sealed interface PlaybackSource {
    data class Single(
        val url: String
    ) : PlaybackSource

    data class SeparateVideoAudio(
        val videoUrl: String,
        val audioUrl: String?
    ) : PlaybackSource
}

data class PlaybackRequest(
    val source: PlaybackSource,
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L
)
