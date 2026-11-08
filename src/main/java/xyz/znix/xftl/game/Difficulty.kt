package xyz.znix.xftl.game

enum class Difficulty {
    EASY,
    NORMAL,
    HARD;

    /**
     * Get the offset applied to the sector number when calculating
     * ship difficulty from the sector number.
     *
     * This is -1 on easy, which delays everything by a sector.
     */
    val sectorOffset: Int get() = if (this == EASY) -1 else 0

    /**
     * Get the offset applied to the sector number when calculating
     * scrap rewards: +1 on easy and -1 on hard, so rewards are bigger
     * on easy and smaller on hard.
     */
    val rewardSectorOffset: Int
        get() = when (this) {
            EASY -> 1
            NORMAL -> 0
            HARD -> -1
        }

    /**
     * Get the localisation key for the start button for this difficulty.
     */
    val startButtonKey: String
        get() = when (this) {
            EASY -> "easy_button"
            NORMAL -> "normal_button"
            HARD -> "hard_button"
        }
}
