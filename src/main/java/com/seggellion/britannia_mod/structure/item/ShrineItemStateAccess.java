package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Non-mutating configured-item read and server placement validation. */
public final class ShrineItemStateAccess {
    public enum Status {
        VALID,
        INVALID_ITEM,
        UNSUPPORTED_FAMILY,
        FAMILY_MISSING,
        VARIANT_MISSING,
        VARIANT_DISABLED,
        INCOMPATIBLE_VARIANT
    }

    public record Validation(Status status, Optional<ShrineItemState> state, boolean usedDefault) {
        public boolean valid() {
            return status == Status.VALID && state.isPresent();
        }
    }

    private final Item shrineItem;
    private final DataComponentType<ShrineItemState> componentType;
    private final FamilyId familyId;
    private final VariantId defaultVariantId;
    private final int expectedCells;

    public ShrineItemStateAccess(Item shrineItem, DataComponentType<ShrineItemState> componentType) {
        this(shrineItem, componentType, ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"), 4);
    }

    public ShrineItemStateAccess(
            Item shrineItem,
            DataComponentType<ShrineItemState> componentType,
            FamilyId familyId,
            VariantId defaultVariantId,
            int expectedCells) {
        this.shrineItem = Objects.requireNonNull(shrineItem, "shrineItem");
        this.componentType = Objects.requireNonNull(componentType, "componentType");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.defaultVariantId = Objects.requireNonNull(defaultVariantId, "defaultVariantId");
        this.expectedCells = expectedCells;
    }

    public Optional<ShrineItemState> read(ItemStack stack) {
        return Optional.ofNullable(stack.get(componentType));
    }

    public Validation validateForPlacement(ItemStack stack, StructureCatalogue catalogue) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(catalogue, "catalogue");
        if (stack.getItem() != shrineItem) {
            return invalid(Status.INVALID_ITEM);
        }
        Optional<ShrineItemState> configured = read(stack);
        ShrineItemState state = configured.orElseGet(() -> new ShrineItemState(
                ShrineItemState.CURRENT_SCHEMA_VERSION, familyId, defaultVariantId));
        if (!state.familyId().equals(familyId)) {
            return new Validation(Status.UNSUPPORTED_FAMILY, Optional.of(state), configured.isEmpty());
        }
        var family = catalogue.family(state.familyId()).orElse(null);
        if (family == null) {
            return new Validation(Status.FAMILY_MISSING, Optional.of(state), configured.isEmpty());
        }
        var variant = catalogue.variant(state.familyId(), state.variantId()).orElse(null);
        if (variant == null) {
            return new Validation(Status.VARIANT_MISSING, Optional.of(state), configured.isEmpty());
        }
        if (!variant.enabled()) {
            return new Validation(Status.VARIANT_DISABLED, Optional.of(state), configured.isEmpty());
        }
        if (!variant.playerFacing() || family.footprint().size() != expectedCells) {
            return new Validation(Status.INCOMPATIBLE_VARIANT, Optional.of(state), configured.isEmpty());
        }
        return new Validation(Status.VALID, Optional.of(state), configured.isEmpty());
    }

    public ItemStack configuredStack(ShrineItemState state) {
        ItemStack stack = new ItemStack(shrineItem);
        stack.set(componentType, Objects.requireNonNull(state, "state"));
        return stack;
    }

    public DataComponentType<ShrineItemState> componentType() {
        return componentType;
    }

    public FamilyId familyId() {
        return familyId;
    }

    public int expectedCells() {
        return expectedCells;
    }

    public static ShrineItemState defaultState() {
        return new ShrineItemState(
                ShrineItemState.CURRENT_SCHEMA_VERSION,
                ShrineMonolithDefinitions.SHRINE,
                new VariantId("honesty"));
    }

    private static Validation invalid(Status status) {
        return new Validation(status, Optional.empty(), false);
    }
}
