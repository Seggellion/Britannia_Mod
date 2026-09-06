package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VariantTopOnlySlabBlockTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @Test
    void stateDefinitionCombinesSixVariationsWithEveryInheritedRoofProperty() {
        VariantTopOnlySlabBlock block = block();

        assertSame(VariantTopOnlySlabBlock.VARIATION, block.variationProperty());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5),
                Set.copyOf(VariantTopOnlySlabBlock.VARIATION.getPossibleValues()));
        assertTrue(block.getStateDefinition().getProperties().contains(TopOnlySlabBlock.TYPE));
        assertTrue(block.getStateDefinition().getProperties().contains(TopOnlySlabBlock.WATERLOGGED));
        assertTrue(block.getStateDefinition().getProperties().contains(TopOnlySlabBlock.SUPPORTS_LANTERN));
        assertTrue(block.getStateDefinition().getProperties().contains(VariantTopOnlySlabBlock.VARIATION));
        assertEquals(72, block.getStateDefinition().getPossibleStates().size());

        BlockState state = block.defaultBlockState();
        assertEquals(SlabType.TOP, state.getValue(TopOnlySlabBlock.TYPE));
        assertFalse(state.getValue(TopOnlySlabBlock.WATERLOGGED));
        assertFalse(state.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN));
        assertEquals(0, state.getValue(VariantTopOnlySlabBlock.VARIATION));
    }

    @Test
    void serverPlacementCanSelectEveryVariationWithoutChangingOtherState() {
        VariantTopOnlySlabBlock block = block();
        BlockState base = block.defaultBlockState()
                .setValue(TopOnlySlabBlock.TYPE, SlabType.DOUBLE)
                .setValue(TopOnlySlabBlock.WATERLOGGED, true)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
        RandomSource random = RandomSource.create(0x524F4F46L);
        Map<Integer, Integer> counts = new HashMap<>();

        for (int placement = 0; placement < 12_000; placement++) {
            BlockState placed = block.applyPlacementVariation(base, random, false);
            counts.merge(placed.getValue(VariantTopOnlySlabBlock.VARIATION), 1, Integer::sum);
            assertSame(block, placed.getBlock());
            assertEquals(SlabType.DOUBLE, placed.getValue(TopOnlySlabBlock.TYPE));
            assertTrue(placed.getValue(TopOnlySlabBlock.WATERLOGGED));
            assertTrue(placed.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN));
        }

        assertEquals(Set.of(0, 1, 2, 3, 4, 5), counts.keySet());
        counts.values().forEach(count -> assertTrue(count > 1_700 && count < 2_300, counts::toString));
    }

    @Test
    void clientPlacementKeepsPredictedStateAndDoesNotConsumeRandomness() {
        VariantTopOnlySlabBlock block = block();
        BlockState predicted = block.defaultBlockState()
                .setValue(VariantTopOnlySlabBlock.VARIATION, 4)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
        long seed = 0x434C49454E54L;
        RandomSource actualRandom = RandomSource.create(seed);
        RandomSource untouchedRandom = RandomSource.create(seed);

        BlockState actual = block.applyPlacementVariation(predicted, actualRandom, true);

        assertSame(predicted, actual);
        assertEquals(untouchedRandom.nextInt(), actualRandom.nextInt());
    }

    @Test
    void cyclingWrapsAndPreservesEveryUnrelatedRoofProperty() throws NoSuchMethodException {
        VariantTopOnlySlabBlock block = block();
        BlockState template = block.defaultBlockState()
                .setValue(TopOnlySlabBlock.TYPE, SlabType.DOUBLE)
                .setValue(TopOnlySlabBlock.WATERLOGGED, true)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);

        for (int variation = 0; variation < 6; variation++) {
            BlockState current = template.setValue(VariantTopOnlySlabBlock.VARIATION, variation);
            BlockState next = block.nextVariationState(current);

            assertSame(block, next.getBlock());
            assertEquals((variation + 1) % 6,
                    next.getValue(VariantTopOnlySlabBlock.VARIATION));
            assertEquals(SlabType.DOUBLE, next.getValue(TopOnlySlabBlock.TYPE));
            assertTrue(next.getValue(TopOnlySlabBlock.WATERLOGGED));
            assertTrue(next.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN));
        }

        assertEquals(TopOnlySlabBlock.class,
                VariantTopOnlySlabBlock.class
                        .getMethod("newBlockEntity", BlockPos.class, BlockState.class)
                        .getDeclaringClass());
        assertEquals(BlockEntity.class,
                VariantTopOnlySlabBlock.class
                        .getMethod("newBlockEntity", BlockPos.class, BlockState.class)
                        .getReturnType());
    }

    @Test
    void itemInteractionDelegatesToTheSharedVariantCycle() {
        DelegationProbe block = new DelegationProbe();
        ItemStack stack = ItemStack.EMPTY;
        BlockState state = block.defaultBlockState();
        BlockPos pos = BlockPos.ZERO;

        ItemInteractionResult result = block.useItemOn(
                stack, state, null, pos, null, InteractionHand.MAIN_HAND, null);

        assertTrue(block.called);
        assertEquals(ItemInteractionResult.FAIL, result);
        assertSame(stack, block.stack);
        assertSame(state, block.state);
        assertSame(pos, block.pos);
    }

    private static VariantTopOnlySlabBlock block() {
        return new VariantTopOnlySlabBlock(BlockBehaviour.Properties.of());
    }

    private static final class DelegationProbe extends VariantTopOnlySlabBlock {
        private boolean called;
        private ItemStack stack;
        private BlockState state;
        private BlockPos pos;

        private DelegationProbe() {
            super(BlockBehaviour.Properties.of());
        }

        @Override
        public ItemInteractionResult cycleVariation(
                ItemStack stack,
                BlockState state,
                Level level,
                BlockPos pos,
                Player player) {
            called = true;
            this.stack = stack;
            this.state = state;
            this.pos = pos;
            return ItemInteractionResult.FAIL;
        }
    }
}
