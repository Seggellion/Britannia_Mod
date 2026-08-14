package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.BritanniaSpawnBlockEntity;
import com.seggellion.britannia_mod.entity.IbisEntity;
import com.seggellion.britannia_mod.entity.IbisVariant;
import com.seggellion.britannia_mod.entity.FlamingoEntity;
import com.seggellion.britannia_mod.entity.FlamingoVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.spawner.BritanniaSpawnableEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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

    @GameTest(template = TEMPLATE)
    public static void flamingoIsAvailableToTheBritanniaSpawner(GameTestHelper helper) {
        FlamingoEntity flamingo = helper.spawn(EntityRegistry.FLAMINGO_ENTITY.get(), new BlockPos(1, 1, 1));
        check(flamingo.getType() == EntityRegistry.FLAMINGO_ENTITY.get(),
                "registered Flamingo entity type did not create a Flamingo");
        check(BritanniaSpawnableEntities.isAllowed(
                        BuiltInRegistries.ENTITY_TYPE.getKey(flamingo.getType())),
                "Flamingo was not exposed through the Britannia spawner allowlist");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void flamingoColorSynchronizesAndPersists(GameTestHelper helper) {
        FlamingoEntity original = helper.spawn(
                EntityRegistry.FLAMINGO_ENTITY.get(), new BlockPos(1, 1, 1));
        for (FlamingoVariant variant : FlamingoVariant.values()) {
            original.setVariant(variant);
            check(original.getVariant() == variant,
                    "Flamingo did not expose synchronized " + variant.serializedName() + " color");
            check(original.getType() == EntityRegistry.FLAMINGO_ENTITY.get(),
                    "Flamingo color unexpectedly changed the entity type");
        }

        original.setVariant(FlamingoVariant.ROSE);
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        FlamingoEntity reloaded = EntityRegistry.FLAMINGO_ENTITY.get().create(helper.getLevel());
        check(reloaded != null, "registered Flamingo type could not create a reload target");
        reloaded.readAdditionalSaveData(saved.copy());
        check(reloaded.getVariant() == FlamingoVariant.ROSE,
                "Flamingo color did not survive the NBT round trip");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void britanniaSpawnBlockAddsSelectedFlamingoToTheWorld(GameTestHelper helper) {
        BlockPos spawnerPos = helper.absolutePos(new BlockPos(4, 3, 4));
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                helper.getLevel().setBlock(spawnerPos.offset(x, -1, z),
                        Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        helper.getLevel().setBlock(spawnerPos,
                BlockRegistry.BRITANNIA_SPAWN_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        check(helper.getLevel().getBlockEntity(spawnerPos) instanceof BritanniaSpawnBlockEntity,
                "Britannia spawn block did not create its block entity");
        BritanniaSpawnBlockEntity spawner =
                (BritanniaSpawnBlockEntity) helper.getLevel().getBlockEntity(spawnerPos);
        spawner.applyConfig(
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.FLAMINGO_ENTITY.get()),
                3, 1, 1, false, 1);

        spawner.serverTick();

        var flamingos = helper.getLevel().getEntitiesOfClass(
                FlamingoEntity.class, new AABB(spawnerPos).inflate(5.0D));
        check(flamingos.size() == 1,
                "Britannia spawn block did not add exactly one selected Flamingo to the world");
        check(spawner.getActiveEntityTypes(helper.getLevel()).equals(java.util.List.of(
                        BuiltInRegistries.ENTITY_TYPE.getKey(EntityRegistry.FLAMINGO_ENTITY.get()))),
                "Britannia spawn block did not track the added Flamingo");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
