package com.seggellion.britannia_mod.structure.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
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

class ShrineVariantCycleServiceTest {
    private static final BlockPos ANCHOR = new BlockPos(31, 70, -17);
    private static final List<String> ORDER = List.of(
            "honesty", "compassion", "valor", "justice", "sacrifice",
            "honor", "spirituality", "humility", "chaos");

    @Test
    void exactNineStepOrderAndWrapAreCatalogueOwned() {
        FakeMutation mutation = new FakeMutation(state("honesty"));
        for (int index = 0; index < ORDER.size(); index++) {
            String current = ORDER.get(index);
            String expected = ORDER.get((index + 1) % ORDER.size());
            mutation.state = Optional.of(state(current));
            mutation.target = Target.success(ANCHOR, mutation.state.orElseThrow());
            assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
            assertEquals(expected, mutation.state.orElseThrow().variantId().value());
        }
        assertEquals(9, mutation.feedback);
        assertEquals(9, mutation.assignments);
    }

    @Test
    void successChangesExactlyVariantAndPreservesCompleteStructureState() {
        PlacedStructureState before = state("justice", Direction.WEST);
        FakeMutation mutation = new FakeMutation(before);
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        PlacedStructureState after = mutation.state.orElseThrow();
        assertEquals("sacrifice", after.variantId().value());
        assertNotEquals(before.variantId(), after.variantId());
        assertEquals(before.schemaVersion(), after.schemaVersion());
        assertEquals(before.familyId(), after.familyId());
        assertEquals(before.facing(), after.facing());
        assertEquals(before.footprint(), after.footprint());
        assertEquals(ANCHOR, mutation.feedbackPosition);
        assertEquals(1, mutation.feedback);
        assertEquals(1, mutation.synchronizations);
    }

    @Test
    void disabledVariantIsSkippedWithoutUsingDisplayNameOrder() {
        Family original = shrineFamily();
        List<Variant> changed = original.variants().stream()
                .map(variant -> variant.id().value().equals("compassion")
                        ? copyVariant(variant, variant.displayName(), variant.cyclePosition(), false, variant.familyId())
                        : variant)
                .toList();
        FakeMutation mutation = new FakeMutation(state("honesty"));
        mutation.family = copyFamily(original, changed, original.defaultVariant());
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        assertEquals("valor", mutation.state.orElseThrow().variantId().value());

        List<Variant> renamed = original.variants().stream()
                .map(variant -> copyVariant(variant,
                        DisplayName.resolved(variant.id().value(), "z." + variant.id().value(),
                                new StringBuilder(variant.id().value()).reverse().toString()),
                        variant.cyclePosition(), variant.enabled(), variant.familyId()))
                .toList();
        mutation = new FakeMutation(state("honesty"));
        mutation.family = copyFamily(original, renamed, original.defaultVariant());
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        assertEquals("compassion", mutation.state.orElseThrow().variantId().value());
    }

    @Test
    void missingCurrentAndNoAlternateFailWithoutMutationOrFeedback() {
        FakeMutation missing = new FakeMutation(state("unknown"));
        assertEquals(Result.CURRENT_VARIANT_MISSING, ShrineVariantCycleService.cycle(missing));
        assertUnchanged(missing, "unknown");

        Family original = shrineFamily();
        Variant honesty = original.variants().getFirst();
        FakeMutation single = new FakeMutation(state("honesty"));
        single.family = copyFamily(original, List.of(honesty), Optional.of(honesty.id()));
        assertEquals(Result.NO_ALTERNATE_ENABLED_VARIANT, ShrineVariantCycleService.cycle(single));
        assertUnchanged(single, "honesty");
    }

