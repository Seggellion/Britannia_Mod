package com.seggellion.britannia_mod.structure.lifecycle;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.item.ShrineItemTransfer;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.AnchorSnapshot;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.RemovalResult;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.WorldAccess;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class ShrineLifecycleTestWorld implements WorldAccess {
    final Object identity = new Object();
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final Map<BlockPos, AnchorSnapshot> anchors = new HashMap<>();
    final Set<ChunkPos> unloaded = new HashSet<>();
    final List<ItemStack> drops = new ArrayList<>();
    final List<BlockPos> writes = new ArrayList<>();
    int synchronizations;
    BlockPos throwOnRemove;
    boolean reenterOnRemove;
    boolean failPartPlacement;
    RemovalResult recursiveResult;

    AnchorSnapshot placeShrine(BlockPos anchorPos, Direction facing, String variant) {
        return place(anchorPos, facing, ShrineMonolithDefinitions.SHRINE, variant);
    }

    AnchorSnapshot placeMonolith(BlockPos anchorPos, Direction facing) {
        return place(anchorPos, facing, ShrineMonolithDefinitions.MONOLITH,
                "diagnostic_missing_content");
    }

    private AnchorSnapshot place(
            BlockPos anchorPos,
            Direction facing,
            com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId familyId,
            String variant) {
        var footprint = ShrineMonolithDefinitions.catalogue()
                .family(familyId).orElseThrow().footprint();
        PlacedStructureState state = new PlacedStructureState(
                familyId, new VariantId(variant), facing, footprint);
        BlockState anchorState = MilestoneTwoRegisteredTestContent.anchor().defaultBlockState()
                .setValue(LargeStructureAnchorBlock.FACING, facing);
        AnchorSnapshot snapshot = new AnchorSnapshot(anchorPos, anchorState, state);
        anchors.put(anchorPos, snapshot);
        blocks.put(anchorPos, anchorState);
        for (LocalOffset offset : footprint) {
            if (!offset.equals(LocalOffset.ANCHOR)) {
                BlockPos pos = ShrinePlacementPlanner.worldPosition(anchorPos, facing, offset);
                blocks.put(pos, MilestoneTwoRegisteredTestContent.part().stateFor(facing, offset));
            }
        }
        return snapshot;
    }

    void unload(BlockPos pos) {
        unloaded.add(new ChunkPos(pos));
    }

    void load(BlockPos pos) {
        unloaded.remove(new ChunkPos(pos));
    }

    void replace(BlockPos pos, BlockState state) {
        blocks.put(pos, state);
    }

    long shrineCellCount() {
        return blocks.values().stream().filter(state -> isAnchor(state) || isPart(state)).count();
    }

    @Override public Object levelIdentity() { return identity; }
    @Override public boolean chunkLoaded(BlockPos pos) { return !unloaded.contains(new ChunkPos(pos)); }
    @Override public BlockState blockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }
    @Override public Optional<AnchorSnapshot> anchor(BlockPos pos) {
        return isAnchor(blockState(pos)) ? Optional.ofNullable(anchors.get(pos)) : Optional.empty();
    }
    @Override public boolean isAnchor(BlockState state) {
        return state.getBlock() == MilestoneTwoRegisteredTestContent.anchor();
    }
    @Override public boolean isPart(BlockState state) {
        return state.getBlock() == MilestoneTwoRegisteredTestContent.part();
    }
    @Override public boolean replaceable(BlockPos pos) { return blockState(pos).canBeReplaced(); }
    @Override public boolean removeCell(BlockPos pos) {
        if (pos.equals(throwOnRemove)) {
            throw new IllegalStateException("injected removal failure");
        }
        writes.add(pos);
        BlockState old = blockState(pos);
        blocks.put(pos, Blocks.AIR.defaultBlockState());
        if (isAnchor(old)) {
            anchors.remove(pos);
        }
        if (reenterOnRemove && recursiveResult == null && !anchors.isEmpty()) {
            AnchorSnapshot anchor = anchors.values().iterator().next();
            recursiveResult = ShrineLifecycleService.removeFrom(
                    this, anchor.position(), anchor.blockState(), ShrineRemovalCause.SURVIVAL_PLAYER);
        }
        return true;
    }
    @Override public boolean placePart(BlockPos pos, BlockState state) {
        writes.add(pos);
        if (failPartPlacement) {
            return false;
        }
        blocks.put(pos, state);
        return true;
    }
    @Override public BlockState expectedPart(Direction facing, LocalOffset offset) {
        return MilestoneTwoRegisteredTestContent.part().stateFor(facing, offset);
    }
    @Override public ItemStack configuredItem(PlacedStructureState state) {
        return ShrineItemTransfer.fromPlacedState(
                state.familyId().equals(ShrineMonolithDefinitions.MONOLITH)
                        ? MilestoneTwoRegisteredTestContent.monolith()
                        : MilestoneTwoRegisteredTestContent.shrine(), state);
    }
    @Override public void drop(BlockPos pos, ItemStack stack) { drops.add(stack.copy()); }
    @Override public void synchronizeAnchor(BlockPos pos) { synchronizations++; }
}
