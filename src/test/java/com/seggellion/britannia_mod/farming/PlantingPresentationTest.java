package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.item.WateringCanItem;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantingPresentationTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void everyCanonicalSpeciesHasATranslatableNameAndExactConfirmation() throws Exception {
        var lang = JsonParser.parseString(Files.readString(Path.of(System.getProperty("britannia.projectDir", ".")).resolve("src/main/resources/assets/britannia_mod/lang/en_us.json")).replace("\uFEFF", "")).getAsJsonObject();
        assertEquals("You skillfully planted the %s seed.", lang.get("message.britannia_mod.seed_planted").getAsString());
        for (var crop : CropRegistry.all()) assertEquals(crop.displayName(), lang.get("crop.britannia_mod." + crop.id()).getAsString());
        for (var flower : FlowerRegistry.initial().definitions().values())
            assertEquals(lang.get("item.britannia_mod." + flower.id().getPath()).getAsString(), lang.get("crop.britannia_mod." + flower.id().getPath()).getAsString());
        for (var state : FarmingPlotStatus.Stage.values()) assertTrue(lang.has("hud.britannia_mod.plot." + state.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Test void fullPredicateClampsChargesPreservesComponentsAndDefaultsLegacyToFull() {
        var stack = new ItemStack(Items.BUCKET);
        var tag = new CompoundTag(); tag.putString("CustomOwner", "unchanged");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertEquals(12, WateringCanItem.getWaterCharges(stack));
        assertEquals(1f, WateringCanItem.fullModelState(stack));
        for (int charges : new int[]{Integer.MIN_VALUE, -1, 0, 1, 11, 12, 13, Integer.MAX_VALUE}) {
            WateringCanItem.setWaterCharges(stack, charges);
            int clamped = Math.max(0, Math.min(12, charges));
            assertEquals(clamped, WateringCanItem.getWaterCharges(stack));
            assertEquals(clamped == 12 ? 1f : 0f, WateringCanItem.fullModelState(stack));
            assertEquals("unchanged", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("CustomOwner"));
            assertEquals(WateringCanItem.fullModelState(stack), WateringCanItem.fullModelState(stack.copy()));
        }
        for (int malformed : new int[]{-99, 99}) {
            tag.putInt("WaterCharges", malformed); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            assertEquals(malformed < 0 ? 0 : 12, WateringCanItem.getWaterCharges(stack));
        }
    }
}
