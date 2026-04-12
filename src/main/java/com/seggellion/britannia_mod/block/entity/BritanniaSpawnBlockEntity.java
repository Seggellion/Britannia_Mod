package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.stream.Collectors;

public class BritanniaSpawnBlockEntity extends BlockEntity {

    // ========= Configurable fields (saved) =========
    private ResourceLocation entityId = EntityRegistry.MONGBAT_ENTITY.getKey().location();
    private int spawnRadius = 8;
    private int randomMinTicks = 200;
    private int randomMaxTicks = 600;
    private boolean nightOnly = true;
    private int maxEntities = 4;

    // ========= Runtime =========
    private int cooldown = 40;
    private final Set<UUID> spawned = new HashSet<>();
    private final Map<UUID, Integer> outsideTicks = new HashMap<>();

    private static final int OUTSIDE_DESPAWN_TICKS = 20 * 10; // 10s
    private static final int BOUNDARY_MARGIN = 1;

    public BritanniaSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.BRITANNIA_SPAWN_BLOCK_ENTITY.get(), pos, state);
    }

    public void forceCleanup(ServerLevel sl) {
        for (UUID id : new HashSet<>(spawned)) {
            Entity e = sl.getEntity(id);
            if (e instanceof Mob mob) {
                mob.discard();
            }
        }
        spawned.clear();
        outsideTicks.clear();
    }


    // ===== Spawning & policing =====
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;

        pruneSpawned(sl);

        if (sl.getDifficulty().getId() == 0) { cooldown = 200; return; } // Peaceful
        if (nightOnly && sl.isDay()) { cooldown = 20; enforceBoundary(sl); return; }

        if (cooldown > 0) { cooldown--; enforceBoundary(sl); return; }

        if (spawned.size() >= Math.max(0, maxEntities)) {
            cooldown = 20;
            enforceBoundary(sl);
            return;
        }

        Optional<EntityType<?>> maybeType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (maybeType.isEmpty()) { cooldown = 100; enforceBoundary(sl); return; }
        EntityType<?> type = maybeType.get();
        if (type.getCategory() != MobCategory.MONSTER && type.getCategory() != MobCategory.CREATURE) { cooldown = 100; enforceBoundary(sl); return; }
        BlockPos spawnAt = findNearestValidSpawn(sl, worldPosition, spawnRadius);
        if (spawnAt != null) {
            Entity e = type.create(sl);
            if (e instanceof PathfinderMob pmob) {
                pmob.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5,
                        sl.random.nextFloat() * 360F, 0);
                pmob.restrictTo(this.worldPosition, Math.max(1, this.spawnRadius - BOUNDARY_MARGIN));
                pmob.goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(pmob, 1.1));
              //  pmob.setPersistenceRequired();
                sl.addFreshEntity(pmob);
                spawned.add(pmob.getUUID());
            } else if (e instanceof Mob mob) {
                mob.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5,
                        sl.random.nextFloat() * 360F, 0);
               // mob.setPersistenceRequired();
                sl.addFreshEntity(mob);
                spawned.add(mob.getUUID());
            }
        }

        RandomSource r = sl.getRandom();
        int range = Math.max(0, randomMaxTicks - randomMinTicks);
        cooldown = randomMinTicks + r.nextInt(range + 1);

        enforceBoundary(sl);
    }

private @Nullable BlockPos findNearestValidSpawn(ServerLevel sl, BlockPos origin, int radius) {
    RandomSource rand = sl.getRandom();

    // Try up to 20 random spots within the radius
    for (int i = 0; i < 20; i++) {
        int dx = rand.nextInt(-radius, radius + 1);
        int dz = rand.nextInt(-radius, radius + 1);

        BlockPos tryXZ = origin.offset(dx, 0, dz);

        // Only accept positions within circular radius (not just square)
        if (tryXZ.distSqr(origin) > (radius * radius)) continue;

        // Find ground height at this X/Z
        BlockPos groundTop = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tryXZ);
        BlockPos feet = groundTop.above();

        // Must have space for mob
        if (!hasThreeAir(sl, feet)) continue;

        // Check light (if night-only is enforced)
        int blockLight = sl.getBrightness(LightLayer.BLOCK, feet);
        if (blockLight > 7 && nightOnly) continue;

        return feet;
    }

    // If no valid position found after attempts
    return null;
}


    private boolean hasThreeAir(Level lvl, BlockPos feet) {
        return lvl.isEmptyBlock(feet) && lvl.isEmptyBlock(feet.above()) && lvl.isEmptyBlock(feet.above(2));
    }

