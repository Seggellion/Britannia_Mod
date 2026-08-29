package com.seggellion.britannia_mod.crate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Packing a column that starts partway up, because it is standing on something.
 *
 * <h2>What is different from an ordinary column</h2>
 *
 * <p>A freestanding column begins on its root cell's floor and everything is non-negative. One resting
 * on a large crate begins at that crate's lid, thirteen voxels <em>below</em> the first cell it is
 * allowed to own, so its lowest crates have negative bases and belong to cell {@code -1}. Java's
 * integer division truncates toward zero, which would put every one of them in cell {@code 0}
 * alongside the crates actually there — the arithmetic has to floor, and these hold it to that.
 */
class CrateStackFoundationPackingTest {

    @BeforeAll
    static void bootstrap() {
        CrateStackTestSupport.bootstrap();
    }

    /** What a large crate's lid works out to, relative to the cell the column above it roots in. */
    private static final int FOUNDATION_ORIGIN = -1300;

    private static final int SMALL = 715;
    private static final int MEDIUM = 1151;

    private static List<LogicalCrate> crates(CrateVariant... variants) {
        List<LogicalCrate> built = new ArrayList<>();
        for (int index = 0; index < variants.length; index++) {
            built.add(new LogicalCrate(index, variants[index], Direction.NORTH));
        }
        return built;
    }

    private static CrateStackLayout founded(CrateVariant... variants) {
        return CrateStackLayout.of(crates(variants), FOUNDATION_ORIGIN);
    }

    @Nested
    @DisplayName("physical bases")
    class Bases {

        @Test
        @DisplayName("the bottom crate rests exactly on the foundation")
        void bottomCrateRestsOnTheLid() {
            CrateStackLayout layout = founded(CrateVariant.SMALL);

            assertEquals(FOUNDATION_ORIGIN, layout.originHundredths());
            assertEquals(FOUNDATION_ORIGIN, layout.placements().get(0).baseHundredths(),
                    "the bottom crate has to start at the lid, not at the cell floor above it");
            assertEquals(FOUNDATION_ORIGIN + SMALL, layout.placements().get(0).topHundredths());
        }

        @Test
        @DisplayName("crates above it still touch exactly")
        void cratesStackFlush() {
            CrateStackLayout layout =
                    founded(CrateVariant.SMALL, CrateVariant.MEDIUM, CrateVariant.SMALL);
            List<CratePlacement> packed = layout.placements();

            assertEquals(packed.get(0).topHundredths(), packed.get(1).baseHundredths());
            assertEquals(packed.get(1).topHundredths(), packed.get(2).baseHundredths());
            assertEquals(FOUNDATION_ORIGIN + SMALL + MEDIUM + SMALL, layout.totalHundredths());
        }

        @Test
        @DisplayName("the height of the crates is what it would be anywhere else")
        void crateHeightIgnoresTheFoundation() {
            assertEquals(SMALL + SMALL, founded(CrateVariant.SMALL, CrateVariant.SMALL)
                    .crateHeightHundredths());
            assertEquals(SMALL + SMALL, CrateStackLayout.of(
                    crates(CrateVariant.SMALL, CrateVariant.SMALL)).crateHeightHundredths());
        }
    }

    @Nested
    @DisplayName("world cells")
    class Cells {

        @Test
        @DisplayName("a column always owns the cell its block entity stands in")
        void alwaysOwnsItsRoot() {
            // Every crate hangs below the root cell, but the block entity is still up there.
            assertEquals(1, founded(CrateVariant.SMALL).requiredCells());
            assertEquals(1, founded(CrateVariant.MEDIUM).requiredCells());
        }

        @Test
        @DisplayName("a foundation buys thirteen voxels before a second cell is needed")
        void secondCellComesLater() {
            // Freestanding, the third small crate crosses into a second cell at 21.45 voxels.
            assertEquals(2, CrateStackLayout.of(
                    crates(CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL))
                    .requiredCells());
            // Founded, the column starts 13 voxels lower, so four fit before the boundary.
            assertEquals(1, founded(CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL,
                    CrateVariant.SMALL).requiredCells());
            assertEquals(2, founded(CrateVariant.SMALL, CrateVariant.SMALL, CrateVariant.SMALL,
                    CrateVariant.SMALL, CrateVariant.SMALL).requiredCells());
        }

