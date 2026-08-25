package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.merchant.MerchantTypes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key the MerchantSpawnScreen can configure must have a Milestone 16 migration target, or
 * a block carrying it would log "has unmapped type" forever and never converge onto the
 * authoritative post architecture. Rails pins the same vocabulary from its side in
 * {@code test/services/legacy_spawn_block_support_test.rb} (LEGACY_MERCHANT_KEYS).
 */
class LegacySpawnBlockMigratorMerchantKeysTest {

    @Test
    void everyConfigurableMerchantKeyHasAMigrationTarget() {
        for (String key : MerchantTypes.configKeys()) {
            assertTrue(LegacySpawnBlockMigrator.MERCHANT_KEYS.contains(key),
                    "merchant config key '" + key + "' has no authoritative-post migration mapping");
        }
    }

    @Test
    void theMigrationVocabularyIsExactlyTheLegacyMerchantFamily() {
        assertEquals(Set.of("baker", "tavernkeeper", "costermonger", "farmer"),
                LegacySpawnBlockMigrator.MERCHANT_KEYS,
                "must stay level with Rails' LEGACY_MERCHANT_KEYS");
    }
}
