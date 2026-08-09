package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyeItemRegistryTest {
    private static final List<String> PATHS = List.of(
            "madder_red", "woad_blue", "verdigris", "weld_gold",
            "soot_black", "chalk_white", "ice_blue");

    @BeforeAll
    static void registerTestContent() {
        Milestone6RegisteredTestContent.ensureRegistered();
    }

    @Test
    void exactlySevenDeterministicMappingsExistWithoutDuplicates() {
        Map<ResourceLocation, PigmentId> mappings = DyeItemRegistry.itemToPigmentMappings();
        assertEquals(7, mappings.size());
        assertEquals(7, mappings.values().stream().distinct().count());
        for (String path : PATHS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
            assertEquals(new PigmentId(id), mappings.get(id));
        }
    }

    @Test
    void everyRegisteredPigmentItemMapsToItsExistingPigmentId() {
        Milestone6RegisteredTestContent.pigments().forEach((itemId, item) -> {
            assertEquals(new PigmentId(itemId), item.pigmentId());
            assertEquals(new PigmentId(itemId), DyeItemRegistry.pigmentId(item).orElseThrow());
        });
    }

    @Test
    void deferredItemIdsMatchPigmentIdsExactly() {
        assertEquals("britannia_mod:dye_tub", DyeItemRegistry.DYE_TUB.getId().toString());
        assertEquals("britannia_mod:madder_red", DyeItemRegistry.MADDER_RED.getId().toString());
        assertEquals("britannia_mod:woad_blue", DyeItemRegistry.WOAD_BLUE.getId().toString());
        assertEquals("britannia_mod:verdigris", DyeItemRegistry.VERDIGRIS.getId().toString());
        assertEquals("britannia_mod:weld_gold", DyeItemRegistry.WELD_GOLD.getId().toString());
        assertEquals("britannia_mod:soot_black", DyeItemRegistry.SOOT_BLACK.getId().toString());
        assertEquals("britannia_mod:chalk_white", DyeItemRegistry.CHALK_WHITE.getId().toString());
        assertEquals("britannia_mod:ice_blue", DyeItemRegistry.ICE_BLUE.getId().toString());
    }

    @Test
    void unknownItemHasNoMapping() {
        assertTrue(DyeItemRegistry.pigmentId(Items.STICK).isEmpty());
    }

    @Test
    void customClientEditableDataCannotChangeAuthoritativeIdentity() {
        PigmentItem red = Milestone6RegisteredTestContent.pigments().get(
                ResourceLocation.parse("britannia_mod:madder_red"));
        ItemStack stack = new ItemStack(red);
        CompoundTag spoof = new CompoundTag();
        spoof.putString("pigment_id", "britannia_mod:ice_blue");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(spoof));
        assertEquals(PigmentId.parse("britannia_mod:madder_red"), DyeItemRegistry.pigmentId(stack.getItem()).orElseThrow());
        assertFalse(DyeItemRegistry.pigmentId(stack.getItem()).orElseThrow()
                .equals(PigmentId.parse("britannia_mod:ice_blue")));
    }
}