        @Test
        @DisplayName("mediums cross later too")
        void mediumBoundary() {
            assertEquals(1, founded(CrateVariant.MEDIUM, CrateVariant.MEDIUM).requiredCells());
            assertEquals(2, founded(CrateVariant.MEDIUM, CrateVariant.MEDIUM, CrateVariant.MEDIUM)
                    .requiredCells());
        }

        @Test
        @DisplayName("crates below the root belong to the cell below it, not to cell zero")
        void lowCratesLandInTheFoundationCell() {
            CratePlacement bottom = founded(CrateVariant.SMALL, CrateVariant.SMALL)
                    .placements().get(0);

            assertEquals(-1, bottom.firstCell(),
                    "truncating division would say cell 0 and put this crate inside the column");
            assertEquals(-1, bottom.lastCell());
            assertTrue(bottom.occupiesCell(-1));
            assertFalse(bottom.occupiesCell(0));
        }

        @Test
        @DisplayName("a crate spanning the root's floor is drawn from both cells")
        void crossingCrateAppearsInBoth() {
            CratePlacement second = founded(CrateVariant.SMALL, CrateVariant.SMALL)
                    .placements().get(1);

            assertTrue(second.crossesCellBoundary());
            assertTrue(second.occupiesCell(-1));
            assertTrue(second.occupiesCell(0));
        }

        @Test
        @DisplayName("the foundation cell sees the crate three voxels up, where the lid is")
        void localBaseInsideTheFoundationCell() {
            CratePlacement bottom = founded(CrateVariant.SMALL).placements().get(0);

            assertEquals(300, bottom.localBaseHundredths(-1),
                    "the large crate's lid is three voxels into the cell above its anchor");
        }
    }

    @Nested
    @DisplayName("slices")
    class Slices {

        @Test
        @DisplayName("the foundation cell is handed the crates resting on it")
        void foundationCellDrawsTheOverhang() {
            List<LogicalCrate> built = crates(CrateVariant.SMALL, CrateVariant.SMALL);
            CrateStackLayout layout = CrateStackLayout.of(built, FOUNDATION_ORIGIN);

            CrateStackSlice overhang = CrateStackSlice.of(layout, built, -1);
            assertEquals(2, overhang.entries().size(),
                    "both crates reach into the cell the foundation owns");
            assertEquals(300, overhang.entries().get(0).artBaseHundredths(),
                    "the bottom crate's art starts on the lid");
        }

        @Test
        @DisplayName("the column's own cell draws only what reaches it")
        void rootCellDrawsTheRest() {
            List<LogicalCrate> built = crates(CrateVariant.SMALL, CrateVariant.SMALL);
            CrateStackLayout layout = CrateStackLayout.of(built, FOUNDATION_ORIGIN);

            CrateStackSlice root = CrateStackSlice.of(layout, built, 0);
            assertEquals(1, root.entries().size(),
                    "only the upper crate crosses the root cell's floor");
        }

        @Test
        @DisplayName("every crate is drawn by exactly one cell or shared between two")
        void everyCrateIsDrawnSomewhere() {
            List<LogicalCrate> built =
                    crates(CrateVariant.SMALL, CrateVariant.MEDIUM, CrateVariant.SMALL);
            CrateStackLayout layout = CrateStackLayout.of(built, FOUNDATION_ORIGIN);

            List<Integer> drawn = new ArrayList<>();
            for (int cell = -1; cell < layout.requiredCells(); cell++) {
                CrateStackSlice.of(layout, built, cell).entries()
                        .forEach(entry -> {
                            if (!drawn.contains(entry.crateId())) {
                                drawn.add(entry.crateId());
                            }
                        });
            }
            assertEquals(3, drawn.size(), "a crate no cell draws is a crate nobody can see");
        }
    }

    @Nested
    @DisplayName("freestanding columns are untouched")
    class Unchanged {

        @Test
        @DisplayName("an origin of zero packs exactly as before")
        void zeroOriginIsTheOldBehaviour() {
            CrateStackLayout explicit =
                    CrateStackLayout.of(crates(CrateVariant.SMALL, CrateVariant.MEDIUM), 0);
            CrateStackLayout implied =
                    CrateStackLayout.of(crates(CrateVariant.SMALL, CrateVariant.MEDIUM));

            assertEquals(implied.totalHundredths(), explicit.totalHundredths());
            assertEquals(implied.requiredCells(), explicit.requiredCells());
            assertEquals(implied.placements(), explicit.placements());
            assertEquals(0, implied.originHundredths());
        }
    }
}
