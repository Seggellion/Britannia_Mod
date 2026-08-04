package com.seggellion.britannia_mod.structure.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MonolithItemStateTest {
    private static MonolithItem item;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        item = MilestoneTwoRegisteredTestContent.monolith();
    }

    @Test
    void rawStackDefaultsOnlyDuringServerValidation() {
        ItemStack rawStack = new ItemStack(item);
        var validation = item.stateAccess().validateForPlacement(
                rawStack, ShrineMonolithDefinitions.catalogue());

        assertTrue(validation.valid());
        assertTrue(validation.usedDefault());
        assertEquals(state("monolith", "diagnostic_missing_content"),
                validation.state().orElseThrow());
        assertTrue(item.stateAccess().read(rawStack).isEmpty());
    }

    @Test
    void configuredRecoveryAndCopiesPreserveIndependentState() {
        ShrineItemState expected = state("monolith", "diagnostic_missing_content");
        ItemStack recovered = item.stateAccess().configuredStack(expected);
        ItemStack copy = recovered.copy();
        copy.set(MilestoneTwoRegisteredTestContent.monolithComponent(),
                state("monolith", "removed_variant"));

        assertEquals(1, recovered.getCount());
        assertEquals(expected, item.stateAccess().read(recovered).orElseThrow());
        assertEquals("removed_variant", item.stateAccess().read(copy).orElseThrow().variantId().value());
        assertNotEquals(item.stateAccess().read(recovered), item.stateAccess().read(copy));
    }

    @Test
    void unknownVariantAndCrossFamilyStateFailWithoutSubstitution() {
        ItemStack missingVariant = item.stateAccess().configuredStack(
                state("monolith", "removed_variant"));
        ItemStack shrineState = item.stateAccess().configuredStack(state("shrine", "honesty"));

        assertEquals(ShrineItemStateAccess.Status.VARIANT_MISSING,
                item.stateAccess().validateForPlacement(
                        missingVariant, ShrineMonolithDefinitions.catalogue()).status());
        assertEquals(ShrineItemStateAccess.Status.UNSUPPORTED_FAMILY,
                item.stateAccess().validateForPlacement(
                        shrineState, ShrineMonolithDefinitions.catalogue()).status());
        assertEquals("removed_variant",
                item.stateAccess().read(missingVariant).orElseThrow().variantId().value());
    }

    private static ShrineItemState state(String family, String variant) {
        return new ShrineItemState(1, new FamilyId(family), new VariantId(variant));
    }
}
