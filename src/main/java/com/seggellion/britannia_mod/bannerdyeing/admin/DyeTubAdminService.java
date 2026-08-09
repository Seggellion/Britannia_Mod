package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.source.PigmentSourceFailure;
import com.seggellion.britannia_mod.dye.source.PigmentSourceService;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creates canonical typed dye tubs without simulating hand loading or consuming pigment items. */
public final class DyeTubAdminService {
    private final Item dyeTubItem;
    private final PigmentSourceService pigmentSource;

    public DyeTubAdminService(Item dyeTubItem, PigmentSourceService pigmentSource) {
        this.dyeTubItem = Objects.requireNonNull(dyeTubItem, "dyeTubItem");
        this.pigmentSource = Objects.requireNonNull(pigmentSource, "pigmentSource");
    }

    public DyeTubAdminResult create(
            Optional<PigmentId> pigmentId, RegistrySnapshot snapshot, boolean registryAvailable) {
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!registryAvailable) {
            return DyeTubAdminResult.failure(DyeTubAdminFailure.REGISTRY_UNAVAILABLE, "registry_snapshot");
        }
        if (pigmentId.isPresent()) {
            var validation = pigmentSource.createPigmentStack(pigmentId.orElseThrow(), 1, snapshot, true);
            if (!validation.successful()) {
                return DyeTubAdminResult.failure(map(validation.failure()), pigmentId.orElseThrow().toString());
            }
        }
        ItemStack tub = new ItemStack(dyeTubItem);
        DyeTubState state = pigmentId.map(DyeTubState::loadedUnlimited).orElseGet(DyeTubState::empty);
        DyeTubStateAccess.write(tub, state);
        if (tub.getCount() != 1 || tub.getMaxStackSize() != 1 || !DyeTubStateAccess.read(tub).equals(state)) {
            return DyeTubAdminResult.failure(DyeTubAdminFailure.STATE_INVALID, "dye_tub_state");
        }
        return DyeTubAdminResult.success(tub);
    }

    private static DyeTubAdminFailure map(PigmentSourceFailure failure) {
        return switch (failure) {
            case REGISTRY_UNAVAILABLE -> DyeTubAdminFailure.REGISTRY_UNAVAILABLE;
            case PIGMENT_DISABLED -> DyeTubAdminFailure.PIGMENT_DISABLED;
            case PIGMENT_MISSING -> DyeTubAdminFailure.PIGMENT_MISSING;
            case ITEM_MAPPING_MISSING -> DyeTubAdminFailure.PIGMENT_ITEM_MAPPING_MISSING;
            default -> DyeTubAdminFailure.STATE_INVALID;
        };
    }
}
