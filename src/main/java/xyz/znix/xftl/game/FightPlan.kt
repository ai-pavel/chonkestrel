package xyz.znix.xftl.game

import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.ShipShape.LONG
import xyz.znix.xftl.ShipShape.WIDE
import kotlin.random.Random

/**
 * How many enemy ships a fight has, and the [ShipShape] of each: the main
 * enemy ship first, then its wingmen (see [Opponent]).
 *
 * Bigger player ships can fight bigger versions of the vanilla ships (size
 * 2, long or wide, see [ShipBlueprint.variantOf]), more and bigger ones
 * through the game (see [forSector]). The ships have to fit on
 * the screen: their windows take up at most [MAX_SLOTS] places in the row,
 * and a wide ship takes up two. So a fight is one of 1, 1+1, 1+1+1, 2, 2+1,
 * 2+2 (both long, or a long and a wide one), or with three ships, only long
 * 2s: 2+1+1, 2+2+1 or 2+2+2. A wide ship can take the place of two ships.
 */
class FightPlan(val shapes: List<ShipShape>) {
    val size: Int get() = shapes.size

    companion object {
        const val MAX_SLOTS = Opponent.MAX_ENEMY_SHIPS

        /**
         * The chance of each ship in a fight being a bigger version, when
         * that's allowed and fits.
         */
        const val BIG_SHIP_CHANCE = 0.3f

        /**
         * How strong the fights are (see [forSector]) in each sector, from
         * 1, at the start, middle and end of it: one ship, then 1+1 and a
         * long one, building up to three long ships after the blockade.
         */
        val STRENGTH_BY_SECTOR: List<IntArray> = listOf(
            intArrayOf(1, 1, 1), // 1
            intArrayOf(1, 2, 2), // 2
            intArrayOf(2, 3, 3), // 3
            intArrayOf(3, 3, 4), // 4
            intArrayOf(3, 4, 4), // 5
            intArrayOf(4, 4, 5), // 6
            intArrayOf(4, 5, 5), // 7
            intArrayOf(5, 5, 5), // 8, the blockade
            intArrayOf(5, 5, 6), // 9
            intArrayOf(5, 5, 6), // 10
            intArrayOf(5, 5, 6), // 11
            intArrayOf(5, 5, 6), // 12
            intArrayOf(6, 6, 6), // 13 onwards
        )

        // The first sector with long ships (from the last third of it), and
        // with wide ones
        const val LONG_FROM_SECTOR = 2
        const val WIDE_FROM_SECTOR = 5

        /**
         * Every fight that fits on the screen, by its ships' shapes.
         */
        private val FIGHTS: List<List<ShipShape>> = listOf(
            listOf(ShipShape.NORMAL),
            listOf(ShipShape.NORMAL, ShipShape.NORMAL),
            listOf(ShipShape.LONG),
            listOf(ShipShape.NORMAL, ShipShape.NORMAL, ShipShape.NORMAL),
            listOf(ShipShape.LONG, ShipShape.NORMAL),
            listOf(ShipShape.WIDE),
            listOf(ShipShape.LONG, ShipShape.LONG),
            listOf(ShipShape.LONG, ShipShape.NORMAL, ShipShape.NORMAL),
            listOf(ShipShape.WIDE, ShipShape.NORMAL),
            listOf(ShipShape.LONG, ShipShape.LONG, ShipShape.NORMAL),
            listOf(ShipShape.WIDE, ShipShape.LONG),
            listOf(ShipShape.LONG, ShipShape.LONG, ShipShape.LONG),
        )

        /**
         * Plan a fight with [count] ships. Each can be a bigger one (long or
         * wide, at random) with [bigChance], if it still fits. A wide ship
         * that doesn't fit takes the place of one of the ships still to come
         * (it's worth three, see [ShipShape.strength]), so fights with three
         * ships can have one too; if it doesn't fit even then, it's a long
         * one.
         */
        @JvmStatic
        fun plan(count: Int, bigChance: Float, random: Random): FightPlan {
            val shapes = ArrayList<ShipShape>()
            var total = count
            while (shapes.size < total) {
                var shape = ShipShape.NORMAL
                if (random.nextFloat() < bigChance) {
                    shape = if (random.nextBoolean()) ShipShape.WIDE else ShipShape.LONG
                    if (shape == ShipShape.WIDE && !fits(shapes + shape, total)) {
                        if (total - 1 > shapes.size && fits(shapes + shape, total - 1))
                            total--
                        else
                            shape = ShipShape.LONG
                    }
                    if (!fits(shapes + shape, total))
                        shape = ShipShape.NORMAL
                }
                shapes += shape
            }
            return FightPlan(shapes)
        }

        /**
         * Plan a fight for a player ship of size 2 or more, which fights at
         * most [maxShips] ships at once, and at least [minShips] (its
         * [ShipBlueprint.opponentShipsAtStart]), in a [sector]
         * (numbered from 1 as in a sixteen-sector game, see
         * [xyz.znix.xftl.sector.GameMap.nominalSectorNumber]), [progress] of
         * the way across it (0 at the start beacon, 1 at the exit).
         *
         * The fight is as strong as [STRENGTH_BY_SECTOR] says for that part
         * of the sector (the sum of its ships' [ShipShape.strength]), and is
         * one of the fights of that strength that fit on the screen, at
         * random, with long ships from the end of sector 2
         * ([LONG_FROM_SECTOR]) and wide ones from sector 5
         * ([WIDE_FROM_SECTOR]). So the first sector is one ship at a time,
         * the second goes on to two, then one long one, and so on. See
         * doc/game/ship-shapes.md.
         */
        @JvmStatic
        fun forSector(maxShips: Int, minShips: Int, sector: Int, progress: Float, random: Random): FightPlan {
            val part = (progress.coerceIn(0f, 1f) * 3).toInt().coerceAtMost(2)
            val row = STRENGTH_BY_SECTOR[(sector - 1).coerceIn(0, STRENGTH_BY_SECTOR.size - 1)]
            val long = sector > LONG_FROM_SECTOR || (sector == LONG_FROM_SECTOR && part == 2)
            val wide = sector >= WIDE_FROM_SECTOR

            val usable = FIGHTS.filter { fight ->
                fight.size <= maxShips && (long || LONG !in fight) && (wide || WIDE !in fight)
            }
            // The strongest fights there are, up to the strength wanted
            var strength = maxOf(row[part], minShips)
            while (strength > 1 && usable.none { fight -> fight.sumOf { it.strength } == strength })
                strength--
            val fight = usable.filter { fight -> fight.sumOf { it.strength } == strength }.random(random)
            return FightPlan(fight.shuffled(random))
        }

        /**
         * True if these ships fit on the screen, with room for the rest of
         * the [count] ships (as normal ones) after them.
         */
        @JvmStatic
        fun fits(shapes: List<ShipShape>, count: Int = shapes.size): Boolean {
            val rest = (count - shapes.size).coerceAtLeast(0)
            return shapes.sumOf { it.slots } + rest <= MAX_SLOTS
        }
    }
}

