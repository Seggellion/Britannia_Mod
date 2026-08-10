package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.IbisEntity;
import com.seggellion.britannia_mod.entity.IbisVariant;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-server checks for the synchronized and persistent Milestone 6 ibis variant. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NewAssetsIbisGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NewAssetsIbisGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void whiteAndScarletUseOneEntityType(GameTestHelper helper) {
        IbisEntity ibis = helper.spawn(EntityRegistry.IBIS_ENTITY.get(), new BlockPos(1, 1, 1));

        ibis.setVariant(IbisVariant.WHITE);
        check(ibis.getVariant() == IbisVariant.WHITE, "ibis did not expose the white variant");
        ibis.setVariant(IbisVariant.SCARLET);
        check(ibis.getVariant() == IbisVariant.SCARLET, "ibis did not expose the scarlet variant");
        check(ibis.getType() == EntityRegistry.IBIS_ENTITY.get(),
                "changing the variant unexpectedly changed the entity type");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scarletVariantSurvivesSaveAndReload(GameTestHelper helper) {
        IbisEntity original = helper.spawn(EntityRegistry.IBIS_ENTITY.get(), new BlockPos(1, 1, 1));
        original.setVariant(IbisVariant.SCARLET);
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);

        IbisEntity reloaded = EntityRegistry.IBIS_ENTITY.get().create(helper.getLevel());
        check(reloaded != null, "registered ibis entity type could not create a reload target");
        reloaded.readAdditionalSaveData(saved.copy());
        check(reloaded.getVariant() == IbisVariant.SCARLET,
                "scarlet ibis variant did not survive the NBT round trip");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
