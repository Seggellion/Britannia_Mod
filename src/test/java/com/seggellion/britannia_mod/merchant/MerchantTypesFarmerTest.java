package com.seggellion.britannia_mod.merchant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Farming-economy restore: the Farmer is a first-class merchant type.
 *
 * <p>The farmer never existed in either repository -- the only prior trace of the word was the
 * produce trader's buyback profession -- so the MerchantSpawnScreen selector, which is exactly
 * {@link MerchantTypes#configKeys()}, could not offer it. These tests pin the registration and,
 * just as deliberately, that the pre-existing merchant vocabulary is untouched.
 */
class MerchantTypesFarmerTest {

    @Test
    void theSelectorOffersTheFarmerAfterTheExistingMerchants() {
        assertEquals(List.of("baker", "tavernkeeper", "costermonger", "farmer"),
                MerchantTypes.configKeys(),
                "MerchantSpawnScreen shows exactly these keys, in this order");
    }

    @Test
    void theFarmerResolvesToItsOwnDefinition() {
        MerchantDefinition farmer = MerchantTypes.byId("farmer");
        assertEquals("farmer", farmer.configKey());
        assertEquals("farmer", farmer.npcType(), "the Rails live-NPC sync speaks this npc type");
        assertEquals("Farmer", farmer.roleTitle());
    }

    @Test
    void theFarmerCarriesTheFoodFloorAndNobodyElseDoes() {
        MerchantDefinition farmer = MerchantTypes.byId("farmer");
        assertTrue(farmer.requiresFoodSupply());
        assertEquals(MerchantTypes.FARMER_MINIMUM_FOOD_SUPPLY, farmer.minimumFoodSupply());
        assertEquals(20.0D, farmer.minimumFoodSupply(),
                "the ratified rule is food supply >= 20, shared with the Rails supply_gte requirement");

        for (String key : List.of("baker", "tavernkeeper", "costermonger")) {
            assertFalse(MerchantTypes.byId(key).requiresFoodSupply(),
                    key + " spawned ungated before the farmer existed and must stay that way");
        }
    }

    @Test
    void unknownKeysStillFallBackToTheBaker() {
        assertEquals("baker", MerchantTypes.byId("mysterious_stranger").configKey(),
                "the existing unknown-key behavior is the baker fallback, unchanged");
        assertEquals("baker", MerchantTypes.byId(null).configKey());
        assertEquals("baker", MerchantTypes.byId("  ").configKey());
    }

    @Test
    void theExistingAliasesStillResolve() {
        assertEquals("tavernkeeper", MerchantTypes.byId("tavern_keeper").configKey());
        assertEquals("tavernkeeper", MerchantTypes.byId("tavern").configKey());
        assertEquals("costermonger", MerchantTypes.byId("produce_merchant").configKey());
    }

    @Test
    void normalizationAcceptsTheKeyInAnySpelling() {
        assertEquals("farmer", MerchantTypes.normalize("  FaRmEr "));
        assertEquals("farmer", MerchantTypes.byId(" FARMER ").configKey());
    }
}
