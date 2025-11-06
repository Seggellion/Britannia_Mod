package com.seggellion.britannia_mod.features;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.minecraft.world.entity.EntityType;
import java.util.Set;
import net.neoforged.bus.api.EventPriority;

import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;


public final class MobSpawnControl {

    /* ------------ CONFIG ------------ */
    /** Maximum hostile mobs allowed in a 128-block cube around the candidate spawn. */
    private static final int MONSTER_CAP_NEARBY = 10;

    /** Entity types you simply don’t want at all. */
    private static final Set<EntityType<?>> BANNED_TYPES = Set.of(
            EntityType.CREEPER,
            EntityType.ENDERMAN,
            EntityType.WITCH,
            EntityType.PHANTOM,
            EntityType.ZOMBIE,
            EntityType.WANDERING_TRADER,
            EntityType.TRADER_LLAMA
    );

    /* ---- 1.  Strip skeleton bows after they load (works for natural spawns + saved chunks) ---- */
    @SubscribeEvent
    public  void onEntityJoinLevel(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof Skeleton skel) {
            skel.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /* ---- 2.  Pre-emptively cancel banned mobs and limit local monster density ---- */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public  void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck e) {

        /* Block your “never spawn these” list */
        if (BANNED_TYPES.contains(e.getEntityType())) {
            e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }

        /* Apply a local cap only to hostiles (MONSTER category) */
        if (e.getEntityType().getCategory() == MobCategory.MONSTER) {
            ServerLevel level = (ServerLevel) e.getLevel();

            // Count monsters already inside a 128-block cube centred on the candidate position
            int nearby = level.getEntitiesOfClass(Monster.class,
                    new AABB(e.getPos()).inflate(128)).size();

            if (nearby >= MONSTER_CAP_NEARBY) {
                e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        }
    }
}

