package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Plans all validation before applying the two permitted stack mutations. */
public final class DyeTubLoadingService {
    private DyeTubLoadingService() {
    }

    public static DyeTubLoadPlan plan(
            ItemStack tubStack,
            Item registeredTubItem,
            ItemStack offHandStack,
            Function<Item, Optional<PigmentId>> pigmentLookup,
            RegistrySnapshot snapshot,
            boolean registryAvailable,
            DataComponentType<DyeTubState> componentType) {
        Objects.requireNonNull(tubStack, "tubStack");
        Objects.requireNonNull(registeredTubItem, "registeredTubItem");
        Objects.requireNonNull(offHandStack, "offHandStack");
        Objects.requireNonNull(pigmentLookup, "pigmentLookup");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(componentType, "componentType");

        DyeTubState currentState = DyeTubStateAccess.read(tubStack, componentType);
        if (!tubStack.is(registeredTubItem)) {
            return DyeTubLoadPlan.failure(DyeTubLoadResult.INVALID_TUB, currentState);
        }
        if (offHandStack.isEmpty()) {
            return DyeTubLoadPlan.failure(DyeTubLoadResult.EMPTY_OFF_HAND, currentState);
        }

        Optional<PigmentId> pigmentId = pigmentLookup.apply(offHandStack.getItem());
        if (pigmentId.isEmpty()) {
            return DyeTubLoadPlan.failure(DyeTubLoadResult.INVALID_OFF_HAND_ITEM, currentState);
        }
        if (!registryAvailable) {
            return DyeTubLoadPlan.failure(DyeTubLoadResult.REGISTRY_UNAVAILABLE, currentState);
        }

        PigmentId selectedPigment = pigmentId.orElseThrow();
        if (!snapshot.pigments().contains(selectedPigment)) {
            boolean disabled = snapshot.pigments().disabledEntries().stream()
                    .anyMatch(entry -> entry.id().equals(selectedPigment));
            return DyeTubLoadPlan.failure(
                    disabled ? DyeTubLoadResult.PIGMENT_DISABLED : DyeTubLoadResult.PIGMENT_DEFINITION_MISSING,
                    currentState);
        }
        if (currentState.pigmentId().filter(selectedPigment::equals).isPresent()) {
            return DyeTubLoadPlan.failure(DyeTubLoadResult.ALREADY_CONTAINS, currentState);
        }

        DyeTubState replacement = DyeTubState.loadedUnlimited(selectedPigment);
        DyeTubLoadResult result = currentState.pigmentId().isEmpty()
                ? DyeTubLoadResult.LOADED
                : DyeTubLoadResult.REPLACED;
        return DyeTubLoadPlan.success(result, currentState, replacement, offHandStack.getItem(), selectedPigment);
    }

    public static DyeTubLoadResult apply(
            DyeTubLoadPlan plan,
            ItemStack tubStack,
            ItemStack offHandStack,
            boolean creative,
            DataComponentType<DyeTubState> componentType) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(tubStack, "tubStack");
        Objects.requireNonNull(offHandStack, "offHandStack");
        Objects.requireNonNull(componentType, "componentType");

        if (!plan.result().loadedSuccessfully()) {
            return plan.result();
        }
        if (!DyeTubStateAccess.read(tubStack, componentType).equals(plan.originalState())
                || offHandStack.isEmpty()
                || offHandStack.getItem() != plan.pigmentItem().orElseThrow()) {
            return DyeTubLoadResult.STATE_COMPONENT_FAILURE;
        }

        DyeTubStateAccess.write(tubStack, componentType, plan.replacementState().orElseThrow());
        if (!creative) {
            offHandStack.shrink(1);
        }
        return plan.result();
    }
}
