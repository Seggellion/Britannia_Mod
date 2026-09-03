package com.seggellion.britannia_mod.blessed;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that decides whether an {@link ItemStack} is a lifecycle-aware blessed item, and
 * extracts its identity -- Starfarer M7.
 *
 * <p>Several event handlers (destruction, rescue, protection) need this same answer, and the one
 * thing they must not each do is read the tags by hand. Hand-rolled readers drift: one checks
 * {@code blessed} and forgets {@code instance_uuid}, another parses a uuid without catching
 * {@link IllegalArgumentException}, a third treats a missing field as a default. Every one of
 * those divergences becomes a wrong answer about a permanent, one-off commemorative item -- and
 * the wrong answers are not symmetric. Reporting a destruction that did not happen burns an
 * entitlement no player can get back; failing to recognise a blessed item at all loses one.
 *
 * <h2>The stamp this reads</h2>
 * {@code BlessedItemInventorySync#stamp} writes exactly four fields onto
 * {@link DataComponents#CUSTOM_DATA}: {@code blessed} (boolean true), {@code owner} (a player
 * uuid string), {@code deed_id} (Rails' entitlement id) and {@code instance_uuid} (Rails'
 * per-shard materialization id, added in M6). This class is the only reader of that shape.
 *
 * <h2>Two different questions, deliberately not one</h2>
 * {@link #isBlessed(ItemStack)} answers "is this a blessed item at all", and
 * {@link #of(ItemStack)} answers "is this a blessed item this build can speak the lifecycle
 * protocol about". They differ for exactly one real population: <b>legacy blessed items</b>
 * stamped before M6, which carry {@code blessed}/{@code owner}/{@code deed_id} but no
 * {@code instance_uuid}. Those are real, valuable items and rescue/protection must still apply
 * to them, so {@code isBlessed} is true; but they have no materialization identity, so there is
 * nothing Rails could key a destruction report to, and {@code of} is empty. A handler that
 * reported a destruction for one of those would have to invent an identity, and an invented
 * identity is a corrupt report about somebody's permanent property.
 *
 * <h2>Fail closed, everywhere</h2>
 * Every defect -- an empty stack, absent {@link DataComponents#CUSTOM_DATA}, {@code blessed}
 * absent or false or of an unexpected tag type, a blank {@code deed_id}, a malformed uuid,
 * outright corrupt NBT -- resolves to "not a lifecycle-aware blessed item" rather than a guess
 * or a thrown exception. These methods are called from event handlers on the server tick path,
 * where a thrown exception is not a caught error but a crashed tick, so nothing here throws:
 * a corrupt tag produces one warn line and an empty {@link Optional}.
 */
public record BlessedItemLifecycleMetadata(UUID instanceUuid, String deedId, UUID ownerUuid) {
    /** {@code true} on any blessed item, legacy or lifecycle-aware. */
    private static final String KEY_BLESSED = "blessed";
    /** The owning player's uuid, as a string. */
    private static final String KEY_OWNER = "owner";
    /** Rails' {@code BlessedItem.uuid} -- the permanent entitlement, NOT guaranteed uuid-shaped. */
    private static final String KEY_DEED_ID = "deed_id";
    /** Rails' per-shard materialization identity; absent on pre-M6 legacy items. */
    private static final String KEY_INSTANCE_UUID = "instance_uuid";

    private static final Logger LOGGER = LogUtils.getLogger();

    public BlessedItemLifecycleMetadata {
        Objects.requireNonNull(instanceUuid, "instanceUuid");
        Objects.requireNonNull(deedId, "deedId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        if (deedId.isBlank()) {
            throw new IllegalArgumentException("deedId must not be blank");
        }
    }

    /**
     * The lifecycle identity of this stack, or empty if it does not have one.
     *
     * <p>Empty covers four genuinely different situations, and the caller is right to treat them
     * identically -- in every one of them there is no materialization this build may speak about:
     * <ul>
     *   <li>not a blessed item at all (empty stack, no custom data, {@code blessed} absent/false);</li>
     *   <li>a legacy pre-M6 blessed item, which has no {@code instance_uuid} to report against
     *       (note {@link #isBlessed} still answers true for these);</li>
     *   <li>a malformed stamp -- an unparseable {@code instance_uuid} or {@code owner}, or a blank
     *       {@code deed_id} -- which is logged once, at warn, carrying the offending values;</li>
     *   <li>NBT so corrupt that reading it threw, which is caught here rather than propagated
     *       into whatever event handler asked the question.</li>
     * </ul>
     *
     * <p>{@code deed_id} is accepted as any non-blank string and is deliberately NOT parsed as a
     * uuid: legacy Rails entitlement ids are not guaranteed uuid-shaped, and this class has no
     * business rejecting a real entitlement over a format it was never promised. {@code owner}
     * and {@code instance_uuid} have no such history -- both are structurally uuids -- so a
     * malformed one there is corruption and is treated as such.
     */
    public static Optional<BlessedItemLifecycleMetadata> of(ItemStack stack) {
        try {
            CompoundTag tag = blessedCustomData(stack);
            if (tag == null) return Optional.empty();

            String rawInstanceUuid = tag.getString(KEY_INSTANCE_UUID);
            if (rawInstanceUuid.isBlank()) {
                // A legacy (pre-M6) blessed item. Not an error, and deliberately not logged at
                // warn: these are expected to exist in the wild for as long as any of them do.
                return Optional.empty();
            }

            String rawDeedId = tag.getString(KEY_DEED_ID);
            String rawOwnerUuid = tag.getString(KEY_OWNER);
            UUID instanceUuid = parseUuidOrNull(rawInstanceUuid);
            UUID ownerUuid = parseUuidOrNull(rawOwnerUuid);

            if (instanceUuid == null || ownerUuid == null || rawDeedId.isBlank()) {
                // Exactly one warn per call, carrying every offending value, so an operator can
                // see the whole malformed stamp at once instead of one field per line.
                LOGGER.warn("Ignoring a malformed blessed lifecycle stamp: instance_uuid='{}' "
                        + "owner='{}' deed_id='{}'", rawInstanceUuid, rawOwnerUuid, rawDeedId);
                return Optional.empty();
            }
            return Optional.of(new BlessedItemLifecycleMetadata(instanceUuid, rawDeedId, ownerUuid));
        } catch (RuntimeException corrupt) {
            LOGGER.warn("Ignoring an unreadable blessed lifecycle stamp", corrupt);
            return Optional.empty();
        }
    }

    /**
     * Whether this stack is a blessed item at all, lifecycle fields or not.
     *
     * <p>True for a legacy pre-M6 blessed item, for which {@link #of} is empty. That asymmetry is
     * the point: protection and rescue must cover every blessed item ever issued, whereas the
     * reporting protocol may only cover the ones with a materialization identity to report.
     */
    public static boolean isBlessed(ItemStack stack) {
        try {
            return blessedCustomData(stack) != null;
        } catch (RuntimeException corrupt) {
            LOGGER.warn("Ignoring an unreadable blessed stamp", corrupt);
            return false;
        }
    }

    /**
     * The stack's custom data if and only if it carries {@code blessed=true}, else {@code null}.
     *
     * <p>{@link CompoundTag#getBoolean} is total: a {@code blessed} that is absent, or present as
     * a string or a list rather than a number, reads as {@code false} rather than throwing, which
     * is exactly the fail-closed answer wanted -- an ordinary item, and an item whose tag has
     * been tampered into an unexpected shape, are both "not blessed".
     */
    private static CompoundTag blessedCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        return tag.getBoolean(KEY_BLESSED) ? tag : null;
    }

    private static UUID parseUuidOrNull(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
