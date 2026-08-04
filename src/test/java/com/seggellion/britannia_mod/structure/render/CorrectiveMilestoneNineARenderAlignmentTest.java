package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemStateAccess;
import com.seggellion.britannia_mod.structure.multiblock.ShrineRenderTransform;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CorrectiveMilestoneNineARenderAlignmentTest {
    private static final Path RESOURCES = Path.of("src/main/resources/assets/britannia_mod");
    private static final Path GEOMETRY = RESOURCES.resolve("geo/shrine.geo.json");
    private static final Path CREATIVE_TAB = Path.of(
            "src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java");
    private static final Path REGISTRY = Path.of(
            "src/main/java/com/seggellion/britannia_mod/registry/LargeStructureRegistry.java");
    private static final BlockPos ANCHOR = new BlockPos(30, 64, -20);
    private static final double EPSILON = 1.0E-8;
    private static ShrineItem shrineItem;

    @BeforeAll
    static void bootstrap() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        shrineItem = MilestoneTwoRegisteredTestContent.shrine();
    }

    @Test
    void priorGeometryFixtureReproducesOneBlockFacingDependentMisalignment() {
        AABB priorModel = new AABB(-24, 0, -8, 8, 16, 24);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB occupied = ShrineRenderTransform.occupiedShrineFootprintEnvelope(ANCHOR, facing);
            AABB rendered = ShrineRenderTransform.renderedShrineEnvelope(ANCHOR, facing, priorModel);
            assertFalse(contains(occupied, rendered), facing + " must reproduce the former overrun");
            double centerError = Math.abs(centerX(occupied) - centerX(rendered))
                    + Math.abs(centerZ(occupied) - centerZ(rendered));
            assertEquals(1.0, centerError, EPSILON, facing + " had one full block of lateral error");
        }
    }

    @Test
    void correctedJsonIsFiniteCenteredLowProfileAndUvSafe() throws IOException {
        Geometry geometry = readGeometry();
        assertEquals("geometry.britannia_mod.shrine_placeholder", geometry.identifier());
        assertEquals(new AABB(-7, 0, -7, 23, 16, 23), geometry.bounds());
        assertEquals(List.of(0.0, 0.0, 0.0), geometry.rootPivot());
        assertEquals(3, geometry.cubes().size());
        assertEquals(30.0, geometry.bounds().getXsize());
        assertEquals(30.0, geometry.bounds().getZsize());
        assertEquals(16.0, geometry.bounds().getYsize());

        Set<String> boxes = new HashSet<>();
        for (Cube cube : geometry.cubes()) {
            cube.origin().forEach(value -> assertTrue(Double.isFinite(value)));
            cube.size().forEach(value -> assertTrue(Double.isFinite(value) && value > 0));
            assertEquals(3, cube.origin().size());
            assertEquals(3, cube.size().size());
            assertEquals(2, cube.uv().size());
            assertFalse(cube.hasRotation());
            assertFalse(cube.hasInflate());
            assertTrue(cube.uv().get(0) >= 0 && cube.uv().get(1) >= 0);
            assertTrue(cube.uv().get(0) + 2 * (cube.size().get(0) + cube.size().get(2)) <= 128);
            assertTrue(cube.uv().get(1) + cube.size().get(2) + cube.size().get(1) <= 128);
            assertTrue(boxes.add(cube.origin() + ":" + cube.size()), "No duplicate cube is allowed");
        }
        assertEquals(1, geometry.boneCount());
        assertEquals(0, geometry.childBoneCount());
    }

    @Test
    void everyFacingEnvelopeIsCenteredInsetAndContainedByItsFourCells() throws IOException {
        AABB model = readGeometry().bounds();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB occupied = ShrineRenderTransform.occupiedShrineFootprintEnvelope(ANCHOR, facing);
            AABB rendered = ShrineRenderTransform.renderedShrineEnvelope(ANCHOR, facing, model);
            assertTrue(contains(occupied, rendered), facing.toString());
            assertEquals(centerX(occupied), centerX(rendered), EPSILON);
            assertEquals(centerZ(occupied), centerZ(rendered), EPSILON);
            assertEquals(1.875, rendered.getXsize(), EPSILON);
            assertEquals(1.875, rendered.getZsize(), EPSILON);
            assertEquals(1.0, rendered.getYsize(), EPSILON);
            assertEquals(1.0 / 16.0, rendered.minX - occupied.minX, EPSILON);
            assertEquals(1.0 / 16.0, occupied.maxX - rendered.maxX, EPSILON);
            assertEquals(1.0 / 16.0, rendered.minZ - occupied.minZ, EPSILON);
            assertEquals(1.0 / 16.0, occupied.maxZ - rendered.maxZ, EPSILON);
            assertEquals(ANCHOR.getY(), rendered.minY, EPSILON);
            assertEquals(ANCHOR.getY() + 1.0, rendered.maxY, EPSILON);
            assertEquals(0.0, ShrineRenderTransform.renderOffsetBlocks(
                    ShrineMonolithDefinitions.SHRINE), EPSILON);
            assertTrue(contains(ShrineRenderTransform.worldBounds(ANCHOR), rendered));
        }
    }

    @Test
    void correctedEnvelopeNeverTouchesAnyOfEightPerimeterStairCells() throws IOException {
        AABB model = readGeometry().bounds();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB rendered = ShrineRenderTransform.renderedShrineEnvelope(ANCHOR, facing, model);
            Set<BlockPos> occupied = occupiedCells(facing);
            Set<BlockPos> perimeter = perimeter(occupied);
            assertEquals(4, occupied.size());
            assertEquals(8, perimeter.size());
            Map<BlockPos, BlockState> stairs = new HashMap<>();
            int index = 0;
            for (BlockPos position : perimeter) {
                BlockState state = representativeStair(index++);
                stairs.put(position, state);
                assertFalse(rendered.intersects(new AABB(position)), facing + " enters " + position);
            }
            Map<BlockPos, BlockState> snapshot = Map.copyOf(stairs);
            for (var variant : ShrineMonolithDefinitions.catalogue()
                    .family(ShrineMonolithDefinitions.SHRINE).orElseThrow().variants()) {
                assertEquals(rendered, ShrineRenderTransform.renderedShrineEnvelope(ANCHOR, facing, model));
                assertEquals(snapshot, stairs, variant.id().value());
            }
            assertTrue(stairs.values().stream().anyMatch(state -> state.getValue(StairBlock.HALF) == Half.TOP));
            assertTrue(stairs.values().stream().anyMatch(state -> state.getValue(StairBlock.HALF) == Half.BOTTOM));
            assertTrue(stairs.values().stream().anyMatch(
                    state -> state.getValue(StairBlock.SHAPE) == StairsShape.INNER_LEFT));
            assertTrue(stairs.values().stream().anyMatch(
                    state -> state.getValue(StairBlock.SHAPE) == StairsShape.OUTER_RIGHT));
        }
    }

    @Test
    void oneSharedGeometryServesAllNineTexturesWithoutChangingMappings() {
        var shrine = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
        assertEquals(9, shrine.variants().size());
        assertEquals(1, shrine.variants().stream()
                .map(variant -> variant.model().logicalIdentity()).distinct().count());
        assertEquals(List.of(
                "textures/block/shrine/honesty.png",
                "textures/block/shrine/compassion.png",
                "textures/block/shrine/valor.png",
                "textures/block/shrine/justice.png",
                "textures/block/shrine/sacrifice.png",
                "textures/block/shrine/honor.png",
                "textures/block/shrine/spirituality.png",
                "textures/block/shrine/humility.png",
                "textures/block/shrine/chaos.png"),
                shrine.variants().stream()
                        .map(variant -> variant.texture().location().orElseThrow().path()).toList());
    }

    @Test
    void protectedAssetsRetainTheirApprovedHashes() throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("geo/shrine_missing.geo.json", "460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781"),
                Map.entry("animations/shrine.animation.json", "F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E"),
                Map.entry("models/item/shrine.json", "829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985"),
                Map.entry("textures/block/shrine/honesty.png", "D35747568A37960736C20F8359F55043B19739FC7D1FAB34144F8F971C36BCCC"),
                Map.entry("textures/block/shrine/compassion.png", "79160E8AF141E4395A64D64439F7FBF0C2170D78073A136E6CBEF8A130FC87BC"),
                Map.entry("textures/block/shrine/valor.png", "A748C870317998F911AC328960685F4B41AE8330635DC839F35D27EC6ACA4A1F"),
                Map.entry("textures/block/shrine/justice.png", "5F01D14F16870EBA66C6A4C3E2F19C919E403A5DCDDC12037904285C825B1B8B"),
                Map.entry("textures/block/shrine/sacrifice.png", "E4C56B44DC44C749DB10E2D34824DCF0079774FAAF9C2368330CC57036B4EEC4"),
                Map.entry("textures/block/shrine/honor.png", "ADC2A67CFA7476D4C1D18A7C49E9F1937D552099FCFE9F1D3C821B832135D381"),
                Map.entry("textures/block/shrine/spirituality.png", "D50CF726BD459C85313A9173E708C49C3ADC72559D0EABC9458FDFED8FF05CF1"),
                Map.entry("textures/block/shrine/humility.png", "E201645E5D9058E28022B22904114E09823E736DDE8021D2E4DA2CE9E558AC8A"),
                Map.entry("textures/block/shrine/chaos.png", "D8B2FDEB4158BBF86A053CDD383E532569F4DDDAEFF178D2472F5ABC2507EDC3"),
                Map.entry("geo/monolith_diagnostic.geo.json", "0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C"),
                Map.entry("geo/monolith_diagnostic_alternate.geo.json", "B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC"),
                Map.entry("textures/block/monolith/diagnostic_stone.png", "FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4"),
                Map.entry("textures/block/monolith/diagnostic_alternate_stone.png", "E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A"),
                Map.entry("animations/monolith.animation.json", "D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530"),
                Map.entry("models/item/monolith.json", "E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED"));
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), sha256(RESOURCES.resolve(entry.getKey())), entry.getKey());
        }
        assertEquals("05C52F184101C5AA62EEE515EB3BB285975FAAE2744EAC3381512000F0EEE62E",
                sha256(GEOMETRY));
    }

    @Test
    void existingDecorTabExposesExactlyOneExplicitHonestyShrineStack() throws IOException {
        ItemStack stack = CreativeTabRegistry.shrineCreativeStack(shrineItem);
        assertEquals(shrineItem, stack.getItem());
        assertEquals(ShrineItemStateAccess.defaultState(),
                shrineItem.stateAccess().read(stack).orElseThrow());
        var validation = shrineItem.stateAccess().validateForPlacement(
                stack, ShrineMonolithDefinitions.catalogue());
        assertTrue(validation.valid());
        assertFalse(validation.usedDefault(), "Creative stack carries the explicit approved default");

        String source = Files.readString(CREATIVE_TAB);
        String decor = source.substring(source.indexOf("CREATIVE_DECOR_TAB"),
                source.indexOf("CREATIVE_ITEMS_TAB"));
        assertEquals(1, occurrences(source, "LargeStructureRegistry.SHRINE.get()"));
        assertEquals(1, occurrences(decor, "shrineCreativeStack(LargeStructureRegistry.SHRINE.get())"));
        assertEquals(0, occurrences(source, "LargeStructureRegistry.MONOLITH.get()"));
        assertEquals(5, occurrences(source, "DeferredHolder<CreativeModeTab, CreativeModeTab>"));

        String registry = Files.readString(REGISTRY);
        assertEquals(1, occurrences(registry, "SHRINE = ITEMS.register("));
        assertEquals(0, occurrences(registry, "LARGE_STRUCTURE_ANCHOR_ITEM"));
        assertEquals(0, occurrences(registry, "LARGE_STRUCTURE_PART_ITEM"));
    }

    private static Geometry readGeometry() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(GEOMETRY)).getAsJsonObject();
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        assertEquals(1, geometries.size());
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals(128, description.get("texture_width").getAsInt());
        assertEquals(128, description.get("texture_height").getAsInt());
        JsonArray bones = geometry.getAsJsonArray("bones");
        assertEquals(1, bones.size());
        JsonObject rootBone = bones.get(0).getAsJsonObject();
        List<Double> pivot = numbers(rootBone.getAsJsonArray("pivot"));
        List<Cube> cubes = new ArrayList<>();
        AABB bounds = null;
        for (var element : rootBone.getAsJsonArray("cubes")) {
            JsonObject object = element.getAsJsonObject();
            List<Double> origin = numbers(object.getAsJsonArray("origin"));
            List<Double> size = numbers(object.getAsJsonArray("size"));
            Cube cube = new Cube(origin, size, numbers(object.getAsJsonArray("uv")),
                    object.has("rotation"), object.has("inflate"));
            cubes.add(cube);
            AABB cubeBounds = new AABB(
                    origin.get(0), origin.get(1), origin.get(2),
                    origin.get(0) + size.get(0), origin.get(1) + size.get(1),
                    origin.get(2) + size.get(2));
            bounds = bounds == null ? cubeBounds : union(bounds, cubeBounds);
        }
        int childBones = 0;
        for (var element : bones) {
            if (element.getAsJsonObject().has("parent")) childBones++;
        }
        return new Geometry(description.get("identifier").getAsString(), pivot,
                List.copyOf(cubes), bounds, bones.size(), childBones);
    }

    private static List<Double> numbers(JsonArray array) {
        List<Double> values = new ArrayList<>(array.size());
        array.forEach(value -> values.add(value.getAsDouble()));
        return List.copyOf(values);
    }

    private static AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX), Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ), Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY), Math.max(first.maxZ, second.maxZ));
    }

    private static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX - EPSILON && inner.minY >= outer.minY - EPSILON
                && inner.minZ >= outer.minZ - EPSILON && inner.maxX <= outer.maxX + EPSILON
                && inner.maxY <= outer.maxY + EPSILON && inner.maxZ <= outer.maxZ + EPSILON;
    }

    private static double centerX(AABB box) {
        return (box.minX + box.maxX) / 2.0;
    }

    private static double centerZ(AABB box) {
        return (box.minZ + box.maxZ) / 2.0;
    }

    private static Set<BlockPos> occupiedCells(Direction facing) {
        Set<BlockPos> result = new HashSet<>();
        var footprint = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.SHRINE).orElseThrow().footprint();
        for (var offset : footprint) {
            result.add(com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner
                    .worldPosition(ANCHOR, facing, offset));
        }
        return Set.copyOf(result);
    }

    private static Set<BlockPos> perimeter(Set<BlockPos> occupied) {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos cell : occupied) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = cell.relative(direction);
                if (!occupied.contains(candidate)) result.add(candidate.immutable());
            }
        }
        return Set.copyOf(result);
    }

    private static BlockState representativeStair(int index) {
        Direction facing = Direction.Plane.HORIZONTAL.stream().toList().get(index % 4);
        Half half = index % 2 == 0 ? Half.BOTTOM : Half.TOP;
        StairsShape shape = switch (index % 4) {
            case 0 -> StairsShape.STRAIGHT;
            case 1 -> StairsShape.INNER_LEFT;
            case 2 -> StairsShape.OUTER_LEFT;
            default -> StairsShape.OUTER_RIGHT;
        };
        return Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, half)
                .setValue(StairBlock.SHAPE, shape);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private record Geometry(
            String identifier,
            List<Double> rootPivot,
            List<Cube> cubes,
            AABB bounds,
            int boneCount,
            int childBoneCount) {
    }

    private record Cube(
            List<Double> origin,
            List<Double> size,
            List<Double> uv,
            boolean hasRotation,
            boolean hasInflate) {
    }
}
