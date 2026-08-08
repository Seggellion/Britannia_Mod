package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.service.ServiceActionDispatcher;
import com.seggellion.britannia_mod.service.banking.BankingCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.DifficultyInstance;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Milestone 6 Slice 3b: the generic Service NPC entity itself, in isolation.
 * Extends {@link CitizenEntity} directly as a sibling to {@link QuestGiverEntity}
 * (not extending it, not extending any trader class) — the compatibility map names
 * a new class as the recommendation and never designates CitizenEntity's existing
 * subclasses as a base, and this sibling relationship keeps
 * {@code CitizenEntity.shouldBeSaved()}'s closed trader-exclusion instanceof chain
 * from ever matching it, so it persists correctly by inherited default.
 *
 * <p>Nothing in this slice spawns this entity from real gameplay: no bootstrap
 * consumption, no chunk-load hook, no reconciler. It is only reachable through
 * direct construction (test/manual code) and {@link #assignHomePost(BlockPos)}.
 * That wiring is Slice 3c.
 */
public class ServiceNpcEntity extends CitizenEntity {
    /**
     * Default stationary-post radius, matching AlcoholTraderEntity's own literal
     * exactly. Used for any natural-spawn safety net and for callers that don't need
     * a different radius. Not necessarily correct for every Service NPC type forever:
     * "Service NPC" is deliberately generic (service_npc_type_key already varies per
     * instance today, e.g. "bank_teller"), and different service types may reasonably
     * need different post radii — a teller fixed behind a counter versus, say, a
     * market vendor free to move around a stall. Slice 3c's reconciler is where that
     * per-type/per-assignment decision actually gets made (from Rails-configured data
     * this slice has no access to); this class only needs to be able to accept
     * whatever radius 3c decides on, hence the {@link #assignHomePost(BlockPos, int)}
     * overload below.
     */
    private static final int DEFAULT_HOME_RESTRICTION_RADIUS = 1;

    @Nullable private UUID spawnPointId;
    @Nullable private UUID assignmentPublicId;
    @Nullable private String serviceNpcTypeKey;
    private long definitionRevision;
    private long assignmentRevision;

    public ServiceNpcEntity(EntityType<? extends ServiceNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Service NPC";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    // ---------- Goals: stay rigidly at the assigned post ----------
    // A Service NPC (e.g. a bank teller) must be reliably found at its assigned
    // spawn point, matching AlcoholTraderEntity's exact stationary pattern rather
    // than entity/ai/RestrictedStrollGoal's wander-within-radius behavior. That
    // class is dead code today (never instantiated anywhere in the mod) and
    // reviving unexercised code for a person players transact banking through is
    // a worse risk than the minor immersion cost of standing still. See the
    // completion report for the full justification.
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // Deliberately no RandomStrollGoal: this entity does not wander voluntarily.
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                         MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        // Safety-net default for any natural spawn path, mirroring AlcoholTraderEntity
        // exactly. Nothing in this slice triggers a natural spawn; assignHomePost
        // below is how test/manual code sets the real assigned position.
        this.restrictTo(this.blockPosition(), DEFAULT_HOME_RESTRICTION_RADIUS);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    /**
     * Restricts this entity to a specific assigned position using the default radius.
     * No bootstrap or assignment data feeds this in Slice 3b — test/manual code
     * supplies the position directly, standing in for the spawn point Slice 3c will
     * eventually resolve this from.
     */
    public void assignHomePost(BlockPos pos) {
        assignHomePost(pos, DEFAULT_HOME_RESTRICTION_RADIUS);
    }

    /**
     * Restricts this entity to a specific assigned position with an explicit radius,
     * for callers (Slice 3c's reconciler) that resolve a per-type or per-assignment
     * radius from Rails-configured data rather than accepting the default.
     */
    public void assignHomePost(BlockPos pos, int radius) {
        this.restrictTo(pos, radius);
    }

    // ---------- Interaction: Milestone 7 Slice A entry point ----------
    // Genuinely new: ServiceNpcEntity had no interactAt/mobInteract override before this
    // slice (confirmed by recon). Deliberately server-side-only rather than mirroring
    // QuestGiverEntity's own client-triggered-then-C2S-payload mechanism: vanilla already
    // calls interactAt on the logical server for a real player interaction (this is the
    // authoritative call, not a mirror of client prediction), so there is no need to
    // round-trip a client-supplied entity/UUID through a new payload just to get back to
    // a server context we are already in. The `player instanceof ServerPlayer` pattern
    // match is what actually excludes the client-side call (a client-side interactAt
    // invocation receives a client-only Player, never a ServerPlayer, so it can never
    // satisfy this branch and falls through to super unconditionally); `!level().isClientSide`
    // is kept alongside it as a second, redundant-by-construction guard consistent with
    // this codebase's layered-validation convention elsewhere.
    //
    // Gated on live capability, not a hardcoded service key: only a teller whose current
    // ServiceNpcType (from the server-side ServiceNpcRegistryCache, the same source
    // ServiceActionDispatcher already trusts) actually allows "bank.open" triggers this at
    // all. Any other teller (a future non-banking Service NPC type) falls through to
    // super.interactAt unchanged.
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide
                && player instanceof ServerPlayer serverPlayer
                && BankingCapability.supportsBankOpen(this.getServiceNpcTypeKey())) {
            ServiceActionDispatcher.dispatchBankOpen(serverPlayer, this);
            return InteractionResult.sidedSuccess(false);
        }
        return super.interactAt(player, hit, hand);
    }

    // ---------- Despawn prevention ----------
    // Verified against the real decompiled 1.21.1 Mob source: checkDespawn() skips
    // its entire distance/timer logic whenever isPersistenceRequired() (or
    // requiresCustomPersistence(), irrelevant here) returns true. Overriding the
    // getter directly — rather than only calling the no-arg setPersistenceRequired()
    // once at construction — makes this immune to a stale "PersistenceRequired=false"
    // NBT tag ever silently re-enabling despawn on load, since
    // Mob.readAdditionalSaveData unconditionally overwrites the backing field from
    // whatever was saved. This is deliberate and explicit, not a ride on vanilla's
    // incidental "named entities are despawn-exempt" behavior other CitizenEntity
    // subclasses currently depend on.
    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ---------- Persistent identity beyond the inherited World NPC UUID ----------
    // CitizenEntity already provides getWorldNpcPublicId()/setWorldNpcPublicId(),
    // live-wired into its own NBT cycle (Milestone 2). These five fields mirror
    // ServiceNpcSpawnBlockEntity's existing (still-empty) scaffolding field-for-field
    // so a future Slice 3c reconciler can move data between the block entity and
    // this mob entity without a naming mismatch.

    @Nullable
    public UUID getSpawnPointId() {
        return spawnPointId;
    }

    public void setSpawnPointId(@Nullable UUID spawnPointId) {
        this.spawnPointId = spawnPointId;
    }

    @Nullable
    public UUID getAssignmentPublicId() {
        return assignmentPublicId;
    }

    public void setAssignmentPublicId(@Nullable UUID assignmentPublicId) {
        this.assignmentPublicId = assignmentPublicId;
    }

    @Nullable
    public String getServiceNpcTypeKey() {
        return serviceNpcTypeKey;
    }

    public void setServiceNpcTypeKey(@Nullable String serviceNpcTypeKey) {
        this.serviceNpcTypeKey = serviceNpcTypeKey;
    }

    public long getDefinitionRevision() {
        return definitionRevision;
    }

    public void setDefinitionRevision(long definitionRevision) {
        this.definitionRevision = definitionRevision;
    }

    public long getAssignmentRevision() {
        return assignmentRevision;
    }

    public void setAssignmentRevision(long assignmentRevision) {
        this.assignmentRevision = assignmentRevision;
    }

    // ---------- Save / Load ----------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (spawnPointId != null) tag.putUUID("SpawnPointId", spawnPointId);
        if (assignmentPublicId != null) tag.putUUID("AssignmentPublicId", assignmentPublicId);
        if (serviceNpcTypeKey != null) tag.putString("ServiceNpcTypeKey", serviceNpcTypeKey);
        tag.putLong("DefinitionRevision", definitionRevision);
        tag.putLong("AssignmentRevision", assignmentRevision);
        // Vanilla Mob never serializes restrictCenter/restrictRadius (confirmed in the
        // decompiled source: Mob.addAdditionalSaveData has no such write) — without this,
        // every chunk unload/reload or server restart would silently strand the entity's
        // home-post restriction, letting it wander freely afterward.
        if (this.hasRestriction()) {
            tag.put("HomePost", NbtUtils.writeBlockPos(this.getRestrictCenter()));
            tag.putInt("HomePostRadius", Math.round(this.getRestrictRadius()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.spawnPointId = tag.hasUUID("SpawnPointId") ? tag.getUUID("SpawnPointId") : null;
        this.assignmentPublicId = tag.hasUUID("AssignmentPublicId") ? tag.getUUID("AssignmentPublicId") : null;
        this.serviceNpcTypeKey = tag.contains("ServiceNpcTypeKey") ? tag.getString("ServiceNpcTypeKey") : null;
        this.definitionRevision = tag.getLong("DefinitionRevision");
        this.assignmentRevision = tag.getLong("AssignmentRevision");
        int homePostRadius = tag.contains("HomePostRadius")
                ? tag.getInt("HomePostRadius")
                : DEFAULT_HOME_RESTRICTION_RADIUS;
        NbtUtils.readBlockPos(tag, "HomePost").ifPresent(pos -> assignHomePost(pos, homePostRadius));
    }
}
