package com.seggellion.britannia_mod.structure.interaction;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureDefinitionValidator;
import com.seggellion.britannia_mod.structure.definition.StructureVariantCycler;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.AnchorSnapshot;
import com.seggellion.britannia_mod.structure.lifecycle.ShrineLifecycleService.Resolution;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative, one-step shrine variant transaction. */
public final class ShrineVariantCycleService {
    public enum Result {
        SUCCESS, WRONG_TOOL, CLIENT_SIDE, UNAUTHORIZED, NOT_A_STRUCTURE_CELL, INVALID_PART,
        ANCHOR_CHUNK_UNAVAILABLE, ANCHOR_MISSING, ANCHOR_BLOCK_ENTITY_MISSING, FAMILY_NOT_SHRINE,
        CURRENT_VARIANT_MISSING, NO_ALTERNATE_ENABLED_VARIANT, INVALID_CYCLE_DEFINITION,
        INCOMPATIBLE_NEXT_VARIANT, STATE_ASSIGNMENT_FAILED, SYNCHRONIZATION_FAILED, ROLLBACK_FAILED
    }

    public record Target(Result result, BlockPos anchorPosition, Optional<PlacedStructureState> state) {
        public Target {
            Objects.requireNonNull(result, "result");
            anchorPosition = Objects.requireNonNull(anchorPosition, "anchorPosition").immutable();
            state = Objects.requireNonNull(state, "state");
        }

        public static Target success(BlockPos anchorPosition, PlacedStructureState state) {
            return new Target(Result.SUCCESS, anchorPosition, Optional.of(state));
        }

        public static Target failure(Result result, BlockPos position) {
            return new Target(result, position, Optional.empty());
        }
    }

    /** Narrow injectable boundary for authoritative mutation, synchronization, and rollback tests. */
    public interface Mutation {
        boolean serverSide();
        boolean correctTool();
        boolean authorized();
        Target resolve();
        StructureCatalogue catalogue();
        default Optional<Family> family(PlacedStructureState state) {
            return catalogue().family(state.familyId());
        }
        boolean assign(PlacedStructureState expected, PlacedStructureState replacement);
        Optional<PlacedStructureState> readback();
        boolean synchronize();
        boolean restore(PlacedStructureState expectedCurrent, PlacedStructureState previous);
        void successFeedback(BlockPos anchorPosition, Variant selected);
    }

    private ShrineVariantCycleService() {
    }

    public static Result cycle(
            ServerLevel level, ServerPlayer player, ItemStack heldStack,
            BlockPos sourcePosition, BlockState sourceState) {
        return cycle(liveMutation(level, player, heldStack, sourcePosition, sourceState));
    }

    public static Result cycle(Mutation mutation) {
        if (!mutation.serverSide()) return Result.CLIENT_SIDE;
        if (!mutation.correctTool()) return Result.WRONG_TOOL;
        if (!mutation.authorized()) return Result.UNAUTHORIZED;

        Target target = mutation.resolve();
        if (target.result() != Result.SUCCESS) return target.result();
        PlacedStructureState previous = target.state().orElseThrow();
        if (!previous.familyId().equals(ShrineMonolithDefinitions.SHRINE)) return Result.FAMILY_NOT_SHRINE;

        Optional<Family> familyResult = mutation.family(previous);
        if (familyResult.isEmpty()) return Result.FAMILY_NOT_SHRINE;
        Family family = familyResult.orElseThrow();
        if (!validCycleDefinition(family)) return Result.INVALID_CYCLE_DEFINITION;
        Optional<Variant> current = family.variants().stream()
                .filter(variant -> variant.id().equals(previous.variantId()))
                .findFirst();
        if (current.isEmpty()) return Result.CURRENT_VARIANT_MISSING;
        Optional<Variant> next = StructureVariantCycler.next(family, previous.variantId());
        if (next.isEmpty() || next.orElseThrow().id().equals(previous.variantId())) {
            return Result.NO_ALTERNATE_ENABLED_VARIANT;
        }
        Variant selected = next.orElseThrow();
        if (!selected.enabled()
                || !selected.familyId().equals(ShrineMonolithDefinitions.SHRINE)
                || !StructureDefinitionValidator.compatible(family, selected)
                || family.sharedGeometry().filter(selected.model()::equals).isEmpty()) {
            return Result.INCOMPATIBLE_NEXT_VARIANT;
        }

        PlacedStructureState replacement = new PlacedStructureState(
                previous.schemaVersion(), previous.familyId(), selected.id(),
                previous.facing(), previous.footprint());
        try {
            if (!mutation.assign(previous, replacement)) {
                return mutation.readback().equals(Optional.of(previous))
                        ? Result.STATE_ASSIGNMENT_FAILED
                        : rollback(mutation, replacement, previous, Result.STATE_ASSIGNMENT_FAILED);
            }
            if (!mutation.readback().equals(Optional.of(replacement))) {
                return rollback(mutation, replacement, previous, Result.STATE_ASSIGNMENT_FAILED);
            }
            if (!mutation.synchronize()) {
                return rollback(mutation, replacement, previous, Result.SYNCHRONIZATION_FAILED);
            }
            if (!mutation.readback().equals(Optional.of(replacement))) {
                return rollback(mutation, replacement, previous, Result.SYNCHRONIZATION_FAILED);
            }
            mutation.successFeedback(target.anchorPosition(), selected);
            return Result.SUCCESS;
        } catch (RuntimeException exception) {
            return mutation.readback().equals(Optional.of(previous))
                    ? Result.STATE_ASSIGNMENT_FAILED
                    : rollback(mutation, replacement, previous, Result.STATE_ASSIGNMENT_FAILED);
        }
    }

