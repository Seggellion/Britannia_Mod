package com.seggellion.britannia_mod.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.network.chat.Component;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import net.minecraft.network.chat.Style;
import com.seggellion.britannia_mod.entity.ai.EscortPlayerGoal;
import net.minecraft.resources.ResourceLocation;

// FATAL CLIENT IMPORTS HAVE BEEN REMOVED

public class QuestGiverEntity extends CitizenEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * The identity Rails joins on: {@code quests.origin_npc}.
     *
     * <p>Before Milestone 7 this was <b>a substring of the display name</b> -- everything after
     * the first colon in {@code personalName} -- so any code path that rewrote that name for
     * presentation silently repointed the NPC at a different quest, or at none (finding Q-08).
     * A quest giver's identity is not presentation, so it now has its own synched, saved field.
     *
     * <p>Empty on entities spawned before this milestone, which is why
     * {@link #resolveQuestGiverApiId()} still understands the old encoding.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_QUEST_GIVER_API_ID =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            QuestGiverEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

    /**
     * Where this <em>particular</em> quest giver's landmarks are, copied from the spawner that
     * placed it (Rowan farming questline M6, discovery 6.5).
     *
     * <p>Deliberately not part of the identity. Two Rowans standing in different towns are one
     * {@code origin_npc} and one questline; only this string differs, and nothing that builds a
     * Rails request reads it -- see {@link #resolveQuestGiverApiId()}, which never looks here.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> DATA_LOCAL_DIRECTIONS =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            QuestGiverEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

    /**
     * The profession an archetype answers with, keyed by its Rails identity. Anything not listed
     * keeps the title every quest giver has always had, so no existing archetype changes.
     */
    private static final java.util.Map<String, String> ROLE_TITLES = java.util.Map.of(
        com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity.ROWAN_ARCHETYPE, "Farmer");

    private static final String DEFAULT_ROLE_TITLE = "Wanderer";

    private boolean reportedLegacyIdentity;

    public QuestGiverEntity(EntityType<? extends QuestGiverEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_QUEST_GIVER_API_ID, "");
        builder.define(DATA_LOCAL_DIRECTIONS, "");
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("questGiverApiId", getQuestGiverApiId());
        tag.putString("questGiverDirections", getLocalDirections());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("questGiverApiId")) {
            setQuestGiverApiId(tag.getString("questGiverApiId"));
        }
        if (tag.contains("questGiverDirections")) {
            setLocalDirections(tag.getString("questGiverDirections"));
        }
    }

    /** Sanitized and bounded by the spawner that supplies it; empty when this giver has no hint. */
    public void setLocalDirections(String directions) {
        this.entityData.set(DATA_LOCAL_DIRECTIONS,
            com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity.sanitizeDirections(directions));
    }

    public String getLocalDirections() {
        return this.entityData.get(DATA_LOCAL_DIRECTIONS);
    }

    /** The profession label for a Rails identity, without needing an entity to ask. */
    public static String roleTitleFor(String questGiverApiId) {
        if (questGiverApiId == null) return DEFAULT_ROLE_TITLE;
        return ROLE_TITLES.getOrDefault(questGiverApiId.trim(), DEFAULT_ROLE_TITLE);
    }

    public void setQuestGiverApiId(String apiId) {
        this.entityData.set(DATA_QUEST_GIVER_API_ID, apiId == null ? "" : apiId.trim());
    }

    public String getQuestGiverApiId() {
        return this.entityData.get(DATA_QUEST_GIVER_API_ID);
    }

    /**
     * The identity to send to Rails: the field when it is set, and the legacy display-name
     * encoding when it is not.
     *
     * <p>The fallback exists for every quest giver already standing in the world, and it says so
     * once per entity so an operator can see how many are still legacy without being flooded.
     */
    public String resolveQuestGiverApiId() {
        String stored = getQuestGiverApiId();
        if (stored != null && !stored.isBlank()) return stored.trim();

        String rawName = getPersonalName();
        String legacy = rawName == null || rawName.isBlank()
            ? ""
            : (rawName.contains(":") ? rawName.split(":", 2)[1].trim() : rawName.trim());

        if (!legacy.isBlank() && !reportedLegacyIdentity) {
            reportedLegacyIdentity = true;
            LOGGER.debug("event=quest_giver_legacy_identity entity={} api_id={} source=personal_name",
                getStringUUID(), legacy);
        }
        return legacy;
    }

private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");

private static final Style UO_STYLE = Style.EMPTY
            .withFont(FONT_UO_CLASSIC)
            .withColor(0x2194A5);

    /**
     * Driven by the identity, not by a stored field, so it cannot drift from the quest the giver
     * offers and needs nothing extra to survive a respawn. Widened to public so a test outside this
     * package can read it; no behaviour depends on the visibility.
     */
    @Override
    public String getRoleTitle() {
        return roleTitleFor(resolveQuestGiverApiId());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // Inherits the float and stroll goals from CitizenEntity
        
        // Priority 0: Always follow the player if the escort tag exists!
        // speedModifier = 1.2, stopDistance = 3 blocks, teleportDistance = 20 blocks
        this.goalSelector.addGoal(0, new EscortPlayerGoal(this, 1.2D, 3.0F, 20.0F));
    }

@Override
    public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            // Only run the UI logic on the client
            if (player.level().isClientSide) {
                String rawName = this.getPersonalName(); 
                if (rawName == null || rawName.isEmpty()) rawName = "Traveler";
                
                String parsedDisplayName = rawName;
                String internalApiId = rawName;

                if (rawName.contains(":")) {
                    String[] parts = rawName.split(":", 2); 
                    parsedDisplayName = parts[0]; 
                    internalApiId = parts[1];
                }

                final String displayNpcName = parsedDisplayName;
                String gender = this.getGender(); 
                if (gender == null || gender.isEmpty()) gender = "unknown";
                final String finalGender = gender;
                
                QuestClient.interactWithNpc(this.getId(), this.getUUID(), response -> {
                    if (response != null && response.error == null) {
                        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
                            com.seggellion.britannia_mod.network.ClientNetworkHandler.openQuestDecisionScreen(
                                response, displayNpcName, finalGender, this.getUUID()
                            );
                        }
                    } else {
                        String errMsg = (response != null && response.error != null) ? response.error : "I am busy, traveler.";
                        player.displayClientMessage(Component.literal("§e" + displayNpcName + " says: '" + errMsg + "'"), false);
                    }
                });
            }
            
            // Return SUCCESS on both sides to prevent desyncs and block-clicks!
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        
        return super.interactAt(player, hit, hand);
    }

    // --- OVERRIDES TO HIDE THE ENCODING FROM THE PLAYER ---
    @Override
    public Component getName() {
        String raw = this.getPersonalName();
        if (raw != null && raw.contains(":")) {
           return Component.literal(raw.split(":", 2)[0]).withStyle(UO_STYLE);
        }
        return super.getName().copy().withStyle(UO_STYLE);
    }

    @Override
    public Component getDisplayName() {
        String raw = this.getPersonalName();
        if (raw != null && raw.contains(":")) {
            return Component.literal(raw.split(":", 2)[0]).withStyle(UO_STYLE);
        }
        Component superName = super.getDisplayName();
        return superName != null ? superName.copy().withStyle(UO_STYLE) : Component.empty();
    }
}
