package dev.aaa1115910.bv.entity

enum class VideoSource {
    Ugc,
    Pgc,
    Cheese;

    val isUgc: Boolean
        get() = this == Ugc

    val isPgc: Boolean
        get() = this == Pgc

    val isCheese: Boolean
        get() = this == Cheese
}
