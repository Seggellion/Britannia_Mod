package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;
import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionDyeContent;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

class ProductionBannerCatalogueTest {
    @Test
    void emptyAndSmallGenericFixturesRemainOutsideCatalogueBoundary() {
        RegistrySnapshot empty = RegistrySnapshot.empty();
        assertFalse(ProductionBannerCatalogue.isProductionCatalogue(empty));
        assertDoesNotThrow(() -> {
            if (ProductionBannerCatalogue.isProductionCatalogue(empty)) {
                ProductionBannerCatalogue.requireComplete(empty);
            }
        });
    }

    @Test
    void incompleteReleaseCatalogueFailsTheCatalogueSpecificBoundary() {
        RegistrySnapshot empty = RegistrySnapshot.empty();
        assertThrows(IllegalStateException.class, () -> ProductionBannerCatalogue.requireComplete(empty));
    }

    @Test
    void canonicalIdSetContainsExactlyThirtyThreeIds() {
        assertTrue(ProductionBannerCatalogue.canonicalIds().size() == 33);
    }

    @Test
    void productionDyeValidatorDetectsMissingRequiredMaterial() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ProductionDyeContent.requireComplete(RegistrySnapshot.empty()));
        assertTrue(exception.getMessage().contains("cotton"));
    }
}
