package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The asset half of the thin-wall junction contract.
 *
 * <p>{@link ThinWall} encodes a corner or T-junction as nothing but {@code FACING} plus one
 * {@code CORNER} flag, which only works because every family's assets keep one convention: the
 * straight model for {@code facing=X} hugs edge {@code X}, and the corner model for
 * {@code facing=X} hugs exactly the pair {@code {X, counter-clockwise-of-X}}. The runtime
 * derivation picks the facing whose pair matches the neighbourhood, so a family whose art drifted
 * off this convention would connect its junctions wrongly again - silently, and only in game.
 * This suite reads the blockstates and models straight from the resources and holds every variant
 * to that convention, the way the alignment suite holds the runtime to its half.
 *
 * <p>An edge counts as hugged when the model's boxes, after the variant's own {@code y} rotation,
 * are flush against it and their union runs the full sixteen pixels along it - full length,
 * because a junction meets its neighbours at the block faces, and a panel that stops short leaves
 * the join open exactly the way the original defect did.
 */
class ThinWallCornerModelContractTest {

    /**
     * Every family registered in {@code BlockRegistry} as an adaptive {@link ThinWall} - the ones
     * whose corners and junctions re-derive their facing, so their corner art has to keep the
     * convention. Keep this in step with the registry, the same way
     * {@code tools/gen_expected_states.py} keeps its lists in step: a family added there and not
     * here simply is not held to the convention.
     */
    private static final List<String> FAMILIES = List.of(
        "oak_wall_bottom", "oak_wall_top",
        "brick_wall_bottom", "brick_wall_top",
        "stone_wall_bottom", "stone_wall_top",
        "dark_stone_wall_bottom", "dark_stone_wall_half",
        "cobblestone_foundation", "cobblestone_wall_bottom", "cobblestone_wall_top",
        "birch_wall", "log_wall",
        "plaster_stone_foundation", "plaster_stone_wall_bottom", "plaster_stone_wall_top",
        "plaster_wood_foundation", "plaster_wood_wall_bottom", "plaster_wood_wall_top");

    /**
     * The {@link ThinWall.ConnectionRule#FIXED_FACING} registrations. They draw no junction art
     * and are exempt from the corner convention, but they carry the same state space as every
     * other {@code ThinWall} - facing, corner, filled, style 0..2 - and a pivot or a decorator
     * tool can put them in any of it, so every state still has to resolve to a model.
     */
    private static final List<String> STRAIGHT_ONLY = List.of(
        "window_1x1", "window_cross_1x1", "window_birch_1x1",
        "curtain_top", "curtain_foundation", "curtain_bottom",
        "stone_arch", "dark_stone_arch",
        "stone_wall_half",
        "corral_wall", "corral_wall_pole", "corral_corner_fence");

    /** How far off flush a panel may sit, and how much of the sixteen pixels it may miss. */
    private static final double FLUSH = 1.0D;
    private static final double EPSILON = 1.0E-6D;

