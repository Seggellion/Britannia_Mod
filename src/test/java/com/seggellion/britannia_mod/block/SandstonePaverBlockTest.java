package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runtime and resource contracts for the light/dark sandstone pavers. */
class SandstonePaverBlockTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final Path SANDSTONE = ASSETS.resolve("textures/block/structure/sandstone");
    private static final List<String> IDS = List.of(
        "light_sandstone_paver", "dark_sandstone_paver");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @Test
    void stateSpacePersistsExactlyTwoHorizontalAxesAndTwoTextures() {
        SandstonePaverBlock block = new SandstonePaverBlock(BlockBehaviour.Properties.of());

        assertEquals(Set.of(Direction.Axis.X, Direction.Axis.Z),
            Set.copyOf(SandstonePaverBlock.AXIS.getPossibleValues()));
        assertEquals(Set.of(0, 1), Set.copyOf(SandstonePaverBlock.TEXTURE_VARIANT.getPossibleValues()));
        assertEquals(4, block.getStateDefinition().getPossibleStates().size());
        assertFalse(SandstonePaverBlock.AXIS.getPossibleValues().contains(Direction.Axis.Y));
    }

    @Test
    void placementRandomizesAxesAndTexturesApproximatelyEvenly() {
        SandstonePaverBlock block = new SandstonePaverBlock(BlockBehaviour.Properties.of());
        RandomSource random = RandomSource.create(0x5A4D570EL);
        Map<Direction.Axis, Integer> axes = new HashMap<>();
        Map<Integer, Integer> textures = new HashMap<>();

        for (int placement = 0; placement < 20_000; placement++) {
            BlockState state = block.randomPlacementState(random);
            axes.merge(state.getValue(SandstonePaverBlock.AXIS), 1, Integer::sum);
            textures.merge(state.getValue(SandstonePaverBlock.TEXTURE_VARIANT), 1, Integer::sum);
        }

        assertEquals(Set.of(Direction.Axis.X, Direction.Axis.Z), axes.keySet());
        assertEquals(Set.of(0, 1), textures.keySet());
        assertTrue(Math.abs(axes.get(Direction.Axis.X) - axes.get(Direction.Axis.Z)) < 1_000, axes::toString);
        assertTrue(Math.abs(textures.get(0) - textures.get(1)) < 1_000, textures::toString);
    }

    @Test
    void structureRotationSwapsOnlyQuarterTurnAxes() {
        SandstonePaverBlock block = new SandstonePaverBlock(BlockBehaviour.Properties.of());
        BlockState x = block.defaultBlockState().setValue(SandstonePaverBlock.AXIS, Direction.Axis.X);

        assertEquals(Direction.Axis.Z,
            block.rotate(x, Rotation.CLOCKWISE_90).getValue(SandstonePaverBlock.AXIS));
        assertEquals(Direction.Axis.Z,
            block.rotate(x, Rotation.COUNTERCLOCKWISE_90).getValue(SandstonePaverBlock.AXIS));
        assertEquals(Direction.Axis.X,
            block.rotate(x, Rotation.CLOCKWISE_180).getValue(SandstonePaverBlock.AXIS));
        assertEquals(Direction.Axis.X,
            block.rotate(x, Rotation.NONE).getValue(SandstonePaverBlock.AXIS));
    }

    @Test
    void suppliedTexturesMapToCompleteNonRandomRenderVariants() throws Exception {
        try (Stream<Path> textures = Files.list(SANDSTONE)) {
            assertEquals(Set.of(
                    "light_sandstone_paver_1.png", "light_sandstone_paver_2.png",
                    "dark_sandstone_paver_1.png", "dark_sandstone_paver_2.png"),
                textures.map(path -> path.getFileName().toString())
                    .filter(name -> name.contains("sandstone_paver"))
                    .collect(java.util.stream.Collectors.toSet()));
        }

        for (String id : IDS) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + id + ".json"))
                .getAsJsonObject("variants");
            assertEquals(Set.of(
                "axis=x,texture_variant=0", "axis=z,texture_variant=0",
                "axis=x,texture_variant=1", "axis=z,texture_variant=1"), variants.keySet());

            for (int texture = 0; texture < 2; texture++) {
                String suffix = String.valueOf(texture + 1);
                String expectedModel = "britannia_mod:block/structure/sandstone/" + id + "_" + suffix;
                JsonObject x = variants.getAsJsonObject("axis=x,texture_variant=" + texture);
                JsonObject z = variants.getAsJsonObject("axis=z,texture_variant=" + texture);
                assertEquals(expectedModel, x.get("model").getAsString());
                assertEquals(expectedModel, z.get("model").getAsString());
                assertEquals(0, x.get("y").getAsInt());
                assertEquals(90, z.get("y").getAsInt());

                JsonObject model = json(ASSETS.resolve(
                    "models/block/structure/sandstone/" + id + "_" + suffix + ".json"));
                assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
                assertEquals("britannia_mod:block/structure/sandstone/" + id + "_" + suffix,
                    model.getAsJsonObject("textures").get("all").getAsString());
            }

            JsonObject itemModel = json(ASSETS.resolve("models/item/" + id + ".json"));
            assertEquals("britannia_mod:block/structure/sandstone/" + id + "_1",
                itemModel.get("parent").getAsString());
        }
    }

    @Test
    void bothPaversAreRegisteredObtainableMineableAndSelfDropping() throws Exception {
        String blocks = Files.readString(PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/registry/BlockRegistry.java"));
        String items = Files.readString(PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String tabs = Files.readString(PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"));
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        String pickaxe = Files.readString(RESOURCES.resolve(
            "data/minecraft/tags/block/mineable/pickaxe.json"));

        for (String id : IDS) {
            String constant = id.toUpperCase();
            assertTrue(blocks.contains("SandstonePaverBlock> " + constant));
            assertTrue(blocks.contains("BLOCKS.register(\"" + id + "\""));
            assertTrue(items.contains("ITEMS.register(\"" + id + "\""));
            assertTrue(items.contains("BlockRegistry." + constant + ".get()"));
            assertTrue(tabs.contains("ItemRegistry." + constant + "_ITEM.get()"));
            assertTrue(language.has("block.britannia_mod." + id));
            assertTrue(pickaxe.contains("\"britannia_mod:" + id + "\""));

            JsonObject loot = json(RESOURCES.resolve(
                "data/britannia_mod/loot_table/blocks/" + id + ".json"));
            JsonObject entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("britannia_mod:" + id, entry.get("name").getAsString());
        }
    }

    private static JsonObject json(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
