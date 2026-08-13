package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrabbyAxesTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Stream<Item> vanillaAxes() {
        return Stream.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
                Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
    }

    static Stream<Item> nonAxes() {
        return Stream.of(Items.IRON_SWORD, Items.DIAMOND_PICKAXE, Items.IRON_SHOVEL,
                Items.DIAMOND_HOE, Items.STICK, Items.SHEARS, Items.FLINT_AND_STEEL);
    }

    @ParameterizedTest
    @MethodSource("vanillaAxes")
    void everyVanillaAxeIsRecognised(Item item) {
        assertTrue(GrabbyAxes.isAxe(new ItemStack(item)), item.toString());
    }

    @ParameterizedTest
    @MethodSource("nonAxes")
    void nonAxesAreRejected(Item item) {
        assertFalse(GrabbyAxes.isAxe(new ItemStack(item)), item.toString());
    }

    @Test
    void emptyAndNullStacksAreRejected() {
        assertFalse(GrabbyAxes.isAxe(ItemStack.EMPTY));
        assertFalse(GrabbyAxes.isAxe(null));
    }

    @Test
    void theModsOwnTwoHandedAxeIsCoveredByTheSubclassArm() {
        // This is the case a tag-only check would miss. britannia_mod:two_handed_axe carries no
        // ItemTags.AXES membership - the mod ships no data/minecraft/tags directory - so the only
        // thing that recognises it is that it extends AxeItem.
        assertTrue(AxeItem.class.isAssignableFrom(TwoHandedAxeItem.class),
                "TwoHandedAxeItem must remain an AxeItem subclass for GrabbyAxes to recognise it");
    }

    @Test
    void theModsOtherToolFamiliesAreNotAxes() {
        assertFalse(AxeItem.class.isAssignableFrom(QualitySwordItem.class));
        assertFalse(AxeItem.class.isAssignableFrom(BritanniaPickaxeItem.class));
    }
}
