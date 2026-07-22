package com.seggellion.britannia_mod.banner.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Read-only advisory world access. Implementations must not request or force-load chunks. */
public interface BannerPlacementPreviewWorld {
    boolean inWorldBounds(BlockPos position);
    boolean chunkLoaded(BlockPos position);
    boolean replaceable(BlockPos position);
    boolean validWallSupport(BlockPos supportPosition, Direction outwardFacing);

    /** False is the normal client value because region/protection authority is server-only. */
    boolean serverProtectionKnownAllowed();
}
