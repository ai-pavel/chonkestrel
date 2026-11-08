package xyz.znix.xftl.weapon

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import xyz.znix.xftl.BlueprintManager
import xyz.znix.xftl.Datafile
import xyz.znix.xftl.ShipShape
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.modding.SlipstreamDirectoryMod
import xyz.znix.xftl.math.ConstPoint
import xyz.znix.xftl.sector.Mirage
import xyz.znix.xftl.testkit.InGameTest
import xyz.znix.xftl.weapons.AbstractWeaponBlueprint
import xyz.znix.xftl.weapons.BeamBlueprint
import xyz.znix.xftl.weapons.BombBlueprint
import xyz.znix.xftl.weapons.AbstractWeaponProjectile
import xyz.znix.xftl.weapons.IRoomTargetingWeapon
import xyz.znix.xftl.weapons.WeaponSizes

/**
 * Check the combined weapons (see [WeaponSizes]): two or three of a weapon
 * in one slot.
 */
class CombinedWeaponTest {
    private fun kit() = InGameTest(extraBlueprints = listOf(BLUEPRINT_XML))

    @Test
    fun combinedVersionsAreMade() {
        val kit = kit()
        val double = kit.state.blueprintManager[WeaponSizes.nameFor("COMBO_LASER", 2)] as AbstractWeaponBlueprint
        val triple = kit.state.blueprintManager[WeaponSizes.nameFor("COMBO_LASER", 3)] as AbstractWeaponBlueprint

        assertEquals(2, double.combinedCount)
        assertEquals(listOf("COMBO_LASER", "COMBO_LASER"), double.combinedOf)
        assertEquals(3, triple.combinedCount)

        // Dearer, slower, and more power, but not twice or three times as much
        assertEquals(66, double.cost)
        assertEquals(10f * 1.25f, double.chargeTime, 0.001f)
        assertEquals(3, double.power)
        assertEquals(4, triple.power)
        assertEquals(2, double.shots)

        // A beam gets through another layer of shields for each extra copy
        val beam2 = kit.state.blueprintManager[WeaponSizes.nameFor("COMBO_BEAM", 2)] as AbstractWeaponBlueprint
        val beam3 = kit.state.blueprintManager[WeaponSizes.nameFor("COMBO_BEAM", 3)] as AbstractWeaponBlueprint
        assertEquals(1, beam2.shieldPiercing)
        assertEquals(2, beam3.shieldPiercing)
        assertEquals(0, double.shieldPiercing)

        // They aren't in the loot pool themselves
        assertEquals(0, double.rarity)
        assertEquals(0, triple.rarity)
    }

    @Test
    fun onlyWeaponsWithRaritiesAreCombined() {
        val kit = kit()
        // TEST_LASER has no rarity (as a vanilla weapon would)
        assertNull(kit.state.blueprintManager.getOrNull(WeaponSizes.nameFor("TEST_LASER", 2)))
        // and a combined weapon isn't combined again
        assertNull(kit.state.blueprintManager.getOrNull(WeaponSizes.nameFor(WeaponSizes.nameFor("COMBO_LASER", 2), 2)))
    }

    @Test
    fun eachCopyFiresAtItsOwnTarget() {
        val kit = kit()
        kit.givePlayerBP(WeaponSizes.nameFor("COMBO_LASER", 2))
        kit.upgradeAll()
        kit.powerUpAll()

        val weapon = kit.player.hardpoints[0].weapon!!
        repeat(200) {
            if (!weapon.isCharged)
                kit.updateSingle(0.1f)
        }
        assertTrue(weapon.isCharged)

        // Copy 0 at the first room and copy 1 at the second
        val rooms = kit.player.rooms
        (weapon as IRoomTargetingWeapon).fireCombined { copy -> rooms[copy] }

        val fired = HashSet<AbstractWeaponProjectile>()
        repeat(100) {
            kit.updateSingle(0.02f)
            fired += kit.player.projectiles.filterIsInstance<AbstractWeaponProjectile>()
        }

        // Two shots from each copy
        assertEquals(4, fired.size)
        assertEquals(2, fired.count { it.target == rooms[0] })
        assertEquals(2, fired.count { it.target == rooms[1] })
    }

