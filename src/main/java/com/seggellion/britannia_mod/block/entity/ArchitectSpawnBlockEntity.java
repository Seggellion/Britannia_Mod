package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.entity.ArchitectEntity;
import com.seggellion.britannia_mod.entity.TownPersonEntity;
import com.seggellion.britannia_mod.network.CityDataSync;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.service.spawn.LegacySpawnBlockMigrator;
import com.seggellion.britannia_mod.util.HasCityName;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A legacy Architect post, waiting to become an authoritative one.
 *
 * <h2>What this block used to decide, and no longer does</h2>
 * It carried its own economy: 400 food and 200 wood in the city, polled every 1200 ticks, and
 * on that alone it spawned an Architect and two townspeople, or despawned everything it had
 * spawned. Neither number had anything to do with building a house, and worse, it was a second
 * opinion -- a city Rails considered ineligible could still be staffed here, and a city Rails
 * had staffed could be emptied by a bad harvest.
 *
 * <p>Rails now owns the decision entirely: a treasury floor and standing stock of the five
 * materials the Architect builds from, with its own dwell rules so a post does not flap. None
 * of those numbers appear in this repository, and none should. The count of economic thresholds
 * in mod production code is zero.
 *
 * <h2>What it does instead</h2>
 * It migrates itself, once, onto the same authoritative post architecture the merchant and
 * trader blocks were moved onto in Vendor/Trader Milestone 16 -- and then it is gone. From
 * there the existing pipeline does everything: the post registers with Rails carrying
 * {@code economic:architect_vendor}, Rails opens or closes an assignment against it, and
 * {@code ServiceNpcAssignmentReconciler} materializes or removes the Architect to match. That
 * reconciler is also what stamps the entity with its economic type, city and world-NPC id,
 * which is what makes the Milestone 7 deed catalogue work on a living Architect rather than
 * only on paper.
 *
 * <p>Until the migration succeeds -- Rails unreachable, city bootstrap not yet loaded -- this
 * block does nothing at all. That is deliberate. The merchant and trader blocks keep their
 * legacy behaviour while they wait because their legacy behaviour was harmless; this one's was
 * the very gate being retired, so there is nothing to fall back to.
 *
 * <p>Townspeople are not this block's business any more either. {@code
 * TownPersonPopulationManager} converges a city toward the population Rails says its prosperity
 * supports, which is the regional system the migration notes were waiting on. Any townspeople
 * this block already spawned are left standing when it migrates, exactly as a migrating
 * merchant block leaves its own.
 */
public class ArchitectSpawnBlockEntity extends BlockEntity implements HasCityName {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * How often migration is retried. Not an economic cadence -- there is no economics here to
     * re-evaluate. It is how long this block waits before asking again whether Rails and the
     * city registry have become available.
     */
    private static final int MIGRATION_RETRY_TICKS = 1200;

    private int migrationRetryCooldown = 0;
    private String cityName = "";
    private final List<UUID> associatedNpcs = new ArrayList<>();

    public ArchitectSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ARCHITECT_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    @Override
    public String getCityName() {
        return cityName;
    }

    public void setCityName(String name) {
        this.cityName = name;
        setChanged();
    }

    /** Townspeople this block spawned before the population manager took the job over. */
    public int getTrackedTownPersonCount() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        return (int) associatedNpcs.stream()
                .map(serverLevel::getEntity)
                .filter(entity -> entity instanceof TownPersonEntity)
                .count();
    }

@Override
public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
    CompoundTag tag = super.getUpdateTag(provider);
    tag.putString("CityName", cityName);
    return tag;
}

@Override
public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
    super.handleUpdateTag(tag, provider);
    this.cityName = tag.getString("CityName");
}

@Override
public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}

public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
    HolderLookup.Provider provider = (this.level != null) ? this.level.registryAccess() : null;
    this.handleUpdateTag(pkt.getTag(), provider);
}

    public void tick() {
        if (level == null || level.isClientSide || migrationRetryCooldown-- > 0) return;
        if (cityName == null || cityName.isEmpty()) return;

        migrationRetryCooldown = MIGRATION_RETRY_TICKS;
        ServerLevel serverLevel = (ServerLevel) level;

        // Replaces this block when it succeeds, so nothing may run after it.
        LegacySpawnBlockMigrator.migrateArchitectBlock(serverLevel, this);
    }

    /**
     * Removes the Architect this block was managing, immediately before migration replaces it.
     *
     * <p>The authoritative pipeline staffs the new post with its own persistent world NPC, so a
     * legacy Architect left standing would simply be a second one. Townspeople are deliberately
     * spared -- owner decision #12 leaves them to the regional population manager -- and are
     * dropped from this block's tracking so that its removal does not take them with it. A
     * migrating merchant block reaches the same outcome by never having tracked them.
     */
    public void despawnManagedNpcForMigration(ServerLevel serverLevel) {
        for (UUID id : associatedNpcs) {
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof ArchitectEntity)) continue;

            entity.remove(RemovalReason.DISCARDED);
            CityDataSync.removeNpcAsync(serverLevel, id);
            LOGGER.info("Legacy architect despawned for migration source={} npc={} city={}",
                    worldPosition.toShortString(), id, cityName);
        }
        associatedNpcs.clear();
    }

    private void despawnAssociatedNpcs(ServerLevel sl) {
        for (UUID id : associatedNpcs) {
            Entity e = sl.getEntity(id);
            if (e != null) {
                e.remove(RemovalReason.DISCARDED);
                CityDataSync.removeNpcAsync(sl, id);
            }
        }
        associatedNpcs.clear();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) despawnAssociatedNpcs(sl);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("CityName", cityName);

        ListTag list = new ListTag();
        for (UUID id : associatedNpcs) {
            CompoundTag t = new CompoundTag();
            t.putUUID("NPC", id);
            list.add(t);
        }
        tag.put("AssociatedNPCs", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        cityName = tag.getString("CityName");

        associatedNpcs.clear();
        ListTag list = tag.getList("AssociatedNPCs", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            associatedNpcs.add(t.getUUID("NPC"));
        }
    }
}
