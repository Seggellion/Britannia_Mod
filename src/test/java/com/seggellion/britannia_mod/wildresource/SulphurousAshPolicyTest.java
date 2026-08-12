package com.seggellion.britannia_mod.wildresource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SulphurousAshPolicyTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final BlockPos TARGET = new BlockPos(8, 65, 8);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void safeTargetRequiresReplaceableFluidFreeNonBlockEntitySpace() {
        FakeView view = new FakeView();
        assertTrue(WildResourcePlacementRules.isSafeTarget(TARGET, view));

        view.states.put(TARGET, Blocks.STONE.defaultBlockState());
        assertFalse(WildResourcePlacementRules.isSafeTarget(TARGET, view));

        view.states.put(TARGET, Blocks.WATER.defaultBlockState());
        assertFalse(WildResourcePlacementRules.isSafeTarget(TARGET, view));

        view.states.put(TARGET, Blocks.AIR.defaultBlockState());
        view.hasBlockEntity = true;
        assertFalse(WildResourcePlacementRules.isSafeTarget(TARGET, view));
    }

    @Test
    void ashSupportIsConservativeAndNatural() {
        FakeView view = new FakeView();
        view.states.put(TARGET.below(), Blocks.NETHERRACK.defaultBlockState());
        assertTrue(WildResourcePlacementRules.isAshSupport(TARGET, view));

        view.states.put(TARGET.below(), Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(WildResourcePlacementRules.isAshSupport(TARGET, view));
    }

    @Test
    void tuningAndDropAreCentralizedAndExactlyOneExistingReagent() throws IOException {
        assertEquals(2, new WildResourceEntry(
                WildResourceEntries.SULPHUROUS_ASH, 1, 2, WildResourceEntries.SULPHUROUS_ASH_TUNING,
                (level, chunk, random) -> TARGET,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                WildResourceEntry.PlacementRule.ALLOW,
                (level, position) -> true,
                WildResourceEntry.HarvestStrategy.DISABLED,
                WildResourceEntry.LootStrategy.NONE
        ).maxNodesPerChunk());

        JsonObject root = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/loot_table/blocks/sulphurous_ash_patch.json"
        ))).getAsJsonObject();
        JsonObject entry = root.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("britannia_mod:sulphurous_ash", entry.get("name").getAsString());
        assertEquals(1, root.getAsJsonArray("pools").get(0).getAsJsonObject().get("rolls").getAsInt());
    }

    private static final class FakeView implements WildResourcePlacementRules.PlacementView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private boolean hasBlockEntity;

        @Override public BlockState stateAt(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }
        @Override public boolean hasBlockEntity(BlockPos position) { return hasBlockEntity; }
        @Override public FluidState getFluidState(BlockPos position) { return stateAt(position).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
    }
}
