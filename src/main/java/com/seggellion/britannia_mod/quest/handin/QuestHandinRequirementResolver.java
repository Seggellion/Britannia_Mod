package com.seggellion.britannia_mod.quest.handin;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns Rails' published requirements into the concrete items this server will take (protocol
 * section 1.5.2).
 *
 * <h2>Why this exists at all</h2>
 * Rails deliberately cannot name stage five's produce. The crop-to-produce mapping lives in
 * {@link CropRegistry}, and Rails carries the <i>question</i> -- resolver, flag, and the flag's
 * value pinned at prepare time -- so that "the crop you grew" is answered by the side that knows.
 * This class is that answer, and it is the only place it is computed.
 *
 * <h2>Everything it refuses</h2>
 * A resolver it does not implement, a crop id the registry has never heard of, a crop whose harvest
 * item cannot be resolved, an item id that names nothing registered, and a registry that is not yet
 * ready. Each is a refusal of the whole demand, before any inventory is touched -- because the only
 * alternative is to take what was understood and report a proof Rails answers
 * {@code evidence_rejected} to, which leaves the player short with the quest unfinished.
 *
 * <p>What it never does is guess. It does not infer the crop from what the player is carrying, does
 * not substitute a seed for its produce, and has no default crop. Those three are the mistakes the
 * resolver shape exists to prevent, and none of them is reachable from here: the crop id comes from
 * {@code flag_value} and nowhere else, and the item comes from
 * {@link CropDefinition#harvestItem()} and nowhere else.
 */
public final class QuestHandinRequirementResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestHandinRequirementResolver() {}

    /**
     * Resolves every requirement, or refuses the demand.
     *
     * <p>Order is preserved and {@code requirement_index} is carried through, because that index is
     * how Rails joins the proof back to the requirement it answers. Two requirements naming the same
     * item stay two entries: aggregating them would produce one proof entry for two requirements,
     * which Rails refuses as {@code evidence_mismatch}.
     */
    public static QuestHandinResolution resolve(QuestItemHandinProtocol.Demand demand) {
        if (demand == null) return new QuestHandinResolution.Refused(QuestHandinResolution.MALFORMED_REQUIREMENT);
        List<QuestHandinRemoval> plan = new ArrayList<>(demand.requirements().size());
        for (QuestHandinRequirement requirement : demand.requirements()) {
            QuestHandinResolution one = resolveOne(requirement);
            if (one instanceof QuestHandinResolution.Refused refused) {
                LOGGER.warn("event=quest_handin_requirement_refused handin_uuid={} index={} reason={}",
                        demand.handinUuid(), requirement.index(), refused.reason());
                return refused;
            }
            plan.addAll(((QuestHandinResolution.Resolved) one).plan());
        }
        return new QuestHandinResolution.Resolved(plan);
    }

    private static QuestHandinResolution resolveOne(QuestHandinRequirement requirement) {
        return switch (requirement) {
            case QuestHandinRequirement.Literal literal -> {
                Optional<ResourceLocation> id = registered(literal.itemId());
                yield id.isEmpty()
                        ? new QuestHandinResolution.Refused(QuestHandinResolution.UNKNOWN_ITEM)
                        : new QuestHandinResolution.Resolved(List.of(QuestHandinRemoval.literal(
                                literal.index(), id.get().toString(), literal.count())));
            }
            case QuestHandinRequirement.Resolver resolver -> resolveDynamic(resolver);
        };
    }

    private static QuestHandinResolution resolveDynamic(QuestHandinRequirement.Resolver requirement) {
        if (!QuestHandinRequirement.RESOLVER_AWARDED_CROP_HARVEST_ITEM.equals(requirement.resolver())) {
            // A resolver a later contract version adds is not one this build can answer, and
            // answering it wrongly would take the wrong item.
            return new QuestHandinResolution.Refused(QuestHandinResolution.UNKNOWN_RESOLVER);
        }
        Optional<CropDefinition> crop;
        try {
            crop = CropRegistry.byId(requirement.flagValue());
        } catch (RuntimeException notReady) {
            // CropRegistry bootstraps lazily off deferred item registration; a call before that has
            // settled throws rather than publishing a half-built table. Refusing is right: the next
            // attempt will find it ready, and nothing has been taken.
            LOGGER.warn("event=quest_handin_crop_registry_unavailable flag_value={}",
                    requirement.flagValue(), notReady);
            return new QuestHandinResolution.Refused(QuestHandinResolution.REGISTRY_UNAVAILABLE);
        }
        if (crop.isEmpty()) {
            return new QuestHandinResolution.Refused(QuestHandinResolution.UNKNOWN_CROP);
        }
        Item harvest;
        try {
            harvest = crop.get().harvestItem().get();
        } catch (RuntimeException unresolved) {
            return new QuestHandinResolution.Refused(QuestHandinResolution.CROP_HAS_NO_HARVEST_ITEM);
        }
        if (harvest == null || harvest == Items.AIR) {
            return new QuestHandinResolution.Refused(QuestHandinResolution.CROP_HAS_NO_HARVEST_ITEM);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(harvest);
        if (id == null || Items.AIR.equals(BuiltInRegistries.ITEM.get(id))) {
            return new QuestHandinResolution.Refused(QuestHandinResolution.CROP_HAS_NO_HARVEST_ITEM);
        }
        return new QuestHandinResolution.Resolved(List.of(QuestHandinRemoval.resolved(
                requirement.index(), id.toString(), requirement.count(),
                requirement.resolver(), requirement.flagValue())));
    }

    /**
     * The registry id of a registered, non-air item, or empty.
     *
     * <p>{@code BuiltInRegistries.ITEM.get} answers {@code minecraft:air} for anything it does not
     * know, so "is it air?" is the only way to tell an unregistered id from a real one -- and an
     * item nothing can be removed of is a requirement that could never be satisfied.
     */
    public static Optional<ResourceLocation> registered(String itemId) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId);
        if (parsed == null) return Optional.empty();
        Item item = BuiltInRegistries.ITEM.get(parsed);
        if (item == null || item == Items.AIR) {
            // minecraft:air is a real registry entry, so an authored requirement naming it would
            // resolve. It is refused instead: nothing is ever carried, so it can never be handed in.
            return Optional.empty();
        }
        return Optional.of(parsed);
    }

    /** The item a resolved requirement names, for the removal pass. */
    public static Optional<Item> item(QuestHandinRemoval removal) {
        ResourceLocation parsed = ResourceLocation.tryParse(removal.itemId());
        if (parsed == null) return Optional.empty();
        Item item = BuiltInRegistries.ITEM.get(parsed);
        return item == null || item == Items.AIR ? Optional.empty() : Optional.of(item);
    }
}
