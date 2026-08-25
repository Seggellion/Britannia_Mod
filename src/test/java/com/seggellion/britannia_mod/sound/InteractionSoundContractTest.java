package com.seggellion.britannia_mod.sound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards interaction sounds against the silent failures that Java compilation cannot detect. */
class InteractionSoundContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", ".")).toAbsolutePath().normalize();
    private static final Path JAVA = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/britannia_mod");

    private static final Map<String, ExpectedSound> EXPECTED = Map.of(
            "dye_banner", new ExpectedSound("DYE_BANNER", "dye"),
            "watering_can_fill", new ExpectedSound("WATERING_CAN_FILL", "liquid"),
            "watering_can_dispense", new ExpectedSound("WATERING_CAN_DISPENSE", "fshplsh"),
            "fishing_cast", new ExpectedSound("FISHING_CAST", "fshsplsh"),
            "scissors_cut", new ExpectedSound("SCISSORS_CUT", "scissors")
    );

    @Test
    void everyInteractionEventIsRegisteredAndResolvesToAValidVorbisAsset() throws Exception {
        String registrations = read(JAVA.resolve("ModSounds.java"));
        JsonObject sounds = JsonParser.parseString(read(ASSETS.resolve("sounds.json"))).getAsJsonObject();

        for (Map.Entry<String, ExpectedSound> entry : EXPECTED.entrySet()) {
            String eventId = entry.getKey();
            ExpectedSound expected = entry.getValue();
            assertTrue(registrations.contains(expected.constant() + " = SOUND_EVENTS.register("),
                    expected.constant() + " is not registered through ModSounds.SOUND_EVENTS");
            assertTrue(registrations.contains("\"" + eventId + "\""),
                    eventId + " is missing from its SoundEvent registration");

            assertTrue(sounds.has(eventId), eventId + " is missing from sounds.json");
            String reference = sounds.getAsJsonObject(eventId)
                    .getAsJsonArray("sounds").get(0).getAsString();
            assertEquals("britannia_mod:" + expected.file(), reference, eventId);

            Path ogg = ASSETS.resolve("sounds/" + expected.file() + ".ogg");
            assertTrue(Files.isRegularFile(ogg), ogg + " is missing");
            assertValidPositionalVorbis(ogg);
        }
    }

    @Test
    void dyeCueFollowsOnlyAConfirmedSuccessfulBannerMutation() throws Exception {
        String source = read(JAVA.resolve("dye/preview/DyePreviewRuntime.java"));
        int successGuard = source.indexOf("if (result.successfulApplication())");
        int successCall = source.indexOf("emitSuccess(player);", successGuard);
        int sound = source.indexOf("ModSounds.DYE_BANNER.get()", successCall);
        assertTrue(successGuard >= 0 && successCall > successGuard && sound > successCall);
        assertFalse(source.contains("SoundEvents.DYE_USE"));
    }

    @Test
    void wateringFillAndDispenseUseDistinctCuesOnlyAfterStateChanges() throws Exception {
        assertFalse(EXPECTED.get("watering_can_fill").file()
                .equals(EXPECTED.get("watering_can_dispense").file()));

        String can = read(JAVA.resolve("item/WateringCanItem.java"));
        int refillGuard = can.indexOf("if (charges < MAX_WATER_CHARGES)");
        int refillMutation = can.indexOf("setWaterCharges(stack, MAX_WATER_CHARGES)", refillGuard);
        int fillSound = can.indexOf("ModSounds.WATERING_CAN_FILL.get()", refillMutation);
        assertTrue(refillGuard >= 0 && refillMutation > refillGuard && fillSound > refillMutation);

        int farmingImproved = can.indexOf("boolean improved = farmBlockEntity.water(1)");
        int farmingSound = can.indexOf("ModSounds.WATERING_CAN_DISPENSE.get()", farmingImproved);
        int treeImproved = can.indexOf("boolean improved = orangeRoot.water(1)");
        int treeSound = can.indexOf("ModSounds.WATERING_CAN_DISPENSE.get()", treeImproved);
        assertTrue(farmingImproved >= 0 && farmingSound > farmingImproved);
        assertTrue(treeImproved >= 0 && treeSound > treeImproved);

        String sourceFill = read(JAVA.resolve("util/WaterSourceInteraction.java"));
        int changed = sourceFill.indexOf("getWaterCharges(stack) < WateringCanItem.MAX_WATER_CHARGES");
        int sourceMutation = sourceFill.indexOf("setWaterCharges(stack, WateringCanItem.MAX_WATER_CHARGES)", changed);
        int sourceSound = sourceFill.indexOf("ModSounds.WATERING_CAN_FILL.get()", sourceMutation);
        assertTrue(changed >= 0 && sourceMutation > changed && sourceSound > sourceMutation);

        assertSuccessMutationPrecedesCue(
                JAVA.resolve("farming/FlowerInteractionService.java"),
                "flower.replaceSoil(soil.withHydration(soil.hydration() + 1))",
                "ModSounds.WATERING_CAN_DISPENSE.get()");
        assertSuccessMutationPrecedesCue(
                JAVA.resolve("block/OrangeTreeRootBlock.java"),
                "boolean improved = root.water(1)",
                "ModSounds.WATERING_CAN_DISPENSE.get()");
    }

    @Test
    void scissorsCueCoversEverySuccessfulCustomScissorsHarvestPath() throws Exception {
        String crops = read(JAVA.resolve("block/FarmingBlock.java"));
        int cropMutation = crops.indexOf("consumeSuccessfulFertileHarvest()");
        int cropToolGuard = crops.indexOf("toolStack.is(ItemRegistry.SCISSORS.get())", cropMutation);
        int cropSound = crops.indexOf("ModSounds.SCISSORS_CUT.get()", cropToolGuard);
        assertTrue(cropMutation >= 0 && cropToolGuard > cropMutation && cropSound > cropToolGuard);

        assertSuccessMutationPrecedesCue(
                JAVA.resolve("farming/FlowerInteractionService.java"),
                "flower.harvestAndReset(quality)",
                "ModSounds.SCISSORS_CUT.get()");
        assertSuccessMutationPrecedesCue(
                JAVA.resolve("block/OrangeFruitBlock.java"),
                "dropFruitFromTree(level, pos, root, player, true)",
                "ModSounds.SCISSORS_CUT.get()");
    }

    @Test
    void fishingCastReplacesVanillaThrowOnlyAfterServerHookSpawnSucceeds() throws Exception {
        String mixins = read(PROJECT.resolve("src/main/resources/britannia_mod.mixins.json"));
        String source = read(JAVA.resolve("mixin/FishingRodCastSoundMixin.java"));
        assertTrue(mixins.contains("FishingRodCastSoundMixin"));
        assertTrue(source.contains("sound != SoundEvents.FISHING_BOBBER_THROW"));
        assertTrue(source.contains("boolean added = level.addFreshEntity(entity)"));
        assertTrue(source.contains("if (added && level instanceof ServerLevel"));
        assertTrue(source.contains("ModSounds.FISHING_CAST.get()"));
        assertEquals(1, occurrences(source, "ModSounds.FISHING_CAST.get()"));
        assertFalse(source.contains("net.minecraft.client"));
    }

    private static void assertSuccessMutationPrecedesCue(Path sourcePath, String mutation, String cue)
            throws Exception {
        String source = read(sourcePath);
        int mutationIndex = source.indexOf(mutation);
        int cueIndex = source.indexOf(cue, mutationIndex);
        assertTrue(mutationIndex >= 0 && cueIndex > mutationIndex,
                sourcePath.getFileName() + ": " + cue + " must follow " + mutation);
    }

    private static void assertValidPositionalVorbis(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        assertTrue(bytes.length > 64, path + " is too small to contain a Vorbis stream");
        assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII), path.toString());

        int segmentCount = Byte.toUnsignedInt(bytes[26]);
        int packetStart = 27 + segmentCount;
        assertTrue(packetStart + 16 < bytes.length, path + " has an invalid first Ogg page");
        assertEquals(1, Byte.toUnsignedInt(bytes[packetStart]), path + " has no Vorbis identification packet");
        assertEquals("vorbis", new String(bytes, packetStart + 1, 6, StandardCharsets.US_ASCII), path.toString());
        assertEquals(1, Byte.toUnsignedInt(bytes[packetStart + 11]), path + " must be mono for positional playback");
        int sampleRate = ByteBuffer.wrap(bytes, packetStart + 12, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertTrue(sampleRate > 0, path + " has an invalid sample rate");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private record ExpectedSound(String constant, String file) {
    }
}