    @Test
    void invalidCycleDefinitionFailsAndCyclerExcludesIncompatibleCandidates() {
        Family original = shrineFamily();
        List<Variant> duplicate = new ArrayList<>(original.variants());
        duplicate.set(1, copyVariant(duplicate.get(1), duplicate.get(1).displayName(),
                0, true, duplicate.get(1).familyId()));
        FakeMutation invalid = new FakeMutation(state("honesty"));
        invalid.family = copyFamily(original, duplicate, original.defaultVariant());
        assertEquals(Result.INVALID_CYCLE_DEFINITION, ShrineVariantCycleService.cycle(invalid));
        assertUnchanged(invalid, "honesty");

        List<Variant> incompatible = new ArrayList<>(original.variants());
        Variant compassion = incompatible.get(1);
        incompatible.set(1, copyVariant(compassion, compassion.displayName(), compassion.cyclePosition(),
                true, new FamilyId("monolith")));
        FakeMutation wrongFamily = new FakeMutation(state("honesty"));
        wrongFamily.family = copyFamily(original, incompatible, original.defaultVariant());
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(wrongFamily));
        assertEquals("valor", wrongFamily.state.orElseThrow().variantId().value());
    }

    @Test
    void entryFailuresNeverResolveOrMutate() {
        FakeMutation client = new FakeMutation(state("honesty"));
        client.server = false;
        assertEquals(Result.CLIENT_SIDE, ShrineVariantCycleService.cycle(client));
        assertEquals(0, client.resolutions);

        FakeMutation wrongTool = new FakeMutation(state("honesty"));
        wrongTool.tool = false;
        assertEquals(Result.WRONG_TOOL, ShrineVariantCycleService.cycle(wrongTool));
        assertEquals(0, wrongTool.resolutions);

        FakeMutation unauthorized = new FakeMutation(state("honesty"));
        unauthorized.authorization = false;
        assertEquals(Result.UNAUTHORIZED, ShrineVariantCycleService.cycle(unauthorized));
        assertEquals(0, unauthorized.resolutions);
        assertUnchanged(client, "honesty");
        assertUnchanged(wrongTool, "honesty");
        assertUnchanged(unauthorized, "honesty");
    }

    @Test
    void everyTargetResolutionFailureIsPropagatedWithoutMutation() {
        List<Result> failures = List.of(
                Result.NOT_A_STRUCTURE_CELL, Result.INVALID_PART, Result.ANCHOR_CHUNK_UNAVAILABLE,
                Result.ANCHOR_MISSING, Result.ANCHOR_BLOCK_ENTITY_MISSING);
        for (Result failure : failures) {
            FakeMutation mutation = new FakeMutation(state("honesty"));
            mutation.target = Target.failure(failure, ANCHOR);
            assertEquals(failure, ShrineVariantCycleService.cycle(mutation));
            assertUnchanged(mutation, "honesty");
        }
    }

    @Test
    void nonShrineFamilyIsRejected() {
        FakeMutation mutation = new FakeMutation(new PlacedStructureState(
                new FamilyId("monolith"), new VariantId("diagnostic_missing_content"),
                Direction.NORTH, state("honesty").footprint()));
        assertEquals(Result.FAMILY_NOT_SHRINE, ShrineVariantCycleService.cycle(mutation));
        assertEquals(0, mutation.assignments);
    }

    @Test
    void assignmentFailureAndPartialAssignmentRestorePreviousState() {
        FakeMutation cleanFailure = new FakeMutation(state("honesty"));
        cleanFailure.assign = false;
        assertEquals(Result.STATE_ASSIGNMENT_FAILED, ShrineVariantCycleService.cycle(cleanFailure));
        assertUnchanged(cleanFailure, "honesty");

        FakeMutation partial = new FakeMutation(state("honesty"));
        partial.assign = false;
        partial.mutateWhenAssignFails = true;
        assertEquals(Result.STATE_ASSIGNMENT_FAILED, ShrineVariantCycleService.cycle(partial));
        assertUnchanged(partial, "honesty");
        assertEquals(1, partial.restores);
        assertEquals(1, partial.synchronizations);
    }

    @Test
    void synchronizationFailureRollsBackAndRetryAdvancesOnlyOnce() {
        FakeMutation mutation = new FakeMutation(state("honesty"));
        mutation.synchronizationResults = new ArrayList<>(List.of(false, true));
        assertEquals(Result.SYNCHRONIZATION_FAILED, ShrineVariantCycleService.cycle(mutation));
        assertUnchanged(mutation, "honesty");
        assertEquals(0, mutation.feedback);

        mutation.synchronizationResults = new ArrayList<>(List.of(true));
        assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
        assertEquals("compassion", mutation.state.orElseThrow().variantId().value());
        assertEquals(1, mutation.feedback);
    }

    @Test
    void failedRollbackIsExplicitAndEmitsNoSuccessFeedback() {
        FakeMutation mutation = new FakeMutation(state("honesty"));
        mutation.synchronizationResults = new ArrayList<>(List.of(false));
        mutation.restore = false;
        assertEquals(Result.ROLLBACK_FAILED, ShrineVariantCycleService.cycle(mutation));
        assertEquals(0, mutation.feedback);
    }

    @Test
    void everyTransitionRetainsSharedGeometryAndSelectsExpectedTexture() {
        Family family = shrineFamily();
        for (int index = 0; index < ORDER.size(); index++) {
            FakeMutation mutation = new FakeMutation(state(ORDER.get(index)));
            assertEquals(Result.SUCCESS, ShrineVariantCycleService.cycle(mutation));
            Variant selected = family.variants().get((index + 1) % ORDER.size());
            assertEquals(family.sharedGeometry().orElseThrow(), selected.model());
            assertEquals("textures/block/shrine/" + ORDER.get((index + 1) % ORDER.size()) + ".png",
                    selected.texture().location().orElseThrow().path());
        }
    }

    @Test
    void repeatedCatalogueAccessPreservesDeterministicStableOrderAndLocalization() {
        for (int attempt = 0; attempt < 5; attempt++) {
            List<Variant> variants = ShrineMonolithDefinitions.catalogue()
                    .family(ShrineMonolithDefinitions.SHRINE).orElseThrow().variants();
            assertEquals(ORDER, variants.stream().map(variant -> variant.id().value()).toList());
            assertTrue(variants.stream().allMatch(variant -> variant.displayName().translationKey().isPresent()));
        }
    }

    private static Family shrineFamily() {
        return ShrineMonolithDefinitions.catalogue().family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
    }

    private static PlacedStructureState state(String variant) {
        return state(variant, Direction.NORTH);
    }

    private static PlacedStructureState state(String variant, Direction facing) {
        return new PlacedStructureState(ShrineMonolithDefinitions.SHRINE, new VariantId(variant), facing,
                List.of(LocalOffset.ANCHOR, new LocalOffset(1, 0, 0),
                        new LocalOffset(0, 0, 1), new LocalOffset(1, 0, 1)));
    }

    private static Family copyFamily(Family family, List<Variant> variants, Optional<VariantId> defaultVariant) {
        return new Family(family.id(), family.displayName(), family.dimensions(), family.footprint(),
                family.anchorOffset(), family.placementMode(), family.collisionProfile(), family.renderOrigin(),
                family.renderOffsetVoxels(), family.geometryMode(), family.sharedGeometry(), defaultVariant,
                family.contentStatus(), variants);
    }

    private static Variant copyVariant(
            Variant variant, DisplayName displayName, int cyclePosition,
            boolean enabled, FamilyId familyId) {
        return new Variant(variant.id(), familyId, displayName, variant.dimensions(), variant.footprint(),
                variant.placementMode(), variant.collisionProfile(), variant.renderOrigin(),
                variant.renderOffsetVoxels(), variant.model(), variant.texture(), cyclePosition,
                enabled, variant.playerFacing(), variant.contentStatus());
    }

    private static void assertUnchanged(FakeMutation mutation, String variant) {
        assertEquals(variant, mutation.state.orElseThrow().variantId().value());
        assertEquals(0, mutation.feedback);
    }

    private static final class FakeMutation implements Mutation {
        boolean server = true;
        boolean tool = true;
        boolean authorization = true;
        boolean assign = true;
        boolean mutateWhenAssignFails;
        boolean restore = true;
        Optional<PlacedStructureState> state;
        Target target;
        Family family = shrineFamily();
        List<Boolean> synchronizationResults = new ArrayList<>(List.of(true));
        int assignments;
        int restores;
        int synchronizations;
        int feedback;
        int resolutions;
        BlockPos feedbackPosition;

        FakeMutation(PlacedStructureState state) {
            this.state = Optional.of(state);
            this.target = Target.success(ANCHOR, state);
        }

        @Override public boolean serverSide() { return server; }
        @Override public boolean correctTool() { return tool; }
        @Override public boolean authorized() { return authorization; }
        @Override public Target resolve() { resolutions++; return target; }
        @Override public StructureCatalogue catalogue() { return ShrineMonolithDefinitions.catalogue(); }
        @Override public Optional<Family> family(PlacedStructureState ignored) { return Optional.of(family); }
        @Override public boolean assign(PlacedStructureState expected, PlacedStructureState replacement) {
            assignments++;
            if (!state.equals(Optional.of(expected))) return false;
            if (assign || mutateWhenAssignFails) state = Optional.of(replacement);
            return assign;
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
            feedbackPosition = position;
            assertEquals(state.orElseThrow().variantId(), selected.id());
        }
    }
}
