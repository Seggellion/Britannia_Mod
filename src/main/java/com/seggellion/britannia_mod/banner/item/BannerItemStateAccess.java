package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionEntry;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionRegistry;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.service.DyeResolutionOutcome;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Focused, non-mutating validation/planning and explicit targeted mutation for banner stacks. */
public final class BannerItemStateAccess {
    private final Item bannerItem;
    private final DataComponentType<BannerInstanceState> componentType;
    private final DyeResolver dyeResolver;

    public BannerItemStateAccess(
            Item bannerItem,
            DataComponentType<BannerInstanceState> componentType,
            DyeResolver dyeResolver) {
        this.bannerItem = Objects.requireNonNull(bannerItem, "bannerItem");
        this.componentType = Objects.requireNonNull(componentType, "componentType");
        this.dyeResolver = Objects.requireNonNull(dyeResolver, "dyeResolver");
    }

    public Optional<BannerInstanceState> read(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return Optional.ofNullable(stack.get(componentType));
    }

    public BannerStateValidation validate(ItemStack stack, RegistrySnapshot snapshot, boolean registryAvailable) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getItem() != bannerItem) {
            return invalid(Optional.empty(), BannerStateIssueKind.INVALID_ITEM, itemId(stack));
        }
        Optional<BannerInstanceState> state = read(stack);
        if (state.isEmpty()) {
            return new BannerStateValidation(BannerStateStatus.UNCONFIGURED, Optional.empty(),
                    List.of(new BannerStateIssue(BannerStateIssueKind.COMPONENT_MISSING, "banner_instance_state")));
        }
        return validateState(state.orElseThrow(), snapshot, registryAvailable);
    }

    public BannerStateValidation validateState(
            BannerInstanceState state, RegistrySnapshot snapshot, boolean registryAvailable) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return new BannerStateValidation(BannerStateStatus.REGISTRY_UNAVAILABLE, Optional.of(state),
                    List.of(new BannerStateIssue(BannerStateIssueKind.REGISTRY_UNAVAILABLE, "registry_snapshot")));
        }

        List<BannerStateIssue> issues = new ArrayList<>();
        BannerDefinition definition = snapshot.banners().find(state.bannerDefinitionId()).orElse(null);
        if (definition == null) {
            issues.add(missingOrDisabled(snapshot.banners(), state.bannerDefinitionId(),
                    BannerStateIssueKind.DEFINITION_MISSING, BannerStateIssueKind.DEFINITION_DISABLED));
        }
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(state.materialId()).orElse(null);
        if (material == null) {
            issues.add(missingOrDisabled(snapshot.fabricMaterials(), state.materialId(),
                    BannerStateIssueKind.MATERIAL_MISSING, BannerStateIssueKind.MATERIAL_DISABLED));
        }
        MaterialPalette palette = material == null ? null
                : snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        if (material != null && palette == null) {
            issues.add(new BannerStateIssue(BannerStateIssueKind.PALETTE_MISSING, material.paletteId().toString()));
        }
        if (palette != null && palette.entries().stream().noneMatch(entry -> entry.id().equals(state.resolvedColourId()))) {
            issues.add(new BannerStateIssue(BannerStateIssueKind.RESOLVED_COLOUR_MISSING,
                    state.resolvedColourId().toString()));
            issues.add(new BannerStateIssue(BannerStateIssueKind.REPAIRABLE_MISSING_COLOUR,
                    state.resolvedColourId().toString()));
        }
        state.sourcePigmentId().ifPresent(pigmentId -> {
            if (!snapshot.pigments().contains(pigmentId)) {
                issues.add(missingOrDisabled(snapshot.pigments(), pigmentId,
                        BannerStateIssueKind.SOURCE_PIGMENT_MISSING, BannerStateIssueKind.SOURCE_PIGMENT_DISABLED));
            }
        });
        if (!snapshot.mounts().contains(state.mountId())) {
            issues.add(missingOrDisabled(snapshot.mounts(), state.mountId(),
                    BannerStateIssueKind.MOUNT_MISSING, BannerStateIssueKind.MOUNT_DISABLED));
        } else if (definition != null && !definition.supportedMounts().contains(state.mountId())) {
            issues.add(new BannerStateIssue(BannerStateIssueKind.UNSUPPORTED_MOUNT, state.mountId().toString()));
        }

        boolean severe = issues.stream().anyMatch(issue -> switch (issue.kind()) {
            case DEFINITION_MISSING, DEFINITION_DISABLED, MATERIAL_MISSING, MATERIAL_DISABLED,
                    PALETTE_MISSING, MOUNT_MISSING, MOUNT_DISABLED, UNSUPPORTED_MOUNT -> true;
            default -> false;
        });
        boolean missingColour = issues.stream().anyMatch(issue ->
                issue.kind() == BannerStateIssueKind.RESOLVED_COLOUR_MISSING);
        BannerStateStatus status = severe ? BannerStateStatus.INVALID
                : missingColour ? BannerStateStatus.REPAIRABLE
                : issues.isEmpty() ? BannerStateStatus.VALID : BannerStateStatus.VALID_WITH_DIAGNOSTICS;
        return new BannerStateValidation(status, Optional.of(state), issues);
    }

    public boolean writeCompleteValid(
            ItemStack stack, BannerInstanceState state, RegistrySnapshot snapshot, boolean registryAvailable) {
        if (stack.getItem() != bannerItem
                || validateState(state, snapshot, registryAvailable).status() != BannerStateStatus.VALID) {
            return false;
        }
        stack.set(componentType, state);
        return true;
    }

    /** Explicit development/test-only clearing boundary. */
    public void clearForDevelopment(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        stack.remove(componentType);
    }

    public BannerColourUpdatePlan planColourUpdate(
            ItemStack stack,
            ResolvedColourId colourId,
            Optional<PigmentId> sourcePigmentId,
            RegistrySnapshot snapshot,
            boolean registryAvailable) {
        Objects.requireNonNull(colourId, "colourId");
        Objects.requireNonNull(sourcePigmentId, "sourcePigmentId");
        BannerStateValidation validation = validate(stack, snapshot, registryAvailable);
        if (!validation.validForColourUpdate()) {
            BannerStateIssue issue = validation.issues().isEmpty()
                    ? new BannerStateIssue(BannerStateIssueKind.COMPONENT_DECODE_FAILURE, "banner_instance_state")
                    : validation.issues().getFirst();
            return failedUpdate(issue);
        }
        BannerInstanceState original = validation.storedState().orElseThrow();
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(original.materialId()).orElseThrow();
        MaterialPalette palette = snapshot.materialPalettes().find(material.paletteId()).orElseThrow();
        if (palette.entries().stream().noneMatch(entry -> entry.id().equals(colourId))) {
            return failedUpdate(new BannerStateIssue(BannerStateIssueKind.RESOLVED_COLOUR_MISSING,
                    colourId.toString()));
        }
        if (sourcePigmentId.isPresent() && !snapshot.pigments().contains(sourcePigmentId.orElseThrow())) {
            PigmentId id = sourcePigmentId.orElseThrow();
            return failedUpdate(missingOrDisabled(snapshot.pigments(), id,
                    BannerStateIssueKind.SOURCE_PIGMENT_MISSING, BannerStateIssueKind.SOURCE_PIGMENT_DISABLED));
        }
        BannerInstanceState replacement = new BannerInstanceState(
                original.schemaVersion(), original.bannerDefinitionId(), original.materialId(), colourId,
                sourcePigmentId, original.mountId());
        return new BannerColourUpdatePlan(Optional.of(original), Optional.of(replacement), Optional.empty());
    }

    public boolean applyColourUpdate(ItemStack stack, BannerColourUpdatePlan plan) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(plan, "plan");
        if (!plan.successful() || stack.getItem() != bannerItem
                || !read(stack).equals(plan.expectedState())) {
            return false;
        }
        stack.set(componentType, plan.replacementState().orElseThrow());
        return true;
    }

    public BannerRepairPlan planRepair(
            ItemStack stack, RegistrySnapshot snapshot, boolean registryAvailable) {
        BannerStateValidation validation = validate(stack, snapshot, registryAvailable);
        if (validation.status() != BannerStateStatus.REPAIRABLE) {
            BannerStateIssue issue = validation.issues().isEmpty()
                    ? new BannerStateIssue(BannerStateIssueKind.RESOLVED_COLOUR_MISSING, "not_repairable")
                    : validation.issues().getFirst();
            return failedRepair(issue);
        }
        BannerInstanceState original = validation.storedState().orElseThrow();
        Optional<ResolvedColourId> replacementColour = Optional.empty();
        Optional<BannerRepairReason> reason = Optional.empty();
        if (original.sourcePigmentId().isPresent()
                && snapshot.pigments().contains(original.sourcePigmentId().orElseThrow())) {
            DyeResolutionOutcome resolved = dyeResolver.resolve(
                    original.sourcePigmentId().orElseThrow(), original.materialId(), snapshot);
            if (resolved.successful()) {
                replacementColour = Optional.of(resolved.result().orElseThrow().resolvedColourId());
                reason = Optional.of(BannerRepairReason.SOURCE_PIGMENT_RERESOLVED);
            }
        }
        if (replacementColour.isEmpty()) {
            DyeResolutionOutcome natural = dyeResolver.resolveNatural(original.materialId(), snapshot);
            if (!natural.successful()) {
                return failedRepair(new BannerStateIssue(BannerStateIssueKind.RESOLVED_COLOUR_MISSING,
                        original.resolvedColourId().toString()));
            }
            replacementColour = Optional.of(natural.result().orElseThrow().resolvedColourId());
            reason = Optional.of(BannerRepairReason.MATERIAL_NATURAL_FALLBACK);
        }
        BannerInstanceState replacement = new BannerInstanceState(
                original.schemaVersion(), original.bannerDefinitionId(), original.materialId(),
                replacementColour.orElseThrow(), original.sourcePigmentId(), original.mountId());
        boolean retained = reason.orElseThrow() == BannerRepairReason.MATERIAL_NATURAL_FALLBACK
                && original.sourcePigmentId().isPresent();
        return new BannerRepairPlan(Optional.of(original), Optional.of(replacement), reason, retained, Optional.empty());
    }

    public boolean applyRepair(ItemStack stack, BannerRepairPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!plan.successful() || stack.getItem() != bannerItem || !read(stack).equals(plan.expectedState())) {
            return false;
        }
        stack.set(componentType, plan.replacementState().orElseThrow());
        return true;
    }

    private static BannerColourUpdatePlan failedUpdate(BannerStateIssue issue) {
        return new BannerColourUpdatePlan(Optional.empty(), Optional.empty(), Optional.of(issue));
    }

    private static BannerRepairPlan failedRepair(BannerStateIssue issue) {
        return new BannerRepairPlan(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(issue));
    }

    private static BannerStateValidation invalid(
            Optional<BannerInstanceState> state, BannerStateIssueKind kind, String id) {
        return new BannerStateValidation(BannerStateStatus.INVALID, state,
                List.of(new BannerStateIssue(kind, id)));
    }

    private static <I, T> BannerStateIssue missingOrDisabled(
            DefinitionRegistry<I, T> registry,
            I id,
            BannerStateIssueKind missing,
            BannerStateIssueKind disabled) {
        boolean isDisabled = registry.disabledEntries().stream()
                .map(DefinitionEntry::id).anyMatch(id::equals);
        return new BannerStateIssue(isDisabled ? disabled : missing, id.toString());
    }

    private static String itemId(ItemStack stack) {
        return stack.getItem().toString();
    }
}