    @Test
    fun vanillaWeaponsAreCombinedButNotAdvancedEditionOnes() {
        val blueprints = BlueprintManager(Datafile(VanillaDatafile.createWithDefaultPath(), emptyList()), true)
        fun combined(name: String, size: Int) = blueprints.getOrNull(WeaponSizes.nameFor(name, size))

        // The vanilla weapons, of every kind, including the ones that are
        // only the enemies' (with a rarity of 0), like the Burst Laser II
        for (name in listOf("LASER_BURST_1", "LASER_BURST_2", "MISSILES_1", "BEAM_1", "ION_1", "BOMB_1", "CRYSTAL_1")) {
            assertNotNull(name, combined(name, 2))
            assertNotNull(name, combined(name, 3))
        }

        // Not the ones from Advanced Edition (for now), nor the drones'
        for (name in listOf("LASER_CHAINGUN", "SHOTGUN", "MISSILE_CHARGEGUN", "DRONE_LASER", "ARTILLERY_FED")) {
            assertNull(name, combined(name, 2))
        }
    }

    @Test
    fun noCombinedWeaponIsFromAdvancedEdition() {
        // With the Chonkestrel mod, as the game's played: every double and
        // triple weapon is made from a weapon of the original game, never
        // one first defined in Advanced Edition's files (such as the flak
        // guns, the chain lasers and the Vulcan)
        val df = Datafile(VanillaDatafile.createWithDefaultPath(), listOf(SlipstreamDirectoryMod(File("mods/chonkestrel").toPath())))
        val blueprints = BlueprintManager(df, true)
        val original = df.parseXML(df["data/blueprints.xml"]).rootElement
            .getChildren("weaponBlueprint").map { it.getAttributeValue("name") }.toSet()
        val advancedEdition = listOf("data/dlcBlueprints.xml", "data/dlcBlueprintsOverwrite.xml")
            .flatMap { df.parseXML(df[it]).rootElement.getChildren("weaponBlueprint") }
            .map { it.getAttributeValue("name") }.filter { it !in original }.toSet()
        assertTrue(advancedEdition.containsAll(listOf("SHOTGUN", "SHOTGUN_2", "LASER_CHAINGUN", "LASER_CHAINGUN_2")))

        val combined = blueprints.blueprints.values.filterIsInstance<AbstractWeaponBlueprint>().filter { it.isCombined }
        assertTrue(combined.size > 50)
        for (weapon in combined) {
            val single = weapon.combinedOf.first()
            assertTrue("${weapon.name} is made from $single", single in original)
            assertTrue("${weapon.name} is made from $single", single !in advancedEdition)
        }
    }

    @Test
    fun aMiragesDisguisesKeepTheSize() {
        // In a mirage nebula, an enemy's weapon looks like a weapon of another
        // kind (see Mirage.weaponDisguise). A single one can look like any of
        // them, Advanced Edition's flak guns too, but a double or triple one
        // looks like another of the same size (never one of Advanced
        // Edition's, which aren't combined), so it isn't drawn as copies of
        // a single weapon, such as a twin flak gun
        val blueprints = BlueprintManager(Datafile(VanillaDatafile.createWithDefaultPath(), emptyList()), true)
        val weapons = blueprints.blueprints.values.filterIsInstance<AbstractWeaponBlueprint>()
            .filter { (it.rarity ?: 0) > 0 }
        val laser = blueprints["LASER_BURST_3"] as AbstractWeaponBlueprint

        val single = Mirage.disguisesFor(laser, weapons, blueprints)
        assertTrue(single.any { it.name == "SHOTGUN" })
        assertTrue(single.none { it.isCombined || Mirage.weaponKind(it) == "laser" })

        for (size in WeaponSizes.SIZES) {
            val combined = WeaponSizes.combined(blueprints, laser, size)!!
            val disguises = Mirage.disguisesFor(combined, weapons, blueprints)
            assertTrue(disguises.size > 10)
            for (disguise in disguises) {
                assertEquals(disguise.name, size, disguise.combinedCount)
                assertTrue(disguise.name, Mirage.weaponKind(disguise) != "laser")
            }
            assertTrue(disguises.none { it.combinedOf.first().startsWith("SHOTGUN") || it.combinedOf.first().contains("CHAINGUN") })
        }
    }

    // The weapons whose triple one is drawn as a triangle (see
    // tools/weapon_sizes.py)
    private val TRIANGLE = setOf(
        "ion_1", "ion_2", "ion_4", "laser_heavy_1", "laser_heavy_2", "laser_hull", "missiles_1", "missiles_2"
    )

