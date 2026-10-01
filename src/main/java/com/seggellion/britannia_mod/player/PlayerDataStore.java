// com/seggellion/britannia_mod/player/PlayerDataStore.java
package com.seggellion.britannia_mod.player;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.quest.handin.QuestHandinRemoval;
import com.seggellion.britannia_mod.quest.handin.QuestHandinRemovalNbt;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataStore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerDataStore() {}
    private static final String KEY = "britannia_player";

    /**
     * The compound this mod keeps inside {@code player.getPersistentData()}.
     *
     * <p>Public because a respawn does not carry it: {@code ServerPlayer#restoreFrom} copies only
     * the {@code PlayerPersisted} sub-tag, so {@code PlayerDataCloneHandler} has to move this one
     * across by hand. THREE durable records below live in here, and they fail in two different
     * directions: losing the hand-in removal marker or the bounded delivery marker to a death is how
     * a player loses an item, while losing the PERMANENT applied-delivery proof (D-0102) risks the
     * opposite - a delivery Rails re-hands being granted a second time, because the proof is the only
     * record that outlives the bounded ones.
     */
    public static final String PERSISTENT_KEY = KEY;

    /**
     * Rowan farming questline M3 (protocol section 1.8): the bounded list of reward deliveries
     * whose items this player received, kept in the same persistent compound as the rest of the
     * player's mod data. It is appended in the same server-thread step as the item insertion, so
     * the vanilla player-file write (temp file, {@code SYNC}, atomic replace) persists the items
     * and the marker together or not at all -- which is what lets a restart tell "inserted and
     * saved" from "inserted and lost" without a second grant.
     */
    public static final String APPLIED_DELIVERY_UUIDS = "applied_delivery_uuids";
    public static final int MAX_APPLIED_DELIVERY_MARKERS = 256;

    /**
     * D-0102: the PERMANENT record of every delivery whose items this player received. The list
     * above is a bounded operational view and evicts its oldest entries; this one never evicts,
     * never expires and is never pruned, because Rails can re-hand an acknowledged
     * {@code delivery_uuid} indefinitely - {@code QuestRewardDeliveries::Publish#find_existing}
     * has no state filter, and {@code publish.rb} records having handed back a uuid whose items
     * were already granted. Once both bounded records for such a uuid had aged out, the
     * reconciliation table read it as new and granted the items a second time. This is the record
     * that refuses that, and it is the reason the bounded list is allowed to stay bounded.
     *
     * <p>Stored as ONE flat {@code IntArrayTag} of four ints per uuid ({@link UUIDUtil}), which is
     * 16 bytes each rather than the ~40 a string entry costs, because this list is unbounded and
     * lives in the player file. It is appended in the same in-memory step as the item insertion,
     * so the vanilla player-file write persists the items and the proof together or not at all.
     */
    public static final String APPLIED_DELIVERY_PROOF = "applied_delivery_proof";

    /** Four ints per uuid, so a proof array is always a multiple of this. */
    private static final int PROOF_STRIDE = 4;

    /**
     * Where an unreadable proof tag is kept verbatim rather than being dropped, modelled on
     * {@code QuestRewardDeliveryLedgerStore}'s per-entry quarantine. "No silent pruning" has to hold
     * for data this build cannot parse as well as for data it can: erasing it would destroy the only
     * evidence that some delivery was granted, which is the opposite of what this record is for.
     */
    public static final String APPLIED_DELIVERY_PROOF_QUARANTINE = "applied_delivery_proof_quarantine";

    public static PlayerData get(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag dataTag = root.getCompound(KEY);
        if (dataTag.isEmpty() || !dataTag.contains("UUID", Tag.TAG_STRING)) {
            PlayerData fresh = new PlayerData(player.getUUID());
            save(player, fresh);
            return fresh;
        }
        return PlayerData.load(dataTag);
    }

    /**
     * Rowan farming questline, strict item hand-ins (protocol section 1.5): the bounded list of
     * hand-in removals whose items have left this player's pack, each carrying the concrete proof
     * of what was taken.
     *
     * <p>It is appended in the same server-thread step as the shrink, so the vanilla player-file
     * write (temp file, atomic replace) persists the removal and its proof together or not at all.
     * That is what lets a restart tell "removed and saved" from "removed and lost" -- and, because
     * each marker carries the whole proof rather than an id, a marker found without its ledger row
     * is still enough to confirm the transaction and get the player either their quest or their
     * items back.
     */
    public static final String HANDIN_REMOVALS = "handin_removals";

    /**
     * Bounded like the delivery markers, but never dropped to make room: a marker is removed only
     * when its transaction has reached an ending, so hitting this bound means the player genuinely
     * holds that many unfinished hand-ins and the ledger refuses to mint another.
     */
    public static final int MAX_HANDIN_REMOVAL_MARKERS = 64;

    private static final String MARKER_HANDIN_UUID = "HandinUuid";
    private static final String MARKER_REQUEST_UUID = "RequestUuid";
    private static final String MARKER_REMOVED_AT = "RemovedAt";
    private static final String MARKER_PROOF = "Proof";

    public static void save(ServerPlayer player, PlayerData data) {
        CompoundTag root = player.getPersistentData();
        CompoundTag out = new CompoundTag();
        data.save(out);
        // Neither marker list is part of PlayerData; carry both across so a profile save (the
        // bootstrap writes one at every login) can never drop them.
        CompoundTag existing = root.getCompound(KEY);
        ListTag markers = markerList(existing);
        if (!markers.isEmpty()) out.put(APPLIED_DELIVERY_UUIDS, markers.copy());
        ListTag handins = handinList(existing);
        if (!handins.isEmpty()) out.put(HANDIN_REMOVALS, handins.copy());
        // The permanent proof is carried across for the same reason and more strictly: dropping it
        // would re-open the duplicate grant this record exists to refuse.
        // Copied VERBATIM, never reconstructed from a parsed view: a save must not be able to
        // narrow, reorder or drop either tag, readable or not.
        if (existing.contains(APPLIED_DELIVERY_PROOF)) {
            out.put(APPLIED_DELIVERY_PROOF, existing.get(APPLIED_DELIVERY_PROOF).copy());
        }
        if (existing.contains(APPLIED_DELIVERY_PROOF_QUARANTINE)) {
            out.put(APPLIED_DELIVERY_PROOF_QUARANTINE, existing.get(APPLIED_DELIVERY_PROOF_QUARANTINE).copy());
        }
        root.put(KEY, out);
    }

    // --- hand-in removal marker -----------------------------------------------------------------

    /** What one marker records: the transaction, the correlation id, and exactly what was taken. */
    public record HandinRemovalMarker(UUID handinUuid, UUID requestUuid, long removedAtMillis,
                                      List<QuestHandinRemoval> proof) {
        public HandinRemovalMarker {
            java.util.Objects.requireNonNull(handinUuid, "handinUuid");
            java.util.Objects.requireNonNull(requestUuid, "requestUuid");
            proof = List.copyOf(proof);
            if (proof.isEmpty()) {
                throw new IllegalArgumentException("a removal marker without its proof proves nothing");
            }
        }
    }

    /**
     * Records that the hand-in's items have left the pack. Idempotent. Only mutates the in-memory
     * persistent data: the caller forces the player-file write, in the same step as the shrink.
     *
     * @return false when the player already holds the maximum unfinished markers, in which case
     *         nothing is recorded and the caller must not remove anything
     */
    public static boolean markHandinRemoved(ServerPlayer player, HandinRemovalMarker marker) {
        if (player == null || marker == null) return false;
        get(player);
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = handinList(dataTag);
        String value = marker.handinUuid().toString();
        for (Tag tag : markers) {
            if (tag instanceof CompoundTag entry
                    && value.equals(entry.getString(MARKER_HANDIN_UUID))) {
                return true;
            }
        }
        // Never evicts to make room. A marker is the only durable evidence that this player gave
        // something up, so the bound refuses a new removal instead of forgetting an old one.
        if (markers.size() >= MAX_HANDIN_REMOVAL_MARKERS) return false;

        CompoundTag entry = new CompoundTag();
        entry.putString(MARKER_HANDIN_UUID, value);
        entry.putString(MARKER_REQUEST_UUID, marker.requestUuid().toString());
        entry.putLong(MARKER_REMOVED_AT, marker.removedAtMillis());
        entry.put(MARKER_PROOF, QuestHandinRemovalNbt.toList(marker.proof()));
        markers.add(entry);
        dataTag.put(HANDIN_REMOVALS, markers);
        return true;
    }

    /**
     * Whether the player's own file records a removal for this transaction <b>at all</b>, readable
     * or not.
     *
     * <p>The distinction matters more than it looks. A marker and the shrink that produced it are
     * written in one in-memory step, so an entry existing is proof the items left the pack, whatever
     * state its bytes are in. Absence means the mutation never reached disk and the player still has
     * everything; a corrupt entry means the opposite. Collapsing the two would either strand a
     * player who could simply hand in again, or offer a second removal to one who already paid.
     */
    public static boolean handinRemovalRecorded(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return false;
        String value = handinUuid.toString();
        for (Tag tag : handinList(player.getPersistentData().getCompound(KEY))) {
            if (tag instanceof CompoundTag entry && value.equals(entry.getString(MARKER_HANDIN_UUID))) {
                return true;
            }
        }
        return false;
    }

    /** The marker for one transaction, or empty. */
    public static Optional<HandinRemovalMarker> handinRemoval(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return Optional.empty();
        String value = handinUuid.toString();
        for (HandinRemovalMarker marker : handinRemovals(player)) {
            if (marker.handinUuid().toString().equals(value)) return Optional.of(marker);
        }
        return Optional.empty();
    }

    /** Every readable marker, oldest first. A marker this build cannot read names no transaction. */
    public static List<HandinRemovalMarker> handinRemovals(ServerPlayer player) {
        List<HandinRemovalMarker> found = new ArrayList<>();
        if (player == null) return found;
        for (Tag tag : handinList(player.getPersistentData().getCompound(KEY))) {
            if (!(tag instanceof CompoundTag entry)) continue;
            try {
                found.add(new HandinRemovalMarker(
                        UUID.fromString(entry.getString(MARKER_HANDIN_UUID)),
                        UUID.fromString(entry.getString(MARKER_REQUEST_UUID)),
                        entry.getLong(MARKER_REMOVED_AT),
                        QuestHandinRemovalNbt.fromList(entry.getList(MARKER_PROOF, Tag.TAG_COMPOUND))));
            } catch (RuntimeException unreadable) {
                // Skipped rather than thrown: one bad marker must not hide the others. It is NOT
                // treated as absent, though -- see handinRemovalRecorded. An entry existing at all
                // proves the shrink happened, because the two were written in one step, so reading
                // "unreadable" as "never removed" would offer the player a second removal.
                LOGGER.warn("event=quest_handin_marker_unreadable player_uuid={} detail={}",
                        player.getStringUUID(), unreadable.toString());
            }
        }
        return found;
    }

    /**
     * Forgets one marker, once its transaction has reached an ending.
     *
     * <p>Unlike the delivery markers, production does remove these -- an unfinished hand-in is a
     * bounded, short-lived thing and a marker that outlived its row would look like an orphan worth
     * confirming. It is removed only <b>after</b> the ledger row is durably settled, so a crash in
     * between leaves a stale marker whose row already says the transaction is over, which
     * reconciliation ignores.
     */
    public static boolean forgetHandinRemoval(ServerPlayer player, UUID handinUuid) {
        if (player == null || handinUuid == null) return false;
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = handinList(dataTag);
        String value = handinUuid.toString();
        boolean removed = markers.removeIf(tag -> tag instanceof CompoundTag entry
                && value.equals(entry.getString(MARKER_HANDIN_UUID)));
        if (removed) dataTag.put(HANDIN_REMOVALS, markers);
        return removed;
    }

    private static ListTag handinList(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(HANDIN_REMOVALS, Tag.TAG_LIST)) return new ListTag();
        return dataTag.getList(HANDIN_REMOVALS, Tag.TAG_COMPOUND);
    }

    // --- reward delivery marker (M3) ------------------------------------------------------------

    /** Whether this player's persistent data records the delivery's items as inserted. */
    public static boolean hasAppliedDelivery(ServerPlayer player, UUID deliveryUuid) {
        if (player == null) return false;
        // One copy of the marker scan, in hasAppliedDeliveryIn.
        return hasAppliedDeliveryIn(player.getPersistentData().getCompound(KEY), deliveryUuid);
    }

    /** The marker list, oldest first. */
    public static List<UUID> appliedDeliveries(ServerPlayer player) {
        List<UUID> found = new ArrayList<>();
        if (player == null) return found;
        for (Tag tag : markerList(player.getPersistentData().getCompound(KEY))) {
            try {
                found.add(UUID.fromString(tag.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // A marker this build cannot read names no delivery it could match.
            }
        }
        return found;
    }

    /**
     * Records that the delivery's items are in the inventory. Idempotent; bounded to the
     * {@value #MAX_APPLIED_DELIVERY_MARKERS} newest. Only mutates the in-memory persistent data:
     * the caller forces the player-file write, in the same step as the insertion.
     */
    public static void markDeliveryApplied(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return;
        get(player);
        // One copy of the eviction rule, in markDeliveryAppliedIn. Two would drift, and the bound is
        // exactly the thing D-0102's permanent proof has to be trusted to backstop.
        markDeliveryAppliedIn(player.getPersistentData().getCompound(KEY), deliveryUuid);
    }

    /**
     * Forgets one marker, as if the player file had been written before it was appended. A
     * restart-simulation seam for tests; production code never removes a marker.
     */
    public static boolean removeAppliedDeliveryMarker(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return false;
        CompoundTag dataTag = player.getPersistentData().getCompound(KEY);
        ListTag markers = markerList(dataTag);
        String value = deliveryUuid.toString();
        boolean removed = markers.removeIf(tag -> value.equals(tag.getAsString()));
        if (removed) dataTag.put(APPLIED_DELIVERY_UUIDS, markers);
        return removed;
    }

    private static ListTag markerList(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(APPLIED_DELIVERY_UUIDS, Tag.TAG_LIST)) return new ListTag();
        return dataTag.getList(APPLIED_DELIVERY_UUIDS, Tag.TAG_STRING);
    }

    // --- permanent applied-delivery proof (D-0102) -----------------------------------------------
    //
    // These operate on the mod compound directly rather than on a ServerPlayer, so the property that
    // matters - a uuid whose bounded records have aged out is still refused - is provable in a unit
    // test with no server. The ServerPlayer facades below are thin on purpose.

    private static int[] proofArray(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(APPLIED_DELIVERY_PROOF, Tag.TAG_INT_ARRAY)) return new int[0];
        int[] found = dataTag.getIntArray(APPLIED_DELIVERY_PROOF);
        // A length that is not a whole number of uuids is unreadable rather than partially readable:
        // guessing which ints pair would invent a uuid nobody recorded.
        return found.length % PROOF_STRIDE == 0 ? found : new int[0];
    }

    /**
     * Is the permanent proof for this delivery in the player's file ON DISK?
     *
     * <p>Reads back {@code <level>/playerdata/<uuid>.dat} - the file vanilla's
     * {@code PlayerDataStorage.save} writes - and looks for the proof inside this mod's compound.
     * It lives here because the file layout and the compound key are this class's business.
     *
     * <p>It exists because a forced save RETURNING is not the same as a forced save having happened:
     * vanilla handles its own serialization, temp-file and replace failures and logs them, so the
     * caller cannot tell from the call. {@code QuestRewardDeliveryService} needs to know, because
     * nothing may report {@code applied} until the items and this proof are durable.
     *
     * <p>An absent or unreadable file answers NO. That is the safe direction: it keeps the delivery
     * owed rather than settling it on a write nobody can confirm.
     */
    public static boolean deliveryProofIsOnDisk(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || player.server == null || deliveryUuid == null) return false;
        try {
            Path file = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(player.getStringUUID() + ".dat");
            if (!Files.isRegularFile(file)) return false;
            CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return hasDeliveryProofIn(saved.getCompound("NeoForgeData").getCompound(KEY), deliveryUuid);
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /** Does this mod compound permanently record the delivery's items as inserted? */
    public static boolean hasDeliveryProofIn(CompoundTag dataTag, UUID deliveryUuid) {
        if (deliveryUuid == null) return false;
        int[] proof = proofArray(dataTag);
        int[] want = UUIDUtil.uuidToIntArray(deliveryUuid);
        for (int at = 0; at + PROOF_STRIDE <= proof.length; at += PROOF_STRIDE) {
            if (proof[at] == want[0] && proof[at + 1] == want[1]
                    && proof[at + 2] == want[2] && proof[at + 3] == want[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Permanently records that the delivery's items were inserted. Idempotent, and UNBOUNDED: there
     * is deliberately no cap, no expiry and no pruning here. Only mutates the compound; the caller
     * forces the player-file write in the same step as the insertion.
     */
    public static void recordDeliveryProofIn(CompoundTag dataTag, UUID deliveryUuid) {
        if (dataTag == null || deliveryUuid == null) return;
        if (hasDeliveryProofIn(dataTag, deliveryUuid)) return;
        quarantineUnreadableProof(dataTag);
        int[] existing = proofArray(dataTag);
        int[] want = UUIDUtil.uuidToIntArray(deliveryUuid);
        int[] grown = new int[existing.length + PROOF_STRIDE];
        System.arraycopy(existing, 0, grown, 0, existing.length);
        System.arraycopy(want, 0, grown, existing.length, PROOF_STRIDE);
        dataTag.put(APPLIED_DELIVERY_PROOF, new IntArrayTag(grown));
    }

    /** How many deliveries this compound permanently records. For tests and diagnostics. */
    public static int deliveryProofCount(CompoundTag dataTag) {
        return proofArray(dataTag).length / PROOF_STRIDE;
    }

    /** The bounded marker append, on a compound, so the eviction is testable without a server. */
    public static void markDeliveryAppliedIn(CompoundTag dataTag, UUID deliveryUuid) {
        if (dataTag == null || deliveryUuid == null) return;
        ListTag markers = markerList(dataTag);
        String value = deliveryUuid.toString();
        for (Tag tag : markers) {
            if (value.equals(tag.getAsString())) return;
        }
        markers.add(StringTag.valueOf(value));
        while (markers.size() > MAX_APPLIED_DELIVERY_MARKERS) markers.remove(0);
        dataTag.put(APPLIED_DELIVERY_UUIDS, markers);
    }

    /**
     * Moves a proof tag this build cannot read into the quarantine key, verbatim, so appending a new
     * uuid cannot overwrite it. Does nothing when the tag is absent or readable.
     */
    public static void quarantineUnreadableProof(CompoundTag dataTag) {
        if (dataTag == null || !dataTag.contains(APPLIED_DELIVERY_PROOF)) return;
        if (proofArray(dataTag).length > 0) return;                    // readable: nothing to quarantine
        // A COMPOUND of numbered entries, not one slot and not a list: a second unreadable proof must
        // not overwrite the first, and a ListTag cannot hold two different tag types, which is exactly
        // what "unreadable" may produce. The first version of this method removed the tag without
        // copying it whenever a quarantine already existed - it discarded the bytes it existed to keep.
        CompoundTag kept = dataTag.getCompound(APPLIED_DELIVERY_PROOF_QUARANTINE);
        kept.put(String.valueOf(kept.size()), dataTag.get(APPLIED_DELIVERY_PROOF).copy());
        dataTag.put(APPLIED_DELIVERY_PROOF_QUARANTINE, kept);
        dataTag.remove(APPLIED_DELIVERY_PROOF);
    }

    /** How many unreadable proof representations are being kept. For tests and diagnostics. */
    public static int quarantinedProofCount(CompoundTag dataTag) {
        return dataTag == null ? 0 : dataTag.getCompound(APPLIED_DELIVERY_PROOF_QUARANTINE).size();
    }

    /**
     * Gives every uuid in the BOUNDED marker list a permanent proof entry, once.
     *
     * <p>Player data written before D-0102 carries markers and no proof. Left alone, such a uuid is
     * refused only while its marker survives; 256 later deliveries evict it, and with its acknowledged
     * ledger row pruned too the uuid becomes insertable again - the very defect this record exists to
     * close. Migrating is safe in a way that seeding from Rails would NOT be: a marker is the shard's
     * OWN durable evidence that the items reached the player file, which is exactly what
     * {@code decide} already trusts it for. Rails' `acknowledged` is a different and weaker claim and
     * is never used here.
     *
     * <p>Idempotent and bounded by the marker cap, and it runs before a delivery is considered, so a
     * legacy marker is migrated while it is still present rather than after it has gone.
     */
    public static int migrateLegacyMarkersIn(CompoundTag dataTag) {
        if (dataTag == null) return 0;
        int migrated = 0;
        for (Tag tag : markerList(dataTag)) {
            try {
                UUID uuid = UUID.fromString(tag.getAsString());
                if (!hasDeliveryProofIn(dataTag, uuid)) {
                    recordDeliveryProofIn(dataTag, uuid);
                    migrated++;
                }
            } catch (IllegalArgumentException ignored) {
                // A marker this build cannot read names no delivery it could match, so it migrates
                // nothing. It is left in place rather than dropped.
            }
        }
        return migrated;
    }

    /** Whether this compound's BOUNDED marker list names the delivery. */
    public static boolean hasAppliedDeliveryIn(CompoundTag dataTag, UUID deliveryUuid) {
        if (deliveryUuid == null) return false;
        String value = deliveryUuid.toString();
        for (Tag tag : markerList(dataTag)) {
            if (value.equals(tag.getAsString())) return true;
        }
        return false;
    }

    /** Whether this player's persistent data PERMANENTLY records the delivery as applied. */
    public static boolean hasDeliveryProof(ServerPlayer player, UUID deliveryUuid) {
        if (player == null) return false;
        return hasDeliveryProofIn(player.getPersistentData().getCompound(KEY), deliveryUuid);
    }

    /**
     * The player-data half of APPLYING a delivery: the bounded marker and the permanent proof, both
     * written together. This is the seam the delivery service calls in the same in-memory step as the
     * item insertion, and the seam the unit tests drive, so a test cannot pass while the service
     * records only one of the two.
     */
    public static void recordDeliveryAppliedIn(CompoundTag dataTag, UUID deliveryUuid) {
        markDeliveryAppliedIn(dataTag, deliveryUuid);
        recordDeliveryProofIn(dataTag, deliveryUuid);
    }

    /** As above, for a live player. Call before the forced player-file write. */
    public static void recordDeliveryApplied(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return;
        recordDeliveryAppliedIn(modDataOf(player), deliveryUuid);
    }

    /**
     * The mod compound for this player, for the callers that decide about a delivery. Exposed so the
     * delivery service can read BOTH durable records through one seam, which is what stops a caller
     * silently passing "no proof".
     */
    public static CompoundTag modDataOf(ServerPlayer player) {
        if (player == null) return new CompoundTag();
        get(player);
        return player.getPersistentData().getCompound(KEY);
    }

    /**
     * Permanently records the delivery as applied. Call in the SAME server-thread step as the item
     * insertion, before the forced player-file write, so the items and the proof persist together.
     */
    public static void recordDeliveryProof(ServerPlayer player, UUID deliveryUuid) {
        if (player == null || deliveryUuid == null) return;
        get(player);
        recordDeliveryProofIn(player.getPersistentData().getCompound(KEY), deliveryUuid);
    }
}
