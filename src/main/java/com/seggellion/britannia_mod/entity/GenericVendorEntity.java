package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Vendor/Trader Milestone 17: the shared presentation for every Rails-defined
 * Vendor profession without a dedicated entity class. The reconciler stamps
 * economic projections with their type key, so ONE registered entity
 * ({@code britannia_mod:vendor}) serves the whole RunUO profession rollout —
 * the role title humanizes the stamped key (e.g. {@code weaponsmith_vendor}
 * → "Weaponsmith"), and Rails stays the sole authority on which professions
 * exist (a new profession is Rails data, never a Java deployment).
 *
 * <p>Interaction, catalog, and purchase behavior all come from
 * {@link AbstractEconomyMerchantEntity} + the stamped-projection routing in
 * {@code ServerCatalogService}/{@code MerchantEconomyService}, identical to
 * the dedicated Baker/Tavernkeeper/Costermonger entities. Dedicated visual
 * presentations can replace this per profession later by editing the type's
 * {@code minecraft_entity_type_key} in Shard Admin — no code change.
 */
public class GenericVendorEntity extends AbstractEconomyMerchantEntity {
    public GenericVendorEntity(EntityType<? extends GenericVendorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return com.seggellion.britannia_mod.economy.VendorRoleTitles.humanize(getEconomicNpcTypeKey());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
