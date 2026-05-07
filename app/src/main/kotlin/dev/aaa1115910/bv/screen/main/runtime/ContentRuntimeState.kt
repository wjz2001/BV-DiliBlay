package dev.aaa1115910.bv.screen.main.runtime

enum class ContentRuntimeState { NotCreated, Shell, Active, Frozen, Disposed }

enum class ActivationReason { FocusDebounced, Click, Restore, DrawerSwitch }

fun ContentRuntimeState.isActiveLike(): Boolean = this == ContentRuntimeState.Active
