package com.seggellion.britannia_mod.dye.source;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import java.util.Optional;

@FunctionalInterface
public interface PigmentItemResolver {
    Optional<PigmentItem> resolve(PigmentId pigmentId);
}
