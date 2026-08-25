package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.GenericVendorEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Vendor/Trader Milestone 19 validation: an economic projection's authoritative
 * stamps must survive the world round-trip (chunk unload/reload, restart).
 *
 * <p>This is load-bearing, not cosmetic: {@code ServerCatalogService} routes a
 * projection to the Rails-authoritative catalog ONLY when it carries
 * {@code economicNpcTypeKey} + {@code economicCityPublicId}, and
 * {@code MerchantEconomyService} routes purchases to the Milestone 14 retail
 * transaction ONLY when {@code worldNpcPublicId} is present too. If those
 * stamps were lost on save/load, every economic Vendor in a restarted world
 * would silently fall back to the legacy catalog and legacy settlement --
 * exactly the kind of quiet regression this milestone exists to rule out.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class EconomicProjectionDurabilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private EconomicProjectionDurabilityGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void economicStampsSurviveSaveAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID worldNpcId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();

        GenericVendorEntity original = EntityRegistry.VENDOR.get().create(level);
        check(original != null, "could not create a vendor projection");
        original.setWorldNpcPublicId(worldNpcId);
        original.setEconomicNpcTypeKey("weaponsmith_vendor");
        original.setEconomicCityPublicId(cityId);
        original.setCityName("Britain");

        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);

        GenericVendorEntity reloaded = EntityRegistry.VENDOR.get().create(level);
        check(reloaded != null, "could not create the reload target");
        reloaded.load(saved);

        check(worldNpcId.equals(reloaded.getWorldNpcPublicId()),
                "worldNpcPublicId lost across save/load -- purchases would fall back to legacy settlement");
        check("weaponsmith_vendor".equals(reloaded.getEconomicNpcTypeKey()),
                "economicNpcTypeKey lost across save/load -- the catalog would fall back to the legacy path");
        check(cityId.equals(reloaded.getEconomicCityPublicId()),
                "economicCityPublicId lost across save/load -- the authoritative city would be unknown");
        check("Britain".equals(reloaded.getCityName()), "city name lost across save/load");

        // Presentation is DERIVED from the surviving stamp, so a reloaded
        // projection still knows its profession rather than degrading to a
        // bare "Vendor". (The visible custom name itself is written by the
        // reconciler when it materializes the entity and persists in vanilla
        // NBT; what matters here is that the derivation input survived.)
        check("Weaponsmith".equals(
                        com.seggellion.britannia_mod.economy.VendorRoleTitles
                                .humanize(reloaded.getEconomicNpcTypeKey())),
                "the reloaded projection could not derive its profession presentation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnstampedProjectionStaysUnstamped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GenericVendorEntity original = EntityRegistry.VENDOR.get().create(level);
        check(original != null, "could not create a vendor projection");

        CompoundTag saved = new CompoundTag();
        original.saveWithoutId(saved);

        GenericVendorEntity reloaded = EntityRegistry.VENDOR.get().create(level);
        check(reloaded != null, "could not create the reload target");
        reloaded.load(saved);

        // Fail closed: an unstamped projection must never invent an identity,
        // because a fabricated stamp would route real money through the wrong
        // authoritative city.
        check(reloaded.getWorldNpcPublicId() == null, "an unstamped projection invented a world NPC id");
        check(reloaded.getEconomicNpcTypeKey() == null, "an unstamped projection invented a type key");
        check(reloaded.getEconomicCityPublicId() == null, "an unstamped projection invented a city");
        helper.succeed();
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
