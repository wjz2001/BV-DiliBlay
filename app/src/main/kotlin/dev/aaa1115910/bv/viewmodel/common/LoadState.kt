package dev.aaa1115910.bv.viewmodel.common

enum class LoadState {
    Idle,
    Loading,
    Success,
    Error
}

fun LoadState.canAutoLoad(): Boolean {
    return this == LoadState.Idle || this == LoadState.Error
}