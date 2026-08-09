package com.seggellion.britannia_mod.structure.item;

import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementService;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Shared configured-family item boundary; registry and serialized IDs remain family-specific. */
public class ConfiguredStructureItem extends Item {
    private final Supplier<DataComponentType<ShrineItemState>> componentType;
    private final FamilyId familyId;
    private final VariantId defaultVariantId;
    private final int expectedCells;

    public ConfiguredStructureItem(
            Properties properties,
            Supplier<DataComponentType<ShrineItemState>> componentType,
            FamilyId familyId,
            VariantId defaultVariantId,
            int expectedCells) {
        super(properties);
        this.componentType = Objects.requireNonNull(componentType, "componentType");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.defaultVariantId = Objects.requireNonNull(defaultVariantId, "defaultVariantId");
        if (expectedCells != 4 && expectedCells != 18) {
            throw new IllegalArgumentException("Configured large structures require four or eighteen cells");
        }
        this.expectedCells = expectedCells;
    }

    public ShrineItemStateAccess stateAccess() {
        return new ShrineItemStateAccess(
                this, componentType.get(), familyId, defaultVariantId, expectedCells);
    }

    public FamilyId familyId() {
        return familyId;
    }

    public VariantId defaultVariantId() {
        return defaultVariantId;
    }

    public int expectedCells() {
        return expectedCells;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return ShrinePlacementService.place(context);
    }
}
