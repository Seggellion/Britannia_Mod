package com.seggellion.britannia_mod.farming;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/**
 * Pure viewer-specific presentation policy. It derives text from synchronized skill state and
 * authoritative definitions and never writes to the supplied stack.
 */
public final class FarmingPlantingItemPresentation {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FarmingPlantingItemPresentation() {
    }

    public enum Surface {
        ITEM_NAME,
        TOOLTIP,
        NARRATION,
        INVENTORY,
        HOTBAR,
        OFF_HAND,
        CONTAINER,
        DROPPED_LABEL,
        PICKUP_MESSAGE,
        CHAT_LINK,
        CREATIVE_INVENTORY,
        CREATIVE_SEARCH,
        RECIPE_BOOK,
        COMPATIBILITY_UI
    }

    public enum IdentityDisclosurePolicy {
        VIEWER_SKILL,
        ADMINISTRATIVE_BYPASS,
        FAIL_CLOSED,
        NATIVE_VANILLA_OUTSIDE_SCOPE,
        NOT_A_PLANTING_ITEM
    }

    public record ViewerState(
            SkillManager.SkillDataState skillDataState,
            float farmingSkill,
            boolean identificationBypass,
            long revision
    ) {
        public ViewerState {
            Objects.requireNonNull(skillDataState, "Farming presentation state is required");
        }
    }

    /** Contains approved display data only; unidentified results never carry species identity. */
    public record PresentationResult(
            boolean applicable,
            boolean identified,
            Component displayName,
            FarmingPlantingMaterialCategory genericCategory,
            boolean minimumRequirementVisible,
            boolean currentSkillVisible,
            List<Component> tooltipLines,
            Component narrationText,
            IdentityDisclosurePolicy identityDisclosurePolicy
    ) {
        public PresentationResult {
            Objects.requireNonNull(displayName, "Presentation display name is required");
            Objects.requireNonNull(genericCategory, "Presentation category is required");
            tooltipLines = List.copyOf(Objects.requireNonNull(tooltipLines, "Tooltip lines are required"));
            Objects.requireNonNull(narrationText, "Narration text is required");
            Objects.requireNonNull(identityDisclosurePolicy, "Disclosure policy is required");
        }
    }

    public static PresentationResult resolve(
            ViewerState viewer,
            ItemStack stack,
            Component existingDisplayName,
            Surface surface
    ) {
        Objects.requireNonNull(viewer, "Viewer state is required");
        Objects.requireNonNull(stack, "Planting stack is required");
        Objects.requireNonNull(existingDisplayName, "Existing display name is required");
        Objects.requireNonNull(surface, "Presentation surface is required");

        FarmingSkillRequirementResolver.ResolvedRequirement resolved;
        try {
            resolved = FarmingSkillRequirementResolver.resolve(stack.getItem()).orElse(null);
        } catch (RuntimeException unresolvedAmbiguity) {
            // Failing closed is right for a name lookup, but this used to swallow the cause
            // outright. Species resolution touches the crop and flower registries, so a genuine
            // registry failure arrived here as silence and only surfaced later, somewhere else,
            // as a definition-count mismatch.
            LOGGER.error("Could not resolve the farming species for {} while rendering its {};"
                            + " falling back to an unidentified name",
                    stack.getItem(), surface, unresolvedAmbiguity);
            Component generic = Component.translatable(
                    FarmingPlantingMaterialCategory.PLANTING_MATERIAL.unidentifiedNameKey()
            );
            return unidentified(generic, FarmingPlantingMaterialCategory.PLANTING_MATERIAL,
                    IdentityDisclosurePolicy.FAIL_CLOSED);
        }

        if (resolved == null) {
            return passThrough(existingDisplayName, FarmingPlantingMaterialCategory.NOT_APPLICABLE,
                    IdentityDisclosurePolicy.NOT_A_PLANTING_ITEM);
        }

        return resolveResolved(viewer, stack, existingDisplayName, surface, resolved);
    }

    /** Testable policy entry point for an already-authoritatively resolved planting item. */
    public static PresentationResult resolveResolved(
            ViewerState viewer,
            ItemStack stack,
            Component existingDisplayName,
            Surface surface,
            FarmingSkillRequirementResolver.ResolvedRequirement resolved
    ) {
        Objects.requireNonNull(viewer, "Viewer state is required");
        Objects.requireNonNull(stack, "Planting stack is required");
        Objects.requireNonNull(existingDisplayName, "Existing display name is required");
        Objects.requireNonNull(surface, "Presentation surface is required");
        Objects.requireNonNull(resolved, "Resolved Farming requirement is required");

        FarmingPlantingMaterialCategory category = FarmingPlantingMaterialCategory.resolve(
                resolved, stack.getItem()
        );
        if (!category.hasUnidentifiedPresentation()) {
            return passThrough(existingDisplayName, category,
                    IdentityDisclosurePolicy.NATIVE_VANILLA_OUTSIDE_SCOPE);
        }

        if (viewer.identificationBypass()) {
            return identified(existingDisplayName, category, IdentityDisclosurePolicy.ADMINISTRATIVE_BYPASS);
        }
        if (viewer.skillDataState() == SkillManager.SkillDataState.AVAILABLE
                && viewer.farmingSkill() >= resolved.minimumFarmingSkill()) {
            return identified(existingDisplayName, category, IdentityDisclosurePolicy.VIEWER_SKILL);
        }

        Component generic = Component.translatable(category.unidentifiedNameKey());
        IdentityDisclosurePolicy policy = viewer.skillDataState() == SkillManager.SkillDataState.AVAILABLE
                ? IdentityDisclosurePolicy.VIEWER_SKILL
                : IdentityDisclosurePolicy.FAIL_CLOSED;
        return unidentified(generic, category, policy);
    }

    private static PresentationResult identified(
            Component existingDisplayName,
            FarmingPlantingMaterialCategory category,
            IdentityDisclosurePolicy policy
    ) {
        return new PresentationResult(true, true, existingDisplayName, category,
                false, false, List.of(), existingDisplayName, policy);
    }

    private static PresentationResult unidentified(
            Component generic,
            FarmingPlantingMaterialCategory category,
            IdentityDisclosurePolicy policy
    ) {
        return new PresentationResult(true, false, generic, category,
                false, false, List.of(), generic, policy);
    }

    private static PresentationResult passThrough(
            Component existingDisplayName,
            FarmingPlantingMaterialCategory category,
            IdentityDisclosurePolicy policy
    ) {
        return new PresentationResult(false, true, existingDisplayName, category,
                false, false, List.of(), existingDisplayName, policy);
    }
}
