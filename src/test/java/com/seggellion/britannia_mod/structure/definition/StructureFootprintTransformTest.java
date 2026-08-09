package com.seggellion.britannia_mod.structure.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.VoxelOffset;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.DisplayName;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.HorizontalFacing;
import com.seggellion.britannia_mod.structure.definition.StructureTransform.WorldPosition;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructureFootprintTransformTest {
    private static final WorldPosition ANCHOR = new WorldPosition(10, 64, 20);
    private static final LocalOffset SAMPLE = new LocalOffset(1, 2, 1);

    @Test
    void shrineFootprintContainsExactlyFourUniqueOffsets() {
        var footprint = DefinitionFixtures.shrine().footprint();
        assertEquals(4, footprint.size());
        assertEquals(4, new HashSet<>(footprint).size());
    }

    @Test
    void monolithFootprintContainsExactlyEighteenUniqueOffsets() {
        var footprint = DefinitionFixtures.monolith().footprint();
        assertEquals(18, footprint.size());
        assertEquals(18, new HashSet<>(footprint).size());
    }

    @Test
    void eachProductionFootprintContainsAnchorExactlyOnce() {
        for (var family : ShrineMonolithDefinitions.catalogue().families()) {
            assertEquals(1, family.footprint().stream().filter(LocalOffset.ANCHOR::equals).count());
        }
    }

    @Test
    void footprintOrderingIsDeterministicYThenZThenX() {
        assertEquals(List.of(
                new LocalOffset(0, 0, 0), new LocalOffset(1, 0, 0),
                new LocalOffset(0, 0, 1), new LocalOffset(1, 0, 1)),
                DefinitionFixtures.shrine().footprint());
        assertEquals(new LocalOffset(2, 2, 1), DefinitionFixtures.monolith().footprint().getLast());
        assertEquals(
                DefinitionFixtures.monolith().footprint(),
                StructureGeometry.rectangularFootprint(ShrineMonolithDefinitions.MONOLITH_DIMENSIONS));
    }

    @Test
    void northFacingUsesWestForRightAndSouthForAway() {
        assertEquals(new WorldPosition(9, 66, 21),
                StructureTransform.worldPosition(ANCHOR, HorizontalFacing.NORTH, SAMPLE));
    }

    @Test
    void eastFacingUsesNorthForRightAndWestForAway() {
        assertEquals(new WorldPosition(9, 66, 19),
                StructureTransform.worldPosition(ANCHOR, HorizontalFacing.EAST, SAMPLE));
    }

    @Test
    void southFacingUsesEastForRightAndNorthForAway() {
        assertEquals(new WorldPosition(11, 66, 19),
                StructureTransform.worldPosition(ANCHOR, HorizontalFacing.SOUTH, SAMPLE));
    }

    @Test
    void westFacingUsesSouthForRightAndEastForAway() {
        assertEquals(new WorldPosition(11, 66, 21),
                StructureTransform.worldPosition(ANCHOR, HorizontalFacing.WEST, SAMPLE));
    }

    @Test
    void reverseTransformsRecoverAnchorAndLocalOffsetForEveryFacing() {
        for (HorizontalFacing facing : HorizontalFacing.values()) {
            for (LocalOffset offset : DefinitionFixtures.monolith().footprint()) {
                WorldPosition part = StructureTransform.worldPosition(ANCHOR, facing, offset);
                assertEquals(ANCHOR, StructureTransform.anchorPosition(part, facing, offset));
                assertEquals(offset, StructureTransform.localOffset(ANCHOR, facing, part).orElseThrow());
            }
        }
    }

    @Test
    void monolithSixteenVoxelYOffsetConvertsToExactlyOneBlock() {
        assertEquals(1.0, ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.toBlockUnits().y());
        assertEquals(0.0, ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.toBlockUnits().x());
        assertEquals(0.0, ShrineMonolithDefinitions.MONOLITH_RENDER_OFFSET.toBlockUnits().z());
    }

    @Test
    void renderOffsetDoesNotAlterGeneratedFootprint() {
        var before = StructureGeometry.rectangularFootprint(ShrineMonolithDefinitions.MONOLITH_DIMENSIONS);
        new VoxelOffset(0, 16, 0).toBlockUnits();
        var after = StructureGeometry.rectangularFootprint(ShrineMonolithDefinitions.MONOLITH_DIMENSIONS);
        assertEquals(before, after);
        assertEquals(LocalOffset.ANCHOR, after.getFirst());
    }

    @Test
    void groupAndDisplayNamesDoNotDetermineFootprint() {
        var original = DefinitionFixtures.shrine();
        var renamed = DefinitionFixtures.withDisplay(
                original, DisplayName.unresolved("unrelated_group_name", "Anything"));
        assertNotEquals(original.displayName(), renamed.displayName());
        assertEquals(original.footprint(), renamed.footprint());
        assertTrue(StructureDefinitionValidator.validate(renamed).valid());
    }
}
