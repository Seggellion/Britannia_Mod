package com.seggellion.britannia_mod.mixin.client;

import com.seggellion.britannia_mod.client.house.ClientHouseBuildRights;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.AdventureHarvestableBlock;
import com.seggellion.britannia_mod.vegetation.ManagedVegetationCutTools;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The client half of Adventure mode's break gate, for the cases the server authorises.
 *
 * <h2>Why one mixin covers every case</h2>
 *
 * <p>Adventure mode refuses breaking twice over, in two places that do not talk to each other. The
 * server's refusal is {@code Player.blockActionRestricted}, reached from
 * {@code CommonHooks.fireBlockBreak} and again from {@code ServerPlayerGameMode.destroyBlock}. The
 * client's is the identical call at the top of {@code MultiPlayerGameMode.startDestroyBlock} — and
 * that one runs <em>before the packet is sent</em>, so a client refusal means the server never
 * hears that anything was attempted.
 *
 * <p>Everything Britannia authorises in Adventure mode therefore has to be exempted here as well,
 * and there is exactly one instruction to redirect, so all of it lives in one place. A second
 * {@code @Redirect} against the same call would be a mixin conflict rather than a second feature.
 *
 * <p>Nothing here decides anything. It only stops the client refusing on the server's behalf; the
 * server still applies every real rule, and refuses the attempt if it was not legitimate.
 *
 * <ul>
 *   <li><b>Managed vegetation and wild resources</b> — an authorised cutting tool. Ownership of the
 *       node is checked server-side.</li>
 *   <li><b>House build rights</b> — {@code SurvivalZoneHandler} lends an owner {@code mayBuild}
 *       while they stand in their own house, but {@code ClientboundPlayerAbilitiesPacket} does not
 *       carry {@code mayBuild}, so the client never learned. That is why an owner could place
 *       blocks in their house and not break them. {@code S2CHouseBuildRightsPayload} is what tells
 *       the client, and {@code HouseBuildRights} / {@code StructureProtectionHandler} still refuse
 *       the perimeter, the lot block, somebody else's house, and anything outside the house.</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientAdventureBreakGateMixin {
    @Redirect(
            method = "startDestroyBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;blockActionRestricted("
                            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/GameType;)Z"
            ),
            remap = false
    )
    private boolean britannia$allowServerAuthorisedAdventureBreak(
            LocalPlayer player,
            Level level,
            BlockPos position,
            GameType gameType
    ) {
        if (gameType == GameType.ADVENTURE) {
            BlockState state = level.getBlockState(position);
            boolean managedVegetationAttack = ManagedVegetationCutTools.canCut(player)
                    && isManagedVegetationCandidate(state);
            boolean wildResourceAttack = state.getBlock() instanceof AdventureHarvestableBlock harvestable
                    && harvestable.allowsAdventureHarvest(player.getMainHandItem());
            if (managedVegetationAttack || wildResourceAttack || ClientHouseBuildRights.granted()) {
                return false;
            }
        }
        return player.blockActionRestricted(level, position, gameType);
    }

    private static boolean isManagedVegetationCandidate(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(BlockRegistry.FERN.get())
                || state.is(BlockRegistry.BLOOD_MOSS.get())
                || state.is(BlockRegistry.MANAGED_FLOWER.get());
    }
}
