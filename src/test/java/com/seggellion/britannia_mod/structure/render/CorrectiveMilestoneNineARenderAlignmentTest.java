package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry;
import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.item.MonolithItem;
import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
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
    private static final Path RENDERER = Path.of(
            "src/main/java/com/seggellion/britannia_mod/client/renderer/shrine/ShrineRenderer.java");
    private static final Path GRANITE_LAYER = Path.of(
            "src/main/java/com/seggellion/britannia_mod/client/renderer/shrine/ShrineGraniteRenderLayer.java");
    private static final BlockPos ANCHOR = new BlockPos(30, 64, -20);
    private static final double EPSILON = 1.0E-8;
    private static ShrineItem shrineItem;
    private static MonolithItem monolithItem;

    @BeforeAll
    static void bootstrap() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        shrineItem = MilestoneTwoRegisteredTestContent.shrine();
        monolithItem = MilestoneTwoRegisteredTestContent.monolith();
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
    void correctedJsonIsFourSquareFlatShrineWithGraniteRimAndSafeFaceUvs() throws IOException {
        Geometry geometry = readGeometry();
        assertEquals("geometry.britannia_mod.shrine", geometry.identifier());
        assertEquals(new AABB(-8, 0, -8, 24, 15, 24), geometry.bounds());
        assertEquals(List.of(0.0, 0.0, 0.0), geometry.rootPivot());
        assertEquals(8, geometry.cubes().size());
        assertEquals(32.0, geometry.bounds().getXsize());
        assertEquals(32.0, geometry.bounds().getZsize());
        assertEquals(15.0, geometry.bounds().getYsize());

        Set<List<Double>> surfaceOrigins = Set.of(
                List.of(-6.0, 0.0, -6.0), List.of(8.0, 0.0, -6.0),
                List.of(-6.0, 0.0, 8.0), List.of(8.0, 0.0, 8.0));
        assertEquals(surfaceOrigins,
                geometry.cubes().stream()
                        .filter(cube -> cube.size().equals(List.of(14.0, 14.0, 14.0)))
                        .map(Cube::origin).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(
                List.of(-8.0, 0.0, -8.0), List.of(-8.0, 0.0, 22.0),
                List.of(-8.0, 0.0, -6.0), List.of(22.0, 0.0, -6.0)),
                geometry.cubes().stream()
                        .filter(cube -> cube.size().equals(List.of(32.0, 15.0, 2.0))
                                || cube.size().equals(List.of(2.0, 15.0, 28.0)))
                        .map(Cube::origin).collect(java.util.stream.Collectors.toSet()));

        Map<List<Double>, List<Double>> correctedTopUvOrigins = Map.of(
                List.of(-6.0, 0.0, -6.0), List.of(0.0, 64.0),
                List.of(8.0, 0.0, -6.0), List.of(64.0, 64.0),
                List.of(-6.0, 0.0, 8.0), List.of(0.0, 0.0),
                List.of(8.0, 0.0, 8.0), List.of(64.0, 0.0));
        geometry.cubes().stream().filter(cube -> surfaceOrigins.contains(cube.origin()))
                .forEach(cube -> {
                    assertEquals(correctedTopUvOrigins.get(cube.origin()), cube.uv().get("up").origin());
                    assertEquals(List.of(64.0, 64.0), cube.uv().get("up").size());
                });

        Set<String> boxes = new HashSet<>();
        for (Cube cube : geometry.cubes()) {
            cube.origin().forEach(value -> assertTrue(Double.isFinite(value)));
            cube.size().forEach(value -> assertTrue(Double.isFinite(value) && value > 0));
            assertEquals(3, cube.origin().size());
            assertEquals(3, cube.size().size());
            assertEquals(Set.of("north", "east", "south", "west", "up", "down"),
                    cube.uv().keySet());
            assertFalse(cube.hasRotation());
            assertFalse(cube.hasInflate());
            for (FaceUv face : cube.uv().values()) {
                assertEquals(2, face.origin().size());
                assertEquals(2, face.size().size());
                for (int axis = 0; axis < 2; axis++) {
                    double start = face.origin().get(axis);
                    double end = start + face.size().get(axis);
                    assertTrue(Double.isFinite(start) && Double.isFinite(end));
                    assertTrue(Math.min(start, end) >= 0);
                    assertTrue(Math.max(start, end) <= 128);
                    assertTrue(face.size().get(axis) != 0);
                }
            }
            assertTrue(boxes.add(cube.origin() + ":" + cube.size()), "No duplicate cube is allowed");
        }
        List<AABB> cubeBounds = geometry.cubes().stream()
                .map(CorrectiveMilestoneNineARenderAlignmentTest::cubeBounds).toList();
        for (int first = 0; first < cubeBounds.size(); first++) {
            for (int second = first + 1; second < cubeBounds.size(); second++) {
                assertFalse(cubeBounds.get(first).intersects(cubeBounds.get(second)),
                        "Cubes may meet at a face but must not overlap: " + first + "/" + second);
            }
        }
        assertEquals(2, geometry.boneCount());
        assertEquals(0, geometry.childBoneCount());
    }

    @Test
    void everyFacingEnvelopeIsCenteredFlushAndContainedByItsFourCells() throws IOException {
        AABB model = readGeometry().bounds();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB occupied = ShrineRenderTransform.occupiedShrineFootprintEnvelope(ANCHOR, facing);
            AABB rendered = ShrineRenderTransform.renderedShrineEnvelope(ANCHOR, facing, model);
            assertTrue(contains(occupied, rendered), facing.toString());
            assertEquals(centerX(occupied), centerX(rendered), EPSILON);
            assertEquals(centerZ(occupied), centerZ(rendered), EPSILON);
            assertEquals(2.0, rendered.getXsize(), EPSILON);
            assertEquals(2.0, rendered.getZsize(), EPSILON);
            assertEquals(0.9375, rendered.getYsize(), EPSILON);
            assertEquals(0.0, rendered.minX - occupied.minX, EPSILON);
            assertEquals(0.0, occupied.maxX - rendered.maxX, EPSILON);
            assertEquals(0.0, rendered.minZ - occupied.minZ, EPSILON);
            assertEquals(0.0, occupied.maxZ - rendered.maxZ, EPSILON);
            assertEquals(ANCHOR.getY(), rendered.minY, EPSILON);
            assertEquals(ANCHOR.getY() + 0.9375, rendered.maxY, EPSILON);
            assertEquals(0.0, ShrineRenderTransform.renderOffsetBlocks(
                    ShrineMonolithDefinitions.SHRINE), EPSILON);
            assertTrue(contains(ShrineRenderTransform.worldBounds(ANCHOR), rendered));
        }
    }

    @Test
    void correctedEnvelopeMeetsFootprintBoundaryWithoutEnteringPerimeterStairCells() throws IOException {
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
    void graniteMaterialPassTargetsOnlyTheDedicatedRimBone() throws IOException {
        String renderer = Files.readString(RENDERER);
        String layer = Files.readString(GRANITE_LAYER);
        assertEquals(1, occurrences(renderer, "addRenderLayer(new ShrineGraniteRenderLayer(this))"));
        assertTrue(renderer.contains("model.getBone(ShrineGraniteRenderLayer.GRANITE_BONE)"));
        assertTrue(layer.contains("SURFACE_BONE = \"shrine_surface\""));
        assertTrue(layer.contains("GRANITE_BONE = \"granite_rim\""));
        assertTrue(layer.contains("ShrineRimMaterialSelection.textureFor(anchor)"));
        assertTrue(layer.contains("surface.setHidden(true)"));
        assertTrue(layer.contains("granite.setHidden(false)"));
        assertTrue(layer.contains("getRenderer().reRender("));
        assertTrue(layer.contains("finally"));
    }

    @Test
    void ownerSuppliedAssetsAndUnchangedProtectedAssetsHaveExpectedHashes() throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("geo/shrine_missing.geo.json", "460F9FDDE68EC578C3FF1B4E26B457AFCF3467485325B4189C14E02820CC4781"),
                Map.entry("animations/shrine.animation.json", "F20A6AFD94D1FCF6463B8FDA98853781FC8548293293514199C4AC1E4C5A981E"),
                Map.entry("models/item/shrine.json", "829C55BB91B761F7529607B3BFD439B73D6A171F01556C12D5F50D5991636985"),
                Map.entry("textures/block/shrine/honesty.png", "BF7C657E85198EB58A82E6466DFCBAFD74E45B3201B9F24000BD553B84744855"),
                Map.entry("textures/block/shrine/compassion.png", "F4A03F885EBF68B0C060450F72EDCFE3D2D48825E030C3A31951F826FBA627CD"),
                Map.entry("textures/block/shrine/valor.png", "2482D99690D718C3D0B06E919118408BE965179C0A878F2924F396EBBA186CF9"),
                Map.entry("textures/block/shrine/justice.png", "50531DACCE0ECB3A513714EC3036C44C96092A787F93854375EDC47904A2BFC3"),
                Map.entry("textures/block/shrine/sacrifice.png", "E9D3FA485A24BC1237B20CE2287F3E7BD93E8792C4BF44C8DA742C25992427DC"),
                Map.entry("textures/block/shrine/honor.png", "723898577D80867D40B4BBF9DADCDCC759446740722F4F496264E88D1826AF36"),
                Map.entry("textures/block/shrine/spirituality.png", "C4394FF25C3EF14DAE11EEDB5D2DF3BD0787608E82602712314AF32565A3CA4A"),
                Map.entry("textures/block/shrine/humility.png", "B8136CE1C9637D1B1A5F6E0738B9698F34854C88F796BD6FC63DB2F955C1688C"),
                Map.entry("textures/block/shrine/chaos.png", "9365AB11463B1442FEE89D131AA0197A38E7F5FE513102E44182D2B1595F4702"),
                Map.entry("textures/block/shrine/granite.png", "A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77"),
                Map.entry("textures/block/shrine/light_granite.png", "A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77"),
                Map.entry("geo/monolith_diagnostic.geo.json", "0D58B1ED73A8811E10B276DB7E55B29F7248DD047074C0FE786882DF0757E53C"),
                Map.entry("geo/monolith_diagnostic_alternate.geo.json", "B2FCBE6841C53313A754A9722E5633957A9AB0334FB96FF05FBCFC42BA7512DC"),
                Map.entry("textures/block/monolith/diagnostic_stone.png", "FE07CE0672EE51D76F2833D1044264C7B65B2ADEB076873D1B07C953509944F4"),
                Map.entry("textures/block/monolith/diagnostic_alternate_stone.png", "E320B72F2D0B9429D07DD4E62D264535760797AE6D1F9DD047C082B6736A2C4A"),
                Map.entry("animations/monolith.animation.json", "D63D4CBCEF6A3E410EE94F38F5684F7B3E9F94BCC69B4D80C925FBBB61FC1530"),
                Map.entry("models/item/monolith.json", "E48339859F7AA66BBC08246B8BC65AC1827E28241509518F9884A854739A2BED"));
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), sha256(RESOURCES.resolve(entry.getKey())), entry.getKey());
        }
        assertEquals("DAEF7813658A6EB8C9831D538599EB58892E184174896D4B2179E21C29C099FE",
                sha256(GEOMETRY));
    }

    @Test
    void existingDecorTabExposesExactlyOneConfiguredShrineAndMonolithStack() throws IOException {
        ItemStack stack = CreativeTabRegistry.shrineCreativeStack(shrineItem);
        assertEquals(shrineItem, stack.getItem());
        assertEquals(ShrineItemStateAccess.defaultState(),
                shrineItem.stateAccess().read(stack).orElseThrow());
        var validation = shrineItem.stateAccess().validateForPlacement(
                stack, ShrineMonolithDefinitions.catalogue());
        assertTrue(validation.valid());
        assertFalse(validation.usedDefault(), "Creative stack carries the explicit approved default");

        ItemStack monolithStack = CreativeTabRegistry.monolithCreativeStack(monolithItem);
        ShrineItemState monolithState = monolithItem.stateAccess().read(monolithStack).orElseThrow();
        assertEquals(monolithItem, monolithStack.getItem());
        assertEquals(ShrineMonolithDefinitions.MONOLITH, monolithState.familyId());
        assertEquals(monolithItem.defaultVariantId(), monolithState.variantId());
        assertTrue(monolithItem.stateAccess().validateForPlacement(
                monolithStack, ShrineMonolithDefinitions.catalogue()).valid());

        String source = Files.readString(CREATIVE_TAB);
        String decor = source.substring(source.indexOf("CREATIVE_DECOR_TAB"),
                source.indexOf("CREATIVE_ITEMS_TAB"));
        assertEquals(1, occurrences(source, "LargeStructureRegistry.SHRINE.get()"));
        assertEquals(1, occurrences(decor, "shrineCreativeStack(LargeStructureRegistry.SHRINE.get())"));
        assertEquals(1, occurrences(source, "LargeStructureRegistry.MONOLITH.get()"));
        assertEquals(1, occurrences(decor, "monolithCreativeStack(LargeStructureRegistry.MONOLITH.get())"));
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
        assertEquals(2, bones.size());
        JsonObject rootBone = bones.get(0).getAsJsonObject();
        List<Double> pivot = numbers(rootBone.getAsJsonArray("pivot"));
        List<Cube> cubes = new ArrayList<>();
        AABB bounds = null;
        for (var boneElement : bones) {
            JsonObject bone = boneElement.getAsJsonObject();
            for (var element : bone.getAsJsonArray("cubes")) {
                JsonObject object = element.getAsJsonObject();
                List<Double> origin = numbers(object.getAsJsonArray("origin"));
                List<Double> size = numbers(object.getAsJsonArray("size"));
                Map<String, FaceUv> faceUvs = new HashMap<>();
                for (var entry : object.getAsJsonObject("uv").entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    faceUvs.put(entry.getKey(), new FaceUv(
                            numbers(face.getAsJsonArray("uv")),
                            numbers(face.getAsJsonArray("uv_size"))));
                }
                Cube cube = new Cube(origin, size, Map.copyOf(faceUvs),
                        object.has("rotation"), object.has("inflate"));
                cubes.add(cube);
                AABB cubeBounds = new AABB(
                        origin.get(0), origin.get(1), origin.get(2),
                        origin.get(0) + size.get(0), origin.get(1) + size.get(1),
                        origin.get(2) + size.get(2));
                bounds = bounds == null ? cubeBounds : union(bounds, cubeBounds);
            }
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

    private static AABB cubeBounds(Cube cube) {
        return new AABB(
                cube.origin().get(0), cube.origin().get(1), cube.origin().get(2),
                cube.origin().get(0) + cube.size().get(0),
                cube.origin().get(1) + cube.size().get(1),
                cube.origin().get(2) + cube.size().get(2));
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
            Map<String, FaceUv> uv,
            boolean hasRotation,
            boolean hasInflate) {
    }

    private record FaceUv(List<Double> origin, List<Double> size) {
    }
}
