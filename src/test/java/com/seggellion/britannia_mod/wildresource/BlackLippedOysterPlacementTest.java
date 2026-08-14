package com.seggellion.britannia_mod.wildresource;

import com.google.gson.JsonArray;
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

class BlackLippedOysterPlacementTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final BlockPos TARGET = new BlockPos(8, 65, 8);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void repositoryLimestoneIdentityAndVanillaCalciteAreAccepted() {
        FakeView view = new FakeView();
        view.states.put(TARGET.below(), Blocks.CALCITE.defaultBlockState());

        assertEquals("minecraft:calcite", WildResourceEntries.CANONICAL_LIMESTONE.toString());
        assertTrue(WildResourcePlacementRules.isOysterSubstrate(TARGET, view));
    }

    @Test
    void unrelatedSubstrateIsRejected() {
        FakeView view = new FakeView();
        view.states.put(TARGET.below(), Blocks.STONE.defaultBlockState());
        assertFalse(WildResourcePlacementRules.isOysterSubstrate(TARGET, view));
    }

    @Test
    void substrateTagIsExtensibleWithoutInventingARegisteredLimestone() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/tags/block/black_lipped_oyster_substrates.json"
        ))).getAsJsonObject();
        JsonArray values = root.getAsJsonArray("values");
        assertEquals("minecraft:calcite", values.get(0).getAsString());
        assertEquals("britannia_mod:limestone", values.get(1).getAsJsonObject().get("id").getAsString());
        assertFalse(values.get(1).getAsJsonObject().get("required").getAsBoolean());
    }

    @Test
    void oysterCapCooldownAndProbeBoundsAreCentralized() {
        WildResourceTuning tuning = WildResourceEntries.BLACK_LIPPED_OYSTER_TUNING;
        assertEquals(4, tuning.maxRandomProbes());
        assertTrue(tuning.attemptIntervalMinTicks() > 0);
        assertTrue(tuning.respawnCooldownMinTicks() >= tuning.attemptIntervalMinTicks());
    }

    private static final class FakeView implements WildResourcePlacementRules.PlacementView {
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        @Override public BlockState stateAt(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }
        @Override public boolean hasBlockEntity(BlockPos position) { return false; }
        @Override public FluidState getFluidState(BlockPos position) { return stateAt(position).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
    }
}