    private static Result rollback(
            Mutation mutation, PlacedStructureState expectedCurrent,
            PlacedStructureState previous, Result failure) {
        try {
            return mutation.restore(expectedCurrent, previous)
                    && mutation.readback().equals(Optional.of(previous))
                    && mutation.synchronize()
                    ? failure : Result.ROLLBACK_FAILED;
        } catch (RuntimeException exception) {
            return Result.ROLLBACK_FAILED;
        }
    }

    private static boolean validCycleDefinition(Family family) {
        Set<Integer> positions = new HashSet<>();
        if (family.variants().isEmpty()) return false;
        for (Variant variant : family.variants()) {
            if (variant.cyclePosition() < 0 || !positions.add(variant.cyclePosition())) return false;
        }
        return true;
    }

    private static Mutation liveMutation(
            ServerLevel level, ServerPlayer player, ItemStack heldStack,
            BlockPos sourcePosition, BlockState sourceState) {
        return new Mutation() {
            private BlockPos anchorPosition = sourcePosition.immutable();
            private LargeStructureAnchorBlockEntity anchor;

            @Override public boolean serverSide() { return !level.isClientSide(); }
            @Override public boolean correctTool() { return heldStack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()); }
            @Override public boolean authorized() { return DecoratorAuthorization.isAuthorized(player); }
            @Override public StructureCatalogue catalogue() { return ShrineMonolithDefinitions.catalogue(); }

            @Override
            public Target resolve() {
                boolean anchorCell = sourceState.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get();
                boolean partCell = sourceState.getBlock() == LargeStructureRegistry.LARGE_STRUCTURE_PART.get();
                if (!anchorCell && !partCell) return Target.failure(Result.NOT_A_STRUCTURE_CELL, sourcePosition);
                Resolution resolution = ShrineLifecycleService.resolve(level, sourcePosition, sourceState);
                anchorPosition = resolution.anchorPosition();
                if (resolution.status() == ShrineLifecycleService.ResolutionStatus.ANCHOR_CHUNK_UNLOADED) {
                    return Target.failure(Result.ANCHOR_CHUNK_UNAVAILABLE, anchorPosition);
                }
                if (resolution.status() == ShrineLifecycleService.ResolutionStatus.INVALID_MEMBERSHIP) {
                    return Target.failure(Result.INVALID_PART, anchorPosition);
                }
                if (!resolution.valid()) {
                    if (!level.getBlockState(anchorPosition).is(LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get())) {
                        return Target.failure(Result.ANCHOR_MISSING, anchorPosition);
                    }
                    return Target.failure(Result.ANCHOR_BLOCK_ENTITY_MISSING, anchorPosition);
                }
                AnchorSnapshot snapshot = resolution.anchor().orElseThrow();
                BlockEntity entity = level.getBlockEntity(anchorPosition);
                if (!(entity instanceof LargeStructureAnchorBlockEntity resolvedAnchor)) {
                    return Target.failure(Result.ANCHOR_BLOCK_ENTITY_MISSING, anchorPosition);
                }
                anchor = resolvedAnchor;
                return Target.success(anchorPosition, snapshot.state());
            }

            @Override public boolean assign(PlacedStructureState expected, PlacedStructureState replacement) {
                return anchor != null && anchor.replacePlacedState(expected, replacement);
            }
            @Override public Optional<PlacedStructureState> readback() {
                return anchor == null ? Optional.empty() : anchor.placedState();
            }
            @Override public boolean synchronize() {
                if (anchor == null) return false;
                anchor.synchronize();
                return true;
            }
            @Override public boolean restore(PlacedStructureState expectedCurrent, PlacedStructureState previous) {
                return anchor != null && anchor.replacePlacedState(expectedCurrent, previous);
            }
            @Override public void successFeedback(BlockPos position, Variant selected) {
                level.playSound(null, position, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable(
                        "message.britannia_mod.shrine.decorator.selected",
                        Component.translatable(selected.displayName().translationKey().orElseThrow())), true);
            }
        };
    }
}
