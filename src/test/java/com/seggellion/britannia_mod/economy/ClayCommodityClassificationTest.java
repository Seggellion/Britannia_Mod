package com.seggellion.britannia_mod.economy;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the mod tells the economy a clay ball is.
 *
 * <p>Rails matches a sell row on {@code (category, subcategory, item_name)} exactly and refuses
 * anything with no category at all, so an unmapped item is not merely cheap — it cannot be
 * offered. Clay was unmapped: Rails has priced {@code clay|raw|clay} since Housing Milestone 9
 * and no item in the mod could ever produce that identity.
 *
 * <p>The mapping table is what is checked here. {@code describeSaleItem} itself cannot run
 * without a loaded mod — it reads a registered data component on the way past — so the full
 * serializer output is pinned in {@code ManagedClayDepositGameTests}, where there is one.
 */
class ClayCommodityClassificationTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aClayBallDescribesItselfAsRawClay() {
        CommodityMapping clay = CommodityMappings.forStack(new ItemStack(Items.CLAY_BALL))
                .orElseThrow(() -> new AssertionError(
                        "a clay ball carries no commodity, so no trader could ever accept it"));
        assertEquals("clay", clay.category());
        assertEquals("raw", clay.subcategory());
        assertEquals("clay", clay.itemName(),
                "Rails resolves the commodity on item_name");
        assertEquals("clay|raw|clay", clay.normalizedKey());
    }

    /**
     * One ball, one unit.
     *
     * <p>{@code clay} is one of Rails' bulk categories, so the seeded row is weight-canonical and
     * {@code BuybackValuation} reads the posted weight whenever it is positive. Posting it makes
     * the sender state the relation rather than leaving the receiver to derive it from
     * {@code unit_weight}, and quote and settlement take this same path so they cannot disagree.
     */
    @Test
    void clayIsAWeightCanonicalCommodity() {
        assertEquals(CommodityUnit.WEIGHT,
                CommodityMappings.forStack(new ItemStack(Items.CLAY_BALL)).orElseThrow().unit(),
                "a count-canonical clay row would leave Rails to derive the weight itself");
    }

    /**
     * Clay is not stone.
     *
     * <p>The obvious shortcut was to hang clay off the stone trader's existing category, since
     * the mason is who buys it. Rails refused that on its own side — clay is dug and fired, not
     * quarried, and it carries its own category — and the mod must not undo the distinction by
     * describing the item differently from how the economy holds it.
     */
    @Test
    void clayIsNotStone() {
        assertFalse("stone".equals(
                CommodityMappings.forStack(new ItemStack(Items.CLAY_BALL)).orElseThrow().category()));
        assertTrue(CommodityMappings.stoneCommodityKey("clay").isEmpty(),
                "clay resolved as a stone commodity key");
    }

    /**
     * The block form is not the commodity either.
     *
     * <p>Only {@code minecraft:clay_ball} is mapped. A vanilla clay block, which is what world
     * generation makes, carries no commodity and cannot be sold even if somebody obtained one.
     */
    @Test
    void theVanillaClayBlockIsNotASaleableCommodity() {
        assertTrue(CommodityMappings.forStack(new ItemStack(Items.CLAY)).isEmpty(),
                "a vanilla clay block carries a commodity, so world generation is economic supply");
    }

    /**
     * Finished construction blocks stay finished.
     *
     * <p>Brick walls and ceramic roof tiles are what clay becomes, and Rails ships a negative
     * probe saying a fired clay good is not a commodity anybody buys. Classifying them as raw
     * clay would turn every brick house into a clay mine.
     */
    @Test
    void brickAndRoofBlocksAreNotRawClay() {
        for (String finished : new String[] {
                "britannia_mod:brick_wall_bottom", "britannia_mod:brick_wall_top",
                "britannia_mod:tile_roof", "britannia_mod:tile_roof_flat" }) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(finished));
            if (item == null || item == Items.AIR) continue; // no item form: already unsellable
            assertTrue(CommodityMappings.forStack(new ItemStack(item))
                            .filter(mapping -> "clay".equals(mapping.category())).isEmpty(),
                    finished + " classifies as raw clay, so a house is a clay mine");
        }
    }
}
