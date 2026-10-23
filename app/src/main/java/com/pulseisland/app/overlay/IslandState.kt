package com.pulseisland.app.overlay

/**
 * Represents the visual state of the floating island pill.
 */
enum class IslandState {
    /**
     * Compact idle pill near top center of the screen.
     */
    COLLAPSED,

    /**
     * Expanded card showing PulseIsland name, active status, and quick stop button.
     */
    EXPANDED
}
