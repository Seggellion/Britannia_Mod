package com.seggellion.britannia_mod.economy;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Which NPCs this server is allowed to register into Rails' legacy live-NPC table, and under what
 * spawn identity.
 *
 * <p>A plain class rather than logic inside {@link ServerEconomyService}: Architecture Decision 0
 * means neither test harness can construct a Minecraft entity, so a rule that reads an entity is a
 * rule no unit test can reach. The two decisions below are the whole of what changed about this
 * server's NPC identity contract, so they are exactly the part that needs to be assertable.
 *
 * <h2>The identity model</h2>
 * <table>
 *   <tr><td>World NPC public id</td><td>the LOGICAL NPC. Rails generates it and owns its
 *       lifecycle. Survives server restart, mod reload, chunk unload/reload, entity death, HTTP
 *       retry and reconciliation. Changes only when the NPC is genuinely a different NPC.</td></tr>
 *   <tr><td>spawn source id</td><td>the PHYSICAL spawn source -- a legacy spawn block's
 *       NBT-persisted {@code SourceId}, or a Rails spawn point's public id. Survives everything
 *       that does not move or replace the block.</td></tr>
 *   <tr><td>Minecraft entity UUID</td><td>ONE INCARNATION of an NPC. Legitimately changes every
 *       time the entity is re-materialized. Never a durable identity for anything.</td></tr>
 * </table>
 */
public final class LiveNpcSyncPolicy {
    /** Tag prefix written by {@code TraderSpawnBlockEntity.addSourceTags}. */
    public static final String TRADER_SOURCE_TAG_PREFIX = "trader_source_";

    private LiveNpcSyncPolicy() {
    }

    /**
     * True when this NPC is a projection of a Rails-owned identity, and therefore must NOT be
     * registered into the legacy live-NPC table by this server.
     *
     * <p>Rails writes that row itself the moment it staffs the post
     * ({@code NpcSpawnAssignments::Create}), keyed on the World NPC public id. This server
     * announcing the same NPC under the current mob's UUID is what produced the duplicate Britain
     * fish traders: two rows, one logical NPC, both permanently alive, both counted in city
     * population.
     *
     * <p>Both fields are required because both are set together, from the same bootstrap
     * assignment snapshot, by {@code ServiceNpcAssignmentReconciler.reconcileEconomic}. Half a
     * projection is a partially reconciled entity, not an authoritative one.
     */
    public static boolean railsAuthoritative(@Nullable UUID worldNpcPublicId,
                                             @Nullable String economicNpcTypeKey) {
        return worldNpcPublicId != null
                && economicNpcTypeKey != null
                && !economicNpcTypeKey.isBlank();
    }

    /**
     * The stable id of the legacy spawn block that owns this entity, or null when nothing does.
     *
     * <p>Null, deliberately, rather than the entity's own UUID. The old fallback reported a mob's
     * UUID as the spawn block that produced it, which Rails then stored as the NPC's permanent
     * spawn identity -- so a "spawn source" changed every time the mob did, and the corrupted
     * production rows are recognisable by {@code spawn_block_id == npc_id}. An omitted field is
     * the honest answer, and Rails leaves what it already knows in place rather than overwriting
     * it with a value that means nothing.
     */
    @Nullable
    public static String legacySpawnSourceId(@Nullable Collection<String> entityTags) {
        if (entityTags == null) return null;

        for (String tag : entityTags) {
            if (tag == null) continue;
            if (tag.startsWith(TRADER_SOURCE_TAG_PREFIX) && tag.length() > TRADER_SOURCE_TAG_PREFIX.length()) {
                return tag.substring(TRADER_SOURCE_TAG_PREFIX.length());
            }
        }
        return null;
    }
}
