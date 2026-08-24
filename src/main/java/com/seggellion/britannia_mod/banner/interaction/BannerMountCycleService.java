package com.seggellion.britannia_mod.banner.interaction;

import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerStructureLifecycle;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative Interior Decorator mutation: cycle a placed banner's mount material
 * through the definition's own {@code supported_mounts} order.
 *
 * <p>Any cell of a multi-block banner resolves to its anchor first, so the mount lives only in
 * the anchor's {@link BannerInstanceState} and can never diverge between sections. The
 * replacement state copies every other field -- definition, fabric material, resolved colour,
 * source pigment -- and the mutation goes through
 * {@link BannerBlockEntity#setBannerStateAndSynchronize}, which preserves the persisted
 * orientation, footprint, block states, and children while marking the entity dirty and
 * resending it to clients. A failed interaction leaves the banner unchanged.
 *
 * <p>Permission parity with the rest of the banner lifecycle: the same
 * {@code mayInteract} + {@code mayUseItemAt} pair placement and removal already answer to, not
 * the shrine tools' permission-level gate -- restyling a banner is a player decoration action
 * on a player-placed block, not world-structure administration.
 */
public final class BannerMountCycleService {
    public enum Result {
        /** The mount changed, was persisted, and was synchronized. */
        CHANGED,
        /** The definition supports exactly one mount; nothing changed. */
        ONLY_MOUNT,
        /** The banner was left untouched. */
        REFUSED;

        public boolean handled() {
            return this != REFUSED;
        }
    }

    private BannerMountCycleService() {
    }

    public static Result cycle(
            ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos, BlockState state) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(tool, "tool");

        BannerStructureLifecycle.Resolution resolution = BannerStructureLifecycle.resolve(level, pos, state);
        if (!resolution.valid() || BannerStructureLifecycle.isGuarded(level, resolution.anchorPos())) {
            return Result.REFUSED;
        }
        BannerBlockEntity anchor = resolution.anchor();
        Direction outwardFacing = anchorFacing(anchor);
        if (!level.mayInteract(player, resolution.anchorPos())
                || !player.mayUseItemAt(resolution.anchorPos(), outwardFacing, tool)) {
            return Result.REFUSED;
        }
        BannerInstanceState current = anchor.bannerState().orElse(null);
        if (current == null || !BannerDataRegistries.isAvailable()) {
            return Result.REFUSED;
        }
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        BannerDefinition definition = snapshot.banners().find(current.bannerDefinitionId()).orElse(null);
        if (definition == null) {
            return Result.REFUSED;
        }
        List<MountId> supported = definition.supportedMounts().stream()
                .filter(id -> snapshot.mounts().contains(id))
                .toList();
        if (supported.isEmpty()) {
            return Result.REFUSED;
        }
        int index = supported.indexOf(current.mountId());
        // A mount no longer in the supported list cycles back INTO validity at the first entry.
        MountId next = supported.get(index < 0 ? 0 : (index + 1) % supported.size());
        MountDefinition nextMount = snapshot.mounts().find(next).orElse(null);
        if (nextMount == null) {
            return Result.REFUSED;
        }
        if (next.equals(current.mountId())) {
            player.displayClientMessage(Component.translatable(
                    "message.britannia_mod.banner.mount.only",
                    Component.translatable(nextMount.displayNameKey())), true);
            return Result.ONLY_MOUNT;
        }
        BannerInstanceState replacement = new BannerInstanceState(
                current.schemaVersion(), current.bannerDefinitionId(), current.materialId(),
                current.resolvedColourId(), current.sourcePigmentId(), next);
        if (!anchor.setBannerStateAndSynchronize(replacement)) {
            return Result.REFUSED;
        }
        if (anchor.bannerState().filter(replacement::equals).isEmpty()) {
            return Result.REFUSED;
        }
        level.playSound(null, resolution.anchorPos(), SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
                SoundSource.BLOCKS, 0.5F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "message.britannia_mod.banner.mount.changed",
                Component.translatable(nextMount.displayNameKey())), true);
        return Result.CHANGED;
    }

    private static Direction anchorFacing(BannerBlockEntity anchor) {
        BlockState state = anchor.getBlockState();
        if (state.hasProperty(com.seggellion.britannia_mod.banner.block.BannerBlock.FACING)) {
            Direction facing = state.getValue(com.seggellion.britannia_mod.banner.block.BannerBlock.FACING);
            if (facing.getAxis().isHorizontal()) {
                return facing;
            }
        }
        return Direction.NORTH;
    }
}
