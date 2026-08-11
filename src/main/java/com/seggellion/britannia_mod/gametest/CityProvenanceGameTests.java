package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.BakerEntity;
import com.seggellion.britannia_mod.item.CityProvenanceItemData;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Vendor/Trader Milestone 9 acceptance: the same item sold in Britain and
 * Minoc preserves distinct origin data across serialization and can never
 * silently merge into one stack.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class CityProvenanceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID BRITAIN = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID MINOC = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private CityProvenanceGameTests() {
    }

    @GameTest(batch = "economic_spawn_posts", template = TEMPLATE)
    public static void distinctOriginsNeverMergeAndSurviveSerialization(GameTestHelper helper) {
        ItemStack britainBread = new ItemStack(Items.BREAD, 3);
        ItemStack minocBread = new ItemStack(Items.BREAD, 3);
        CityProvenanceItemData.apply(britainBread, BRITAIN, "Britain");
        CityProvenanceItemData.apply(minocBread, MINOC, "Minoc");

        check(!ItemStack.isSameItemSameComponents(britainBread, minocBread),
                "different origin cities must never be stack-mergeable");
        ItemStack britainCopy = britainBread.copy();
        check(ItemStack.isSameItemSameComponents(britainBread, britainCopy),
                "identical provenance must remain stackable");

        // Restart simulation: vanilla NBT round-trip preserves both fields.
        CompoundTag saved = (CompoundTag) britainBread.save(helper.getLevel().registryAccess());
        ItemStack reloaded = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
        check(BRITAIN.equals(CityProvenanceItemData.cityPublicId(reloaded)),
                "origin city public id must survive serialization");
        check("Britain".equals(CityProvenanceItemData.cityName(reloaded)),
                "origin city name must survive serialization");
        check(!ItemStack.isSameItemSameComponents(reloaded, minocBread),
                "reloaded stack must still refuse to merge with a different origin");
        helper.succeed();
    }

    @GameTest(batch = "economic_spawn_posts", template = TEMPLATE)
    public static void vendorStampUsesTheAuthoritativeAssignmentCity(GameTestHelper helper) {
        BakerEntity baker = EntityRegistry.BAKER.get().create(helper.getLevel());
        check(baker != null, "baker entity could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        baker.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        baker.setEconomicCityPublicId(BRITAIN);
        baker.setCityName("Britain");
        helper.getLevel().addFreshEntity(baker);

        ItemStack bread = new ItemStack(Items.BREAD);
        CityProvenanceItemData.applyFromVendor(bread, baker);
        check(BRITAIN.equals(CityProvenanceItemData.cityPublicId(bread)),
                "vendor stamp must carry the assignment city public id");
        check("Britain".equals(CityProvenanceItemData.cityName(bread)),
                "vendor stamp must carry the display city name");

        // A legacy-block merchant (no economic stamp) must add nothing.
        BakerEntity legacy = EntityRegistry.BAKER.get().create(helper.getLevel());
        check(legacy != null, "legacy baker could not be created");
        ItemStack plain = new ItemStack(Items.BREAD);
        CityProvenanceItemData.applyFromVendor(plain, legacy);
        check(!CityProvenanceItemData.hasProvenance(plain),
                "legacy merchants without an authoritative city must not stamp provenance");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
