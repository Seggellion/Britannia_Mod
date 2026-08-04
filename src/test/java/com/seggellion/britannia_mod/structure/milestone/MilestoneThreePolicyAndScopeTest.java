package com.seggellion.britannia_mod.structure.milestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MilestoneThreePolicyAndScopeTest {
    private static final Path MAIN = Path.of("src/main/java/com/seggellion/britannia_mod");

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void everyRegisteredAnchorAndPartStateBlocksNormalAndStickyPistonMovement() {
        assertEquals(PushReaction.BLOCK,
                MilestoneTwoRegisteredTestContent.anchor().defaultBlockState().getPistonPushReaction());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int x = 0; x <= 2; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = 0; z <= 1; z++) {
                        LocalOffset offset = new LocalOffset(x, y, z);
                        if (!offset.equals(LocalOffset.ANCHOR)) {
                            assertEquals(PushReaction.BLOCK,
                                    MilestoneTwoRegisteredTestContent.part()
                                            .stateFor(facing, offset).getPistonPushReaction());
                        }
                    }
                }
            }
        }
    }

    @Test
    void anchorAndPartsAreNotWaterloggableOrFluidReplaceable() {
        var anchor = MilestoneTwoRegisteredTestContent.anchor();
        var part = MilestoneTwoRegisteredTestContent.part();
        assertFalse(LiquidBlockContainer.class.isAssignableFrom(anchor.getClass()));
        assertFalse(LiquidBlockContainer.class.isAssignableFrom(part.getClass()));
        assertTrue(anchor.defaultBlockState().getFluidState().isEmpty());
        assertTrue(part.defaultBlockState().getFluidState().isEmpty());
        assertFalse(anchor.defaultBlockState().canBeReplaced());
        assertFalse(part.defaultBlockState().canBeReplaced());
    }

    @Test
    void oneAnchorEntityAndNoPartEntityRemain() {
        assertTrue(EntityBlock.class.isAssignableFrom(
                MilestoneTwoRegisteredTestContent.anchor().getClass()));
        assertFalse(EntityBlock.class.isAssignableFrom(
                MilestoneTwoRegisteredTestContent.part().getClass()));
    }

    @Test
    void playerBreakExplosionExternalReplacementPickAndLootAllDelegateToCentralPolicy() throws Exception {
        for (String file : new String[] {
                "structure/multiblock/LargeStructureAnchorBlock.java",
                "structure/multiblock/LargeStructurePartBlock.java"}) {
            String source = Files.readString(MAIN.resolve(file));
            assertTrue(source.contains("onDestroyedByPlayer"));
            assertTrue(source.contains("ShrineLifecycleService.removeFrom"));
            assertTrue(source.contains("ShrineRemovalCause.EXPLOSION"));
            assertTrue(source.contains("getCloneItemStack"));
            assertTrue(source.contains("return List.of()"));
            assertTrue(source.contains("playerDestroy"));
        }
        String lifecycle = Files.readString(MAIN.resolve(
                "structure/lifecycle/ShrineLifecycleService.java"));
        assertEquals(1, count(lifecycle, "Block\\.popResource\\("));
        assertTrue(lifecycle.contains("finally"));
        assertTrue(lifecycle.contains("IN_PROGRESS.remove(key)"));
        assertTrue(lifecycle.contains("System.identityHashCode(level)"));
    }

    @Test
    void integritySchedulingIsChunkEventDrivenCappedAndNonForceLoading() throws Exception {
        String handler = Files.readString(MAIN.resolve(
                "structure/lifecycle/ShrineIntegrityHandler.java"));
        assertTrue(handler.contains("ChunkEvent.Load"));
        assertTrue(handler.contains("ServerTickEvent.Post"));
        assertTrue(handler.contains("MAX_CHUNKS_PER_TICK = 64"));
        assertTrue(handler.contains("MAX_PENDING_CHUNKS_PER_LEVEL = 4096"));
        assertTrue(handler.contains("deltaX = -1"));
        assertTrue(handler.contains("LevelEvent.Unload"));
        assertTrue(handler.contains("getChunkNow"));
        assertFalse(handler.contains("getChunkAt"));
        assertFalse(handler.contains("level.getChunk("));
    }

    @Test
    void milestoneFourAndLaterScopesRemainAbsentAndCommonCodeHasNoClientImports() throws Exception {
        for (Path folder : new Path[] {
                MAIN.resolve("structure/item"), MAIN.resolve("structure/multiblock"),
                MAIN.resolve("structure/placement"), MAIN.resolve("structure/lifecycle")}) {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("net.minecraft.client"), path.toString());
                    assertFalse(source.contains("BlockEntityRenderer"), path.toString());
                    assertFalse(source.contains("InteriorDecorator"), path.toString());
                    assertFalse(source.contains("texture cycling"), path.toString());
                }
            }
        }
        assertFalse(Files.exists(MAIN.resolve("structure/renderer")));
        String registry = Files.readString(MAIN.resolve("registry/LargeStructureRegistry.java"));
        assertFalse(registry.contains("BlockItem"));
        assertEquals(1, count(registry, "ITEMS\\.register\\(\\s*\"monolith\""));
    }

    @Test
    void exactComponentIdAndThreeFieldSchemaAreRegistered() throws Exception {
        String registry = Files.readString(MAIN.resolve("registry/DataComponentRegistry.java"));
        assertTrue(registry.contains("\"shrine_instance_state\""));
        assertTrue(registry.contains("persistent(ShrineItemState.CODEC)"));
        assertTrue(registry.contains("networkSynchronized(ShrineItemState.STREAM_CODEC)"));
        String state = Files.readString(MAIN.resolve("structure/item/ShrineItemState.java"));
        assertTrue(state.contains("schema_version"));
        assertTrue(state.contains("family_id"));
        assertTrue(state.contains("variant_id"));
        assertFalse(state.contains("placed_footprint"));
    }

    private static long count(String value, String regex) {
        return java.util.regex.Pattern.compile(regex).matcher(value).results().count();
    }
}
