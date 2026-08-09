package com.seggellion.britannia_mod.dye.source;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;

public interface PigmentSourceService {
    PigmentSourceResult createPigmentStack(
            PigmentId pigmentId, int count, RegistrySnapshot snapshot, boolean registryAvailable);

    PigmentSourceListResult listAvailablePigments(RegistrySnapshot snapshot, boolean registryAvailable);
}
