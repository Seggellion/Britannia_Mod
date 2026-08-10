package com.seggellion.britannia_mod.structure.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock;
import com.seggellion.britannia_mod.block.PitcherBlock;
import com.seggellion.britannia_mod.block.WaterWellBlock;
import com.seggellion.britannia_mod.item.PitcherItem;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import com.seggellion.britannia_mod.util.WaterSourceInteraction;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MilestoneFiveUtilityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void wellAndLadderUseTheirRequiredAuthoritativeFootprints() {
        WaterWellBlock well = new WaterWellBlock(
                BlockBehaviour.Properties.of(), 0, 0, 0, 1, 0, 1,
                (x, y, z) -> Block.box(1, 0, 1, 15, 15, 15));
        DecorativeMultiblockBlock ladder = new DecorativeMultiblockBlock(
                BlockBehaviour.Properties.of(), 0, 0, 0, 2, 0, 0,
                (x, y, z) -> Block.box(1, 0, 1, 15, 15, 15));
        assertEquals(4, well.cells().size());
        assertEquals(3, ladder.cells().size());
        Set<Integer> ladderHeights = ladder.cells().stream()
                .map(cell -> cell.y())
                .collect(Collectors.toSet());
        assertEquals(Set.of(0, 1, 2), ladderHeights);
    }

    @Test
    void emptyPitcherRegistrationCarriesWaterBehaviorAndRemainsPlaceable() {
        PitcherBlock block = new PitcherBlock(BlockBehaviour.Properties.of());
        PitcherItem pitcher = new PitcherItem(block, new Item.Properties().stacksTo(1));
        assertEquals(block, pitcher.getBlock());
        ItemStack stack = new ItemStack(pitcher);
        assertFalse(pitcher.isFilled(stack));
        pitcher.fillWithWater(stack);
        assertTrue(pitcher.isFilled(stack));
    }

    @Test
    void fillServiceAcceptsExactlyTheApprovedContainerFamilies() {
        WateringCanItem wateringCan = new WateringCanItem(new Item.Properties().stacksTo(1));
        PitcherItem pitcher = new PitcherItem(
                new PitcherBlock(BlockBehaviour.Properties.of()), new Item.Properties().stacksTo(1));
        assertTrue(WaterSourceInteraction.supports(new ItemStack(wateringCan)));
        assertTrue(WaterSourceInteraction.supports(new ItemStack(pitcher)));
        assertTrue(WaterSourceInteraction.supports(new ItemStack(Items.BUCKET)));
        assertFalse(WaterSourceInteraction.supports(new ItemStack(Items.GLASS_BOTTLE)));
    }
}
