package dev.aaa1115910.biliapi.http.entity.video

import kotlinx.serialization.Serializable

@Serializable
data class OneClickTripleAction(
    val like: Boolean,
    val coin: Boolean,
    val fav:  Boolean
)