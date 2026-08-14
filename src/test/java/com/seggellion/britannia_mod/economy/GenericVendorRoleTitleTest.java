package com.seggellion.britannia_mod.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vendor/Trader Milestone 17: the generic vendor entity presents every
 * Rails-defined profession, so its role title must humanize any stamped
 * economic type key sensibly and never crash on odd input. Tested through
 * the Minecraft-free VendorRoleTitles helper the entity delegates to.
 */
final class GenericVendorRoleTitleTest {
    @Test
    void humanizesRolloutKeys() {
        assertEquals("Weaponsmith", VendorRoleTitles.humanize("weaponsmith_vendor"));
        assertEquals("Stone Crafter", VendorRoleTitles.humanize("stone_crafter_vendor"));
        assertEquals("Holy Mage", VendorRoleTitles.humanize("holy_mage_vendor"));
        assertEquals("Tavernkeeper", VendorRoleTitles.humanize("tavernkeeper"));
    }

    @Test
    void degradesSafely() {
        assertEquals("Vendor", VendorRoleTitles.humanize(null));
        assertEquals("Vendor", VendorRoleTitles.humanize(""));
        assertEquals("Vendor", VendorRoleTitles.humanize("_vendor"));
    }
}
