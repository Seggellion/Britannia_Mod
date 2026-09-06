package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.seggellion.britannia_mod.item.CrateItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Which of the two competing interactions wins when a crate is clicked.
 *
 * <p>Minecraft runs a block's interaction before the held item's, so {@code CrateBlock.useWithoutItem}
 * consumed every click and the placement item was never reached: a crate could not be stacked at all
 * without sneaking. The fix hands the click to the item, but only in the one case that needs it, and
 * this suite is what keeps that exception narrow - a crate that stopped opening for an axe, a torch,
 * or an empty hand would be a far worse regression than the one being fixed.
 *
 * <p>The override reads only the held stack and the hit face, so the level and player it is handed
 * here are deliberately absent; anything that starts consulting them will fail loudly rather than
 * quietly widening the exception.
 */
class CrateStackingInteractionTest {

    private static CrateBlock crate;
    private static Item crateItem;
    private static Item nonCrateBlockItem;

    @BeforeAll
    static void registerTestContent() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        crate = Registry.register(
            BuiltInRegistries.BLOCK,
            id("m1_test_crate"),
            new CrateBlock(
                BlockBehaviour.Properties.of(), 9, "container.test.crate",
                0, 0, 0, 0, 0, 0, (x, y, z) -> CrateShapes.SMALL));
        crateItem = Registry.register(
            BuiltInRegistries.ITEM, id("m1_test_crate"), new CrateItem(crate, new Item.Properties()));
        // A block item whose block is not a crate: the exception must not widen to every BlockItem.
        nonCrateBlockItem = Items.STONE;
    }

    /* ─── the exception ──────────────────────────────────────── */

    @Test
    void aCrateClickedOnTopWhileHoldingACrateYieldsToThePlacementItem() {
        assertEquals(
            ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
            interact(new ItemStack(crateItem), Direction.UP),
            "a crate held over a crate top must reach its own placement code");
    }

    /* ─── everything the exception must not touch ────────────── */

    @Test
    void aCrateClickedOnItsSideWhileHoldingACrateStillOpens() {
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) {
                continue;
            }
            assertEquals(
                ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                interact(new ItemStack(crateItem), face),
                "a crate clicked on its " + face + " face must still open");
        }
    }

    @Test
    void aCrateClickedOnTopWithAnyOtherItemStillOpens() {
        assertEquals(
            ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            interact(new ItemStack(Items.DIAMOND_AXE), Direction.UP),
            "a crate must still open for a tool");
        assertEquals(
            ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            interact(new ItemStack(nonCrateBlockItem), Direction.UP),
            "a crate must still open for a block that is not a crate");
    }

    @Test
    void aCrateClickedOnTopWithAnEmptyHandStillOpens() {
        assertEquals(
            ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            interact(ItemStack.EMPTY, Direction.UP),
            "a crate must still open for an empty hand");
    }

    /**
     * The result chosen matters as much as the branch: {@code PASS} would consume nothing but still
     * fall through to {@code useWithoutItem} and open the crate, and anything that consumes the action
     * would stop the placement item running at all.
     */
    @Test
    void theYieldingResultNeitherConsumesTheActionNorOpensTheCrate() {
        ItemInteractionResult yielding = ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        assertFalse(yielding.consumesAction(), "yielding must not consume the click");
        assertNotEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, yielding,
            "yielding must not fall through to the crate's own menu");
    }

    /**
     * The same rule, stated where Grabby Hands can read it.
     *
     * <p>Grabby reaches crate placement before the block does, so the block's own answer above is only
     * half the arbitration. {@code isPlacementGesture} is what lets Grabby tell a stacking attempt
     * from a click meant for the crate, and it has to agree exactly with the upward-face rule
     * {@code useOn} enforces — a disagreement would either swallow interactions again or claim clicks
     * placement cannot honour.
     */
    @Test
    void onlyAnUpwardFaceCountsAsAPlacementGesture() {
        CrateItem item = new CrateItem(crate, new Item.Properties());
        for (Direction face : Direction.values()) {
            BlockHitResult hit =
                    new BlockHitResult(Vec3.atCenterOf(BlockPos.ZERO), face, BlockPos.ZERO, false);
            assertEquals(face == Direction.UP, item.isPlacementGesture(hit),
                    face + " was misjudged as a placement gesture");
        }
    }

    /* ─── helpers ────────────────────────────────────────────── */

    private static ItemInteractionResult interact(ItemStack held, Direction face) {
        BlockPos pos = BlockPos.ZERO;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        return crate.useItemOn(
            held, crate.defaultBlockState(), null, pos, null, InteractionHand.MAIN_HAND, hit);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}