private void pruneSpawned(ServerLevel sl) {
    if (spawned.isEmpty()) return;
    Iterator<UUID> it = spawned.iterator();
    while (it.hasNext()) {
        UUID id = it.next();
        Entity e = sl.getEntity(id);
        
        if (e != null) {
            if (!(e instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                it.remove();
                outsideTicks.remove(id);
            }
        }
    }
}

    private void enforceBoundary(ServerLevel sl) {
        if (spawned.isEmpty()) return;

        final double centerX = worldPosition.getX() + 0.5;
        final double centerY = worldPosition.getY() + 1;
        final double centerZ = worldPosition.getZ() + 0.5;
        final double rSq = (double) spawnRadius * spawnRadius;
        final double softR = Math.max(1, spawnRadius - BOUNDARY_MARGIN);
        final double hardR = spawnRadius + 2;

        for (UUID id : new ArrayList<>(spawned)) {
            Entity e = sl.getEntity(id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                spawned.remove(id);
                outsideTicks.remove(id);
                continue;
            }

            mob.restrictTo(this.worldPosition, Math.max(1, this.spawnRadius - BOUNDARY_MARGIN));

            double dx = mob.getX() - centerX;
            double dz = mob.getZ() - centerZ;
            double distSq = dx * dx + dz * dz;

            if (distSq <= rSq) {
                double dist = Math.sqrt(distSq);
                if (dist >= softR) {
                    if (mob.getTarget() != null) mob.setTarget(null);
                    double nx = centerX + dx / Math.max(0.001, dist) * (softR - 2);
                    double nz = centerZ + dz / Math.max(0.001, dist) * (softR - 2);
                    mob.getNavigation().moveTo(nx, centerY, nz, 1.2);
                }
                outsideTicks.remove(id);
                continue;
            }

            int t = outsideTicks.getOrDefault(id, 0) + 1;
            outsideTicks.put(id, t);

            if (mob.getTarget() != null) mob.setTarget(null);
            mob.getNavigation().moveTo(centerX, centerY, centerZ, 1.3);

            if (distSq > (hardR * hardR)) {
                var ground = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPosition);
                mob.teleportTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
                mob.getNavigation().stop();
            }

            if (t >= OUTSIDE_DESPAWN_TICKS) {
                mob.discard();
                spawned.remove(id);
                outsideTicks.remove(id);
            }
        }
    }

    // ===== Persistence =====
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("entityId", entityId.toString());
        tag.putInt("spawnRadius", spawnRadius);
        tag.putInt("randomMinTicks", randomMinTicks);
        tag.putInt("randomMaxTicks", randomMaxTicks);
        tag.putBoolean("nightOnly", nightOnly);
        tag.putInt("maxEntities", maxEntities);
        tag.putInt("cooldown", cooldown);

        ListTag list = new ListTag();
        for (UUID id : spawned) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            list.add(t);
        }
        tag.put("spawned", list);
    }

    public List<ResourceLocation> getActiveEntityTypes(ServerLevel level) {
        List<ResourceLocation> list = new ArrayList<>();
        for (UUID id : spawned) {
            Entity e = level.getEntity(id);
            if (e != null) {
                list.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
            }
        }
        return list;
    }


    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("entityId")) entityId = ResourceLocation.parse(tag.getString("entityId"));
        spawnRadius     = tag.getInt("spawnRadius");
        randomMinTicks  = tag.getInt("randomMinTicks");
        randomMaxTicks  = tag.getInt("randomMaxTicks");
        nightOnly       = tag.getBoolean("nightOnly");
        maxEntities     = tag.contains("maxEntities") ? tag.getInt("maxEntities") : 4;
        cooldown        = tag.getInt("cooldown");

        spawned.clear();
        outsideTicks.clear();
        if (tag.contains("spawned", Tag.TAG_LIST)) {
            ListTag list = tag.getList("spawned", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                if (t.hasUUID("id")) spawned.add(t.getUUID("id"));
            }
        }
    }


    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel sl) {
            cleanupOnRemove(sl);
        }
    }

    // ===== Config application =====
public void applyConfig(ResourceLocation id, int radius, int minTicks, int maxTicks,
                        boolean nightOnly, int maxEntities) {
    boolean typeChanged = !id.equals(this.entityId);

    this.entityId = id;
    this.spawnRadius = Mth.clamp(radius, 1, 128);
    this.randomMinTicks = Math.max(1, Math.min(minTicks, maxTicks));
    this.randomMaxTicks = Math.max(this.randomMinTicks, maxTicks);
    this.nightOnly = nightOnly;
    this.maxEntities = Math.max(0, maxEntities);

    if (typeChanged && level instanceof ServerLevel sl) {
        // wipe existing mobs
        for (UUID uuid : new HashSet<>(spawned)) {
            Entity e = sl.getEntity(uuid);
            if (e != null) e.discard(); // no drops/XP
        }
        spawned.clear();
        outsideTicks.clear();
    }

    setChanged();
    if (level != null) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}


   // Add this helper (server-side cleanup)
    private void cleanupOnRemove(ServerLevel sl) {
        for (UUID id : new HashSet<>(spawned)) {
            var e = sl.getEntity(id);
            if (e != null) e.discard(); // no drops/XP
        }
        spawned.clear();
        outsideTicks.clear();
    }

    // Run cleanup when the BE is removed (block broken/replaced or otherwise removed)
    @Override
    public void setRemoved() {
        // call super first or last—either is fine here, but be consistent with your codebase
        super.setRemoved();
        if (level instanceof ServerLevel sl) {
            cleanupOnRemove(sl);
        }
    }

    // ===== Exposed config getters =====
    public ResourceLocation getEntityId() { return entityId; }
    public int getSpawnRadius() { return spawnRadius; }
    public int getRandomMinTicks() { return randomMinTicks; }
    public int getRandomMaxTicks() { return randomMaxTicks; }
    public boolean isNightOnly() { return nightOnly; }
    public int getMaxEntities() { return maxEntities; }
}
