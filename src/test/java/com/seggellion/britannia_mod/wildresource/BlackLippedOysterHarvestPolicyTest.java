package com.seggellion.britannia_mod.wildresource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackLippedOysterHarvestPolicyTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void daggerRecognitionUsesOnlyTheEstablishedExactHolder() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/wildresource/DaggerTools.java"
        ));
        assertTrue(source.contains("stack.is(WeaponRegistry.DAGGER.get())"));
        assertFalse(source.contains("instanceof QualitySwordItem"));
        assertFalse(source.contains("instanceof SwordItem"));
    }

    @Test
    void adventurePathAndServerHarvestBothUseDaggerRecognition() throws IOException {
        String block = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/BlackLippedOysterBlock.java"
        ));
        String harvest = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceHarvestService.java"
        ));
        String interaction = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/wildresource/WildResourceInteractionHandler.java"
        ));

        assertTrue(block.contains("DaggerTools.isDagger(tool)"));
        assertTrue(harvest.contains("DaggerTools.isDagger(tool) && harvestOne("));
        assertTrue(harvest.contains("BlockRegistry.BLACK_LIPPED_OYSTER.get()"));
        assertTrue(harvest.contains("ItemRegistry.BLACK_PEARL.get()"));
        assertTrue(interaction.contains("GameType.ADVENTURE"));
    }

    @Test
    void ordinarySurvivalLootIsExactlyOneBlackPearl() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/loot_table/blocks/black_lipped_oyster.json"
        ))).getAsJsonObject();
        JsonObject pool = root.getAsJsonArray("pools").get(0).getAsJsonObject();
        JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();

        assertEquals(1, pool.get("rolls").getAsInt());
        assertEquals("britannia_mod:black_pearl", entry.get("name").getAsString());
    }

    @Test
    void creativeInventoryExposesTheNewPearlItem() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java"
        ));
        assertTrue(source.contains("safeAccept(output, ItemRegistry.BLACK_PEARL.get())"));
    }

    @Test
    void persistedNodeCanBeClaimedOnlyOnce() {
        WildResourceSavedData data = new WildResourceSavedData();
        BlockPos position = new BlockPos(3, 64, 5);
        data.registerNode(new WildResourceNode(WildResourceEntries.BLACK_LIPPED_OYSTER, position, 1L));

        assertTrue(data.removeNode(position).isPresent());
        assertTrue(data.removeNode(position).isEmpty());
    }
}
