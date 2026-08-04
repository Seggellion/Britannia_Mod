package com.seggellion.britannia_mod.structure.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ClientResource;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.DisplayName;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.interaction.ShrineVariantCycleService.Mutation;
import com.seggellion.britannia_mod.structure.interaction.ShrineVariantCycleService.Result;
import com.seggellion.britannia_mod.structure.interaction.ShrineVariantCycleService.Target;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MonolithVariantCycleServiceTest {
    private static final BlockPos ANCHOR = new BlockPos(-17, 74, 29);

    @Test
    void exactTwoStepCatalogueOrderWrapsDeterministicallyAndChangesOnlyVariantId() {
        PlacedStructureState original = state("diagnostic_missing_content", Direction.EAST);
        FakeMutation mutation = new FakeMutation(original);

        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        PlacedStructureState alternate = mutation.state.orElseThrow();
        assertEquals("diagnostic_alternate", alternate.variantId().value());
        assertNotEquals(original.variantId(), alternate.variantId());
        assertInvariantFields(original, alternate);

        mutation.target = Target.success(ANCHOR, alternate);
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        PlacedStructureState wrapped = mutation.state.orElseThrow();
        assertEquals("diagnostic_missing_content", wrapped.variantId().value());
        assertInvariantFields(original, wrapped);
        assertEquals(2, mutation.assignments);
        assertEquals(2, mutation.synchronizations);
        assertEquals(2, mutation.feedback);
    }

    @Test
    void disabledCandidateIsSkippedAndDisplayOrResourceNamesCannotOwnOrder() {
        Family family = monolithFamily();
        Variant first = family.variants().get(0);
        Variant alternate = family.variants().get(1);
        Variant disabled = copy(alternate, alternate.id(),
                DisplayName.resolved("diagnostic_alternate", "a.translation", "AAA"),
                alternate.familyId(), alternate.footprint(), alternate.renderOffsetVoxels(),
                ClientResource.available("aaa", "britannia_mod", "geo/aaa.geo.json"),
                alternate.texture(), 1, false);
        Variant later = copy(alternate, new VariantId("test_later"),
                DisplayName.resolved("test_later", "z.translation", "ZZZ"),
                alternate.familyId(), alternate.footprint(), alternate.renderOffsetVoxels(),
                ClientResource.available("zzz", "britannia_mod", "geo/zzz.geo.json"),
                ClientResource.available("zzz_texture", "britannia_mod", "textures/block/monolith/zzz.png"),
                2, true);
        FakeMutation mutation = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        mutation.family = copyFamily(family, List.of(first, disabled, later));

        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        assertEquals("test_later", mutation.state.orElseThrow().variantId().value());
    }

    @Test
    void missingCurrentNoAlternateAndDuplicatePositionsFailWithoutMutationOrFeedback() {
        FakeMutation missing = new FakeMutation(state("removed_variant", Direction.NORTH));
        assertEquals(Result.CURRENT_VARIANT_MISSING, ShrineVariantCycleService.cycle(missing));
        assertUnchanged(missing, "removed_variant");

        Family family = monolithFamily();
        FakeMutation single = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        single.family = copyFamily(family, List.of(family.variants().getFirst()));
        assertEquals(Result.NO_ALTERNATE_ENABLED_VARIANT, ShrineVariantCycleService.cycle(single));
        assertUnchanged(single, "diagnostic_missing_content");

        Variant duplicate = copy(family.variants().get(1), family.variants().get(1).id(),
                family.variants().get(1).displayName(), family.id(), family.footprint(),
                family.renderOffsetVoxels(), family.variants().get(1).model(),
                family.variants().get(1).texture(), 0, true);
        FakeMutation invalid = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        invalid.family = copyFamily(family, List.of(family.variants().getFirst(), duplicate));
        assertEquals(Result.INVALID_CYCLE_DEFINITION, ShrineVariantCycleService.cycle(invalid));
        assertUnchanged(invalid, "diagnostic_missing_content");
    }

    @Test
    void incompatibleFootprintOffsetAndResourceIdentitiesFailExplicitly() {
        Family family = monolithFamily();
        Variant current = family.variants().getFirst();
        Variant alternate = family.variants().get(1);

        assertFailure(family, current, copy(alternate, alternate.id(), alternate.displayName(),
                alternate.familyId(), alternate.footprint().subList(0, 17), alternate.renderOffsetVoxels(),
                alternate.model(), alternate.texture(), 1, true), Result.INCOMPATIBLE_FOOTPRINT);
        assertFailure(family, current, copy(alternate, alternate.id(), alternate.displayName(),
                alternate.familyId(), alternate.footprint(), new VoxelOffset(0, 0, 0),
                alternate.model(), alternate.texture(), 1, true), Result.INCOMPATIBLE_RENDER_OFFSET);
        assertFailure(family, current, copy(alternate, alternate.id(), alternate.displayName(),
                alternate.familyId(), alternate.footprint(), alternate.renderOffsetVoxels(),
                ClientResource.available("bad_model", "Bad Namespace", "bad path"),
                alternate.texture(), 1, true), Result.INVALID_MODEL_RESOURCE);
        assertFailure(family, current, copy(alternate, alternate.id(), alternate.displayName(),
                alternate.familyId(), alternate.footprint(), alternate.renderOffsetVoxels(),
                alternate.model(), ClientResource.unavailable("missing_texture", "test"),
                1, true), Result.INVALID_TEXTURE_RESOURCE);
    }

    @Test
    void serverAuthorityAndToolAuthorizationFailuresNeverResolveOrMutate() {
        FakeMutation client = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        client.server = false;
        assertEquals(Result.CLIENT_SIDE, ShrineVariantCycleService.cycle(client));
        assertEquals(0, client.resolutions);

        FakeMutation wrongOrEmptyTool = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        wrongOrEmptyTool.tool = false;
        assertEquals(Result.WRONG_TOOL, ShrineVariantCycleService.cycle(wrongOrEmptyTool));
        assertEquals(0, wrongOrEmptyTool.resolutions);

        FakeMutation unauthorized = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        unauthorized.authorization = false;
        assertEquals(Result.UNAUTHORIZED, ShrineVariantCycleService.cycle(unauthorized));
        assertEquals(0, unauthorized.resolutions);
        assertUnchanged(client, "diagnostic_missing_content");
        assertUnchanged(wrongOrEmptyTool, "diagnostic_missing_content");
        assertUnchanged(unauthorized, "diagnostic_missing_content");
    }

    @Test
    void targetAssignmentSynchronizationAndRollbackFailuresAreNonSuccessful() {
        for (Result failure : List.of(Result.NOT_A_STRUCTURE_CELL, Result.INVALID_PART,
                Result.ANCHOR_CHUNK_UNAVAILABLE, Result.ANCHOR_MISSING,
                Result.ANCHOR_BLOCK_ENTITY_MISSING)) {
            FakeMutation mutation = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
            mutation.target = Target.failure(failure, ANCHOR);
            assertEquals(failure, ShrineVariantCycleService.cycle(mutation));
            assertUnchanged(mutation, "diagnostic_missing_content");
        }

        FakeMutation assignment = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        assignment.assign = false;
        assertEquals(Result.STATE_ASSIGNMENT_FAILED, ShrineVariantCycleService.cycle(assignment));
        assertUnchanged(assignment, "diagnostic_missing_content");

        FakeMutation synchronization = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        synchronization.synchronizationResults = new ArrayList<>(List.of(false, true));
        assertEquals(Result.SYNCHRONIZATION_FAILED, ShrineVariantCycleService.cycle(synchronization));
        assertUnchanged(synchronization, "diagnostic_missing_content");
        assertEquals(1, synchronization.restores);

        synchronization.synchronizationResults = new ArrayList<>(List.of(true));
        synchronization.target = Target.success(ANCHOR, synchronization.state.orElseThrow());
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(synchronization));
        assertEquals("diagnostic_alternate", synchronization.state.orElseThrow().variantId().value());
        assertEquals(1, synchronization.feedback);

        FakeMutation rollback = new FakeMutation(state("diagnostic_missing_content", Direction.NORTH));
        rollback.synchronizationResults = new ArrayList<>(List.of(false));
        rollback.restore = false;
        assertEquals(Result.ROLLBACK_FAILED, ShrineVariantCycleService.cycle(rollback));
        assertEquals(0, rollback.feedback);
    }

    private static void assertFailure(
            Family family, Variant current, Variant alternate, Result expected) {
        FakeMutation mutation = new FakeMutation(state(current.id().value(), Direction.NORTH));
        mutation.family = copyFamily(family, List.of(current, alternate));
        assertEquals(expected, ShrineVariantCycleService.cycle(mutation));
        assertUnchanged(mutation, current.id().value());
    }

    private static void assertInvariantFields(PlacedStructureState before, PlacedStructureState after) {
        assertEquals(before.schemaVersion(), after.schemaVersion());
        assertEquals(ShrineMonolithDefinitions.MONOLITH, after.familyId());
        assertEquals(before.footprint(), after.footprint());
        assertEquals(before.facing(), after.facing());
        assertEquals(18, after.footprint().size());
    }

    private static void assertUnchanged(FakeMutation mutation, String variant) {
        assertEquals(variant, mutation.state.orElseThrow().variantId().value());
        assertEquals(0, mutation.feedback);
    }

    private static Family monolithFamily() {
        return ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.MONOLITH).orElseThrow();
    }

    private static PlacedStructureState state(String variant, Direction facing) {
        return new PlacedStructureState(ShrineMonolithDefinitions.MONOLITH,
                new VariantId(variant), facing, monolithFamily().footprint());
    }

    private static Family copyFamily(Family family, List<Variant> variants) {
        return new Family(family.id(), family.displayName(), family.dimensions(), family.footprint(),
                family.anchorOffset(), family.placementMode(), family.collisionProfile(), family.renderOrigin(),
                family.renderOffsetVoxels(), family.geometryMode(), family.sharedGeometry(),
                family.defaultVariant(), family.contentStatus(), variants);
    }

    private static Variant copy(
            Variant base, VariantId id, DisplayName displayName, FamilyId familyId,
            List<LocalOffset> footprint, VoxelOffset renderOffset, ClientResource model,
            ClientResource texture, int cyclePosition, boolean enabled) {
        return new Variant(id, familyId, displayName, base.dimensions(), footprint,
                base.placementMode(), base.collisionProfile(), base.renderOrigin(), renderOffset,
                model, texture, cyclePosition, enabled, base.playerFacing(), base.contentStatus());
    }

    private static final class FakeMutation implements Mutation {
        boolean server = true;
        boolean tool = true;
        boolean authorization = true;
        boolean assign = true;
        boolean restore = true;
        Optional<PlacedStructureState> state;
        Target target;
        Family family = monolithFamily();
        List<Boolean> synchronizationResults = new ArrayList<>(List.of(true));
        int assignments;
        int restores;
        int synchronizations;
        int feedback;
        int resolutions;

        FakeMutation(PlacedStructureState state) {
            this.state = Optional.of(state);
            target = Target.success(ANCHOR, state);
        }

        @Override public boolean serverSide() { return server; }
        @Override public boolean correctTool() { return tool; }
        @Override public boolean authorized() { return authorization; }
        @Override public Target resolve() { resolutions++; return target; }
        @Override public StructureCatalogue catalogue() { return ShrineMonolithDefinitions.catalogue(); }
        @Override public Optional<Family> family(PlacedStructureState ignored) { return Optional.of(family); }
        @Override public boolean assign(PlacedStructureState expected, PlacedStructureState replacement) {
            assignments++;
            if (!state.equals(Optional.of(expected)) || !assign) return false;
            state = Optional.of(replacement);
            return true;
        }
        @Override public Optional<PlacedStructureState> readback() { return state; }
        @Override public boolean synchronize() {
            synchronizations++;
            return synchronizationResults.isEmpty() || synchronizationResults.removeFirst();
        }
        @Override public boolean restore(PlacedStructureState expectedCurrent, PlacedStructureState previous) {
            restores++;
            if (!restore || !state.equals(Optional.of(expectedCurrent))) return false;
            state = Optional.of(previous);
            return true;
        }
        @Override public void successFeedback(BlockPos position, Variant selected) {
            feedback++;
            assertEquals(ANCHOR, position);
            assertEquals(state.orElseThrow().variantId(), selected.id());
        }
    }
}