    @Test
    fun everyCombinedWeaponHasItsOwnImage() {
        // Made by tools/weapon_sizes.py: an animation for each size of each
        // weapon that's combined, with a barrel for each copy (but one for
        // the beams and bomb launchers, and the triple weapons drawn as a
        // triangle, whose shots all come out of the one in front, see
        // TRIANGLE there)
        val animations = File("mods/chonkestrel/data/animations.xml.append").readText()
        val df = Datafile(VanillaDatafile.createWithDefaultPath(), emptyList())
        val blueprints = BlueprintManager(df, true)
        // (only those whose single weapon has an animation of its own: the
        // crystal shard doesn't, so it's drawn as copies)
        val vanillaAnims = df.parseXML(df["data/animations.xml"]).rootElement.getChildren("weaponAnim")
            .map { it.getAttributeValue("name") }.toSet()
        val combined = blueprints.blueprints.values.filterIsInstance<AbstractWeaponBlueprint>()
            .filter { it.isCombined && it.launcher in vanillaAnims }
        assertTrue(combined.isNotEmpty())
        for (weapon in combined) {
            val art = weapon.combinedArt!!
            val anim = Regex("""<weaponAnim name="$art">(.*?)</weaponAnim>""", RegexOption.DOT_MATCHES_ALL)
                .find(animations)?.groupValues?.get(1)
            assertNotNull("${weapon.name}: $art", anim)
            val barrels = Regex("""<xftlBarrels>([^<]*)</xftlBarrels>""").find(anim!!)!!.groupValues[1].split(",")
            val triangle = weapon.combinedCount == 3 && weapon.launcher in TRIANGLE
            val single = weapon is BeamBlueprint || weapon is BombBlueprint || triangle
            assertEquals(weapon.name, if (single) 1 else weapon.combinedCount, barrels.size)

            // A triangle charges copy by copy, so it has more charging frames
            // than the single weapon, and none of the single one's glow
            if (triangle) {
                val charged = Regex("""<chargedFrame>(\d+)""").find(anim)!!.groupValues[1].toInt()
                assertTrue(weapon.name, charged >= 3 * 4)
                assertTrue(weapon.name, "<chargeImage>" !in anim)
            }
        }
    }

    @Test
    fun doublesTurnUpThroughTheGame() {
        // From the start, more and more through the game, and more often on
        // the bigger ships
        val normal = (0..7).map { WeaponSizes.doubleChance(ShipShape.NORMAL, it) }
        val big = (0..7).map { WeaponSizes.doubleChance(ShipShape.WIDE, it) }
        assertTrue(normal.first() > 0f)
        assertEquals(normal, normal.sorted())
        assertTrue(normal.indices.all { big[it] > normal[it] })
        assertEquals(0.3f, normal.last(), 0.001f)
        assertEquals(0.6f, big.last(), 0.001f)
    }

    @Test
    fun copiesAreDrawnSideBySide() {
        assertEquals(listOf(ConstPoint.ZERO), WeaponSizes.copyOffsets(1, 20))

        // Three are a triangle: the middle one's drawn last, in front, and
        // the two behind it are set back
        val two = WeaponSizes.copyOffsets(2, 20)
        val three = WeaponSizes.copyOffsets(3, 20)
        assertEquals(3, three.size)
        assertEquals(ConstPoint.ZERO, three.last())
        assertTrue(three.take(2).all { it.y > 0 && it.x != 0 })

        // Neither takes up more than 60% of a weapon's width more than one,
        // so they fit in the stores' and equipment's boxes
        val width = { offsets: List<ConstPoint> -> offsets.maxOf { it.x } - offsets.minOf { it.x } }
        assertTrue(width(two) <= 12)
        assertTrue(width(three) <= 12)

        // A beam reaches on through the other windows, however many ships
        // there are: each extra copy adds the beam's length, or the distance
        // to the next window, whichever's longer
        assertEquals(100 + 2 * 400, WeaponSizes.beamLength(100, 3))
        assertEquals(100 + 400, WeaponSizes.beamLength(100, 2))
        assertEquals(3 * 500, WeaponSizes.beamLength(500, 3))
    }

    companion object {
        @Language("XML")
        private const val BLUEPRINT_XML = """
<FTL>
    <weaponBlueprint name="COMBO_LASER">
        <type>LASER</type>
        <weaponArt>generic_test_weapon</weaponArt>
        <iconImage>dummy_anim</iconImage>
        <image>dummy_anim</image>
        <shots>2</shots>
        <damage>1</damage>
        <power>2</power>
        <cooldown>10</cooldown>
        <cost>30</cost>
        <rarity>2</rarity>
    </weaponBlueprint>
    <weaponBlueprint name="COMBO_BEAM">
        <type>BEAM</type>
        <weaponArt>generic_test_weapon</weaponArt>
        <iconImage>dummy_anim</iconImage>
        <image>dummy_anim</image>
        <damage>2</damage>
        <length>60</length>
        <power>3</power>
        <cooldown>10</cooldown>
        <cost>60</cost>
        <rarity>2</rarity>
    </weaponBlueprint>
</FTL>
"""
    }
}
