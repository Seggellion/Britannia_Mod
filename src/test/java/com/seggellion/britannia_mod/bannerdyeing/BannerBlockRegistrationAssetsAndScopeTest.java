package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;

class BannerBlockRegistrationAssetsAndScopeTest {
    private static final Path MAIN = Path.of("src/main/java/com/seggellion/britannia_mod");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void oneAnchorOneGenericPartAndOneAcceptingAnchorEntityAreRegisteredWithNoBlockItem() throws Exception {
        String registry = Files.readString(MAIN.resolve("registry/BannerBlockRegistry.java"));
        assertEquals(1, count(registry, "BLOCKS\\.register\\(\"banner\""));
        assertEquals(1, count(registry, "BLOCKS\\.register\\(\"banner_part\""));
        assertEquals(1, count(registry, "BLOCK_ENTITIES\\.register\\(\"banner\""));
        assertTrue(registry.contains("BannerBlockEntity::new, BANNER.get()"));
        assertFalse(registry.contains("BlockItem"));
        assertEquals(2, count(registry, "pushReaction\\(PushReaction\\.BLOCK\\)"));

        String itemRegistry = Files.readString(MAIN.resolve("registry/BannerItemRegistry.java"));
        assertEquals(1, count(itemRegistry, "ITEMS\\.register\\(\\s*\"banner\""));
        assertFalse(itemRegistry.contains("BlockItem"));
        assertTrue(Files.readString(MAIN.resolve("BritanniaMod.java"))
                .contains("BannerBlockRegistry.register(modEventBus)"));
    }

    @Test
    void anchorUsesOutwardFacingSupportLookupThinRotatedShapeAndCentralLifecycleDropPath() throws Exception {
        BannerBlock block = new BannerBlock(BlockBehaviour.Properties.of());
        var north = block.defaultBlockState().setValue(BannerBlock.FACING, Direction.NORTH)
                .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
        var east = block.defaultBlockState().setValue(BannerBlock.FACING, Direction.EAST)
                .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
        assertEquals(1.0, north.maxZ);
        assertTrue(north.minZ >= 0.875);
        assertEquals(0.0, east.minX);
        assertTrue(east.maxX <= 0.125);
        assertNotEquals(north, east);

        String source = Files.readString(MAIN.resolve("banner/block/BannerBlock.java"));
        assertTrue(source.contains("pos.relative(facing.getOpposite())"));
        assertTrue(source.contains("BannerStructureIntegrity.checkAnchor"));
        assertEquals(1, count(source, "getDrops\\("));
        assertEquals(1, count(source, "getCloneItemStack\\("));
        assertTrue(source.contains("onDestroyedByPlayer"));
        assertTrue(source.contains("BannerStructureLifecycle.removeFrom"));
        assertFalse(Files.exists(RESOURCES.resolve("data/britannia_mod/loot_tables/blocks/banner.json")));
    }

    @Test
    void staticDiagnosticAnchorAndPartAssetsPackageFacingWithoutTintOrLayeredRendering() throws Exception {
        Path blockstatePath = RESOURCES.resolve("assets/britannia_mod/blockstates/banner.json");
        Path modelPath = RESOURCES.resolve("assets/britannia_mod/models/block/banner.json");
        Path texturePath = RESOURCES.resolve("assets/britannia_mod/textures/banner/placeholder/missing.png");
        assertTrue(Files.isRegularFile(blockstatePath));
        assertTrue(Files.isRegularFile(modelPath));
        assertTrue(Files.isRegularFile(texturePath));
        assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/britannia_mod/blockstates/banner_part.json")));
        assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/britannia_mod/models/block/banner_part.json")));
        assertFalse(Files.exists(RESOURCES.resolve("assets/britannia_mod/models/item/banner_part.json")));

        JsonObject variants = JsonParser.parseString(Files.readString(blockstatePath))
                .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=east", "facing=south", "facing=west"),
                variants.keySet());
        String model = Files.readString(modelPath);
        assertTrue(model.contains("banner/placeholder/missing"));
        assertFalse(model.contains("tintindex"));
        assertFalse(model.contains("fabric_base"));
        assertFalse(model.contains("dye_mask"));
        assertFalse(model.contains("static_overlay"));
    }

    @Test
    void commonClassesHaveNoClientImportsPlacedRendererOrChildBlockEntity() throws Exception {
        for (Path folder : Set.of(MAIN.resolve("banner/block"), MAIN.resolve("banner/blockentity"),
                MAIN.resolve("banner/placement"))) {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("net.minecraft.client"), path.toString());
                    assertFalse(source.contains("com.mojang.blaze3d"), path.toString());
                    assertFalse(source.contains("registerBlockEntityRenderer"), path.toString());
                }
            }
        }
        try (var paths = Files.walk(MAIN.resolve("banner"))) {
            Set<String> names = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("BannerPartBlock.java"));
            assertFalse(names.stream().anyMatch(name -> name.contains("PartBlockEntity")
                    || name.contains("ChildBlockEntity") || name.contains("BlockEntityRenderer")));
        }
    }

    @Test
    void placementEntryPointFeedbackAndNoOutOfScopeContentArePresent() throws Exception {
        String item = Files.readString(MAIN.resolve("banner/item/BannerItem.java"));
        assertTrue(item.contains("public InteractionResult useOn(UseOnContext context)"));
        assertTrue(item.contains("BannerPlacementService.place(context)"));
        String service = Files.readString(MAIN.resolve("banner/placement/BannerPlacementService.java"));
        assertTrue(service.contains("instanceof ServerLevel"));
        assertTrue(service.contains("mayInteract"));
        assertTrue(service.contains("mayUseItemAt"));
        assertTrue(service.contains("UPDATE_SUPPRESS_DROPS"));
        assertFalse(service.contains("wall_perpendicular"));
        assertFalse(service.contains("recipe"));
        assertFalse(service.contains("command"));
        assertFalse(service.contains("npc"));

        String lang = Files.readString(RESOURCES.resolve("assets/britannia_mod/lang/en_us.json"));
        for (String key : Set.of("unconfigured", "data_unavailable", "invalid_footprint", "chunk_unloaded",
                "unsupported_orientation", "horizontal_face_required", "target_occupied", "invalid_support",
                "protected", "failed_safely")) {
            assertTrue(lang.contains("message.britannia_mod.banner.placement." + key), key);
        }
    }

    private static long count(String input, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(input).results().count();
    }
}
