package com.seggellion.britannia_mod.moongate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MoongateMilestoneTenContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    @Test
    void oneLogicalBlockRendersTheReauthoredThirtyTwoVoxelModel() throws Exception {
        JsonObject permanent = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/moongate_block.json"))).getAsJsonObject();
        JsonObject billboard = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/moongate_billboard.json"))).getAsJsonObject();
        JsonObject summonBase = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/moongate_summon_base.json"))).getAsJsonObject();
        assertEquals("minecraft:translucent", permanent.get("render_type").getAsString());
        assertEquals("minecraft:translucent", billboard.get("render_type").getAsString());
        assertEquals(5, permanent.getAsJsonArray("elements").size());
        assertEquals(permanent.getAsJsonArray("elements"), billboard.getAsJsonArray("elements"));
        assertEquals(2, summonBase.getAsJsonArray("elements").size());
        assertEquals(7, summonBase.getAsJsonArray("elements").size()
                + billboard.getAsJsonArray("elements").size());
        long northFaces = billboard.getAsJsonArray("elements").asList().stream()
                .map(value -> value.getAsJsonObject().getAsJsonObject("faces"))
                .filter(faces -> faces.has("north"))
                .count();
        long southFaces = billboard.getAsJsonArray("elements").asList().stream()
                .map(value -> value.getAsJsonObject().getAsJsonObject("faces"))
                .filter(faces -> faces.has("south"))
                .count();
        assertEquals(4L, northFaces);
        assertEquals(4L, southFaces);

        double maximumY = Double.NEGATIVE_INFINITY;
        for (JsonObject model : new JsonObject[] {summonBase, billboard}) {
            for (var value : model.getAsJsonArray("elements")) {
                JsonObject element = value.getAsJsonObject();
                for (String bound : new String[] {"from", "to"}) {
                    var coordinates = element.getAsJsonArray(bound);
                    double[] maximum = {16.0D, 32.0D, 16.0D};
                    for (int axis = 0; axis < coordinates.size(); axis++) {
                        double coordinate = coordinates.get(axis).getAsDouble();
                        assertTrue(coordinate >= 0.0D && coordinate <= maximum[axis],
                                "moongate model escaped its 16x32x16 re-authored envelope");
                        if (axis == 1) maximumY = Math.max(maximumY, coordinate);
                    }
                }
            }
        }
        assertEquals(32.0D, maximumY);
    }

    @Test
    void temporarySourceTexturesKeepTheirAtlasDimensionsAndAnimationMetadata() throws Exception {
        int[][] dimensions = {
            {128, 1536}, {34, 34}, {128, 1536}, {64, 960}, {32, 32}, {32, 32}
        };
        Path textureRoot = ASSETS.resolve("textures/block/new_assets/moongate");
        for (int index = 1; index <= 6; index++) {
            String suffix = index == 1 ? "" : Integer.toString(index);
            Path texture = textureRoot.resolve("portal_texture" + suffix + ".png");
            BufferedImage image = ImageIO.read(texture.toFile());
            assertEquals(dimensions[index - 1][0], image.getWidth());
            assertEquals(dimensions[index - 1][1], image.getHeight());
        }
        for (String name : new String[] {
                "portal_texture.png.mcmeta", "portal_texture3.png.mcmeta", "portal_texture4.png.mcmeta"
        }) {
            JsonObject metadata = JsonParser.parseString(Files.readString(textureRoot.resolve(name)))
                    .getAsJsonObject();
            assertEquals(1, metadata.getAsJsonObject("animation").get("frametime").getAsInt());
        }
    }

    @Test
    void finalIdAndTeleportContractRemainWhileLegacyTopIsCompatibilityOnly() throws Exception {
        String blocks = readJava("registry/BlockRegistry.java");
        String items = readJava("registry/ItemRegistry.java");
        String creative = readJava("registry/CreativeTabRegistry.java");
        String gate = readJava("block/MoongateBlock.java");
        String top = readJava("block/MoongateTopBlock.java");
        String renderer = readJava("client/renderer/MoongateBlockEntityRenderer.java");
        JsonObject state = JsonParser.parseString(Files.readString(
                ASSETS.resolve("blockstates/moongate_block.json"))).getAsJsonObject();
        String teleport = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/MoongateTeleportationHandler.java"));

        assertEquals(1, occurrences(blocks, "\"moongate_block\", MoongateBlock::new"));
        assertEquals(1, occurrences(blocks, "\"moongate_top\", MoongateTopBlock::new"));
        assertTrue(items.contains("MOONGATE_TOP_ITEM = ITEMS.register("),
                "legacy item ID must remain loadable in old inventories");
        assertTrue(creative.contains("ItemRegistry.MOONGATE_BLOCK_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.MOONGATE_TOP_ITEM.get()"));
        assertTrue(gate.contains("MoongateTeleportationHandler.teleportPlayer(finalPlayer)"));
        assertTrue(gate.contains("implements EntityBlock"));
        assertTrue(gate.contains("return RenderShape.INVISIBLE"));
        assertTrue(renderer.contains("getMainCamera().getYRot()"));
        assertTrue(renderer.contains("BILLBOARD_MODEL"));
        assertEquals("britannia_mod:block/moongate_block", state.getAsJsonObject("variants")
                .getAsJsonObject("").get("model").getAsString());
        assertFalse(gate.contains("MOONGATE_TOP"), "city gate must not place a second logical cell");
        assertTrue(top.contains("RenderShape.INVISIBLE"));
        assertTrue(top.contains(".randomTicks()"));
        assertTrue(top.contains("removeLegacyCell"));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/moongate_billboard.json")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/moongate_summon_base.json")));
        assertFalse(Files.exists(ASSETS.resolve("models/block/moongate_base.json")),
                "the permanent block's old generic base name must not remain available by accident");

        for (String city : new String[] {
                "Britain", "Moonglow", "Yew", "Minoc", "Trinsic", "Skara Brae", "Jhelom", "Magincia"
        }) {
            assertTrue(teleport.contains("\"" + city + "\""));
        }
        assertTrue(teleport.contains("player.getVehicle()"));
        assertTrue(teleport.contains("quest_escort_"));
        assertTrue(teleport.contains("addPlayerCooldown(playerUUID, 100)"));
    }

    @Test
    void permanentMoongateHumIsOneLocalizedLoopPerLoadedGate() throws Exception {
        JsonObject soundDefinitions = JsonParser.parseString(
                Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        JsonObject hum = soundDefinitions.getAsJsonObject("permanent_moongate_hum");
        JsonObject sample = hum.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("britannia_mod:moongate_hum", sample.get("name").getAsString());
        assertEquals(10, sample.get("attenuation_distance").getAsInt());
        assertFalse(soundDefinitions.getAsJsonObject("moongate_hum")
                .getAsJsonArray("sounds").get(0).getAsJsonObject().has("attenuation_distance"),
                "the separate paired-dungeon Moongate must keep its existing sound definition");

        String sounds = readJava("ModSounds.java");
        String gate = readJava("block/MoongateBlock.java");
        String manager = readJava("client/sound/MoongateAmbientSoundManager.java");
        String instance = readJava("client/sound/MoongateAmbientSound.java");
        assertTrue(sounds.contains("SoundEvent.createFixedRangeEvent("));
        assertTrue(sounds.contains("\"permanent_moongate_hum\"), 10.0F"));
        assertTrue(instance.contains("ModSounds.PERMANENT_MOONGATE_HUM"));
        assertTrue(gate.contains("MoongateAmbientSoundManager.tick"));
        assertFalse(gate.contains("playLocalSound"),
                "random display ticks must not restart overlapping thirteen-second hum samples");
        assertTrue(manager.contains("Map<SoundKey, MoongateAmbientSound> ACTIVE_SOUNDS"));
        assertTrue(manager.contains("CLEANUP_RANGE_BLOCKS = 12.0D"));
        assertTrue(manager.contains("level.hasChunk"));
        assertTrue(manager.contains("BlockRegistry.MOONGATE_BLOCK"));
        assertTrue(manager.contains("minecraft.level != level"));
        assertTrue(instance.contains("this.looping = true"));
        assertTrue(instance.contains("SoundInstance.Attenuation.LINEAR"));
        assertTrue(instance.contains("this.relative = false"));
    }

    @Test
    void pairedDungeonMoongateRemainsASeparateSystem() throws Exception {
        String dungeon = readJava("block/DungeonMoongateBlock.java");
        assertTrue(dungeon.contains("implements EntityBlock"));
        assertTrue(dungeon.contains("DUNGEON_MOONGATE_TOP"));
        assertTrue(dungeon.contains("DungeonMoongateBlockEntity"));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/dungeon_moongate_block.json")));
        assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/dungeon_moongate_top.json")));
    }

    private static String readJava(String relative) throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