/**
 * The extra rewards for a bigger ship (see [FightPlan]), which is worth two
 * (or for a wide one, three) of the ship it's a version of: as much again
 * as its event gave for each, and sometimes a weapon or an augment.
 */
object BigShipRewards {
    /**
     * The chance of a bigger ship's wreck having a weapon or augment, for
     * each ship it's worth beyond the first.
     */
    const val ITEM_CHANCE = 0.15f

    /**
     * The extra rewards for destroying (or killing the crew of) [ship],
     * shown after its [event]'s, or null if it isn't a bigger ship.
     */
    @JvmStatic
    fun salvage(game: InGameState, ship: xyz.znix.xftl.Ship, event: xyz.znix.xftl.sector.Event, random: Random): DialogueWindow.SyntheticEvent? {
        if (!ship.type.isVariant)
            return null

        // Just the resources and items, as the crew and the rest of what
        // the event does only happen once
        val salvage = DialogueWindow.SyntheticEvent(game.translator["xftl_big_ship_salvage"])
        val extra = ship.type.shape.strength - 1
        repeat(extra) {
            val again = event.resolveResources(game, random)
            salvage.resources.fuel += again.fuel
            salvage.resources.missiles += again.missiles
            salvage.resources.droneParts += again.droneParts
            salvage.resources.scrap += again.scrap
            salvage.resources.items += again.items
        }

        if (random.nextFloat() < ITEM_CHANCE * extra) {
            salvage.resources.items += when (random.nextBoolean()) {
                true -> game.lootPool.getWeapon(random)
                false -> game.lootPool.getAugment(random)
            }
        }
        return salvage
    }
}