    @Test
    void everyVariantDrawsPanelsOnExactlyTheEdgesItsStateNames() throws IOException {
        for (String family : FAMILIES) {
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                Map<String, String> properties = propertiesOf(variant.key());
                Direction facing = Direction.byName(properties.get("facing"));
                if (facing == null) {
                    continue; // not a facing-keyed variant; nothing to hold it to
                }

                Set<Direction> wanted = EnumSet.of(facing);
                if ("true".equals(properties.get("corner"))) {
                    wanted.add(facing.getCounterClockWise());
                }

                assertEquals(names(wanted), names(huggedEdges(WindowArt.solidElements(variant))),
                    family + " " + variant.key() + " -> " + variant.model()
                        + ": the model's full-length panels do not match the state's edges");
            }
        }
    }

    @Test
    void everyFamilyHasCornerArtForAllFourFacings() throws IOException {
        for (String family : FAMILIES) {
            Set<Direction> straight = EnumSet.noneOf(Direction.class);
            Set<Direction> corner = EnumSet.noneOf(Direction.class);
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                Map<String, String> properties = propertiesOf(variant.key());
                Direction facing = Direction.byName(properties.get("facing"));
                if (facing == null) {
                    continue;
                }
                ("true".equals(properties.get("corner")) ? corner : straight).add(facing);
            }
            assertEquals(4, straight.size(), family + ": straight art is missing a facing");
            assertEquals(4, corner.size(), family + ": corner art is missing a facing - the"
                + " derivation would have nothing to draw for one junction orientation");
        }
    }

    /**
     * Every state a {@code ThinWall} can actually be in has to match at least one variant key, or
     * the game renders the missing-model checkerboard. The properties a key does not mention are
     * wildcards, so the cheap way for a family with one look is to mention less - and the trap
     * this guards is a key set that mentions a property but not all of its values:
     * {@code style=0}/{@code style=1} keys leave {@code style=2} unmapped even though the
     * decorator tool cycles straight into it, and {@code corner=false} keys leave every pivot
     * unmapped. Both happened.
     */
    @Test
    void everyReachableStateResolvesToAModel() throws IOException {
        List<String> all = new ArrayList<>(FAMILIES);
        all.addAll(STRAIGHT_ONLY);
        for (String family : all) {
            List<Map<String, String>> keys = new ArrayList<>();
            for (WindowArt.Variant variant : WindowArt.variantsOf(family)) {
                keys.add(propertiesOf(variant.key()));
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (String corner : List.of("false", "true")) {
                    for (String filled : List.of("false", "true")) {
                        for (int style = 0; style <= 2; style++) {
                            Map<String, String> state = Map.of(
                                "facing", facing.getSerializedName(),
                                "corner", corner,
                                "filled", filled,
                                "style", String.valueOf(style));
                            assertTrue(keys.stream().anyMatch(key -> matches(key, state)),
                                family + ": state " + state + " matches no variant - the game"
                                    + " would render it as the missing model");
                        }
                    }
                }
            }
        }
    }

    /** Variant-key semantics: every property the key mentions must agree; the rest are wildcards. */
    private static boolean matches(Map<String, String> key, Map<String, String> state) {
        return key.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(state.get(entry.getKey())));
    }

    /* ─── reading the art ────────────────────────────────────── */

    private static Map<String, String> propertiesOf(String variantKey) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String pair : variantKey.split(",")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                properties.put(parts[0], parts[1]);
            }
        }
        return properties;
    }

    /** The edges whose full sixteen-pixel length the boxes cover while flush against them. */
    private static Set<Direction> huggedEdges(List<WindowArt.Box> boxes) {
        Set<Direction> hugged = EnumSet.noneOf(Direction.class);
        for (Direction edge : Direction.Plane.HORIZONTAL) {
            List<double[]> along = new ArrayList<>();
            for (WindowArt.Box box : boxes) {
                boolean flush = switch (edge) {
                    case NORTH -> box.z0() <= FLUSH;
                    case SOUTH -> box.z1() >= WindowArt.BLOCK - FLUSH;
                    case WEST -> box.x0() <= FLUSH;
                    case EAST -> box.x1() >= WindowArt.BLOCK - FLUSH;
                    default -> false;
                };
                if (flush) {
                    along.add(edge.getAxis() == Direction.Axis.Z
                        ? new double[] {box.x0(), box.x1()}
                        : new double[] {box.z0(), box.z1()});
                }
            }
            if (coversFullLength(along)) {
                hugged.add(edge);
            }
        }
        return hugged;
    }

    private static boolean coversFullLength(List<double[]> intervals) {
        intervals.sort((a, b) -> Double.compare(a[0], b[0]));
        double reach = 0.0D;
        for (double[] interval : intervals) {
            if (interval[0] > reach + EPSILON) {
                return false;
            }
            reach = Math.max(reach, interval[1]);
        }
        return reach >= WindowArt.BLOCK - EPSILON;
    }

    private static Set<String> names(Set<Direction> directions) {
        Set<String> names = new TreeSet<>();
        for (Direction direction : directions) {
            names.add(direction.getSerializedName());
        }
        return names;
    }
}
