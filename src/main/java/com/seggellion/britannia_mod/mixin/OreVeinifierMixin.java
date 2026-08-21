package com.seggellion.britannia_mod.mixin;

import com.seggellion.britannia_mod.worldgen.NoiseVeinOreAuthority;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes the ore out of Minecraft's noise ore veins, and leaves the veins.
 *
 * <h2>Why a mixin, and why here</h2>
 * The vein rule is built inside {@code NoiseChunk}'s constructor from three density functions and a
 * random factory, and it decides ore-or-filler in code rather than in data. There is no biome
 * modifier that reaches it — it is not a feature — and no NeoForge event that wraps it.
 *
 * <p>The two data-level alternatives were both worse. Clearing {@code ore_veins_enabled} means
 * shipping an override of the whole Overworld {@code noise_settings} file, which freezes vanilla's
 * entire terrain router at this version's values <em>and</em> deletes the granite and tuff the veins
 * deposit; forcing {@code vein_gap} to a constant needs the same whole-file override for a subtler
 * effect. Both trade a narrow problem for a wide one.
 *
 * <p>{@code OreVeinifier.create} is the narrowest point that exists: one method, one return value,
 * and the returned rule is the only thing in the game that can emit vein ore. Wrapping it changes
 * what a vein is made of and nothing else — not where veins are, not how big they are, not the
 * shape of a single cell.
 *
 * <h2>Scope, by construction</h2>
 * {@code NoiseChunk} only calls this when the dimension's noise settings enable ore veins. In
 * 1.21.1 that is the Overworld family alone; the Nether, the End, the caves preset and the
 * floating-islands preset all pass {@code false}, so this method is never called for them and this
 * mixin cannot affect them. It is a generation rule, so chunks already on disk are untouched.
 */
@Mixin(OreVeinifier.class)
public abstract class OreVeinifierMixin {

    /**
     * Wrap the vanilla vein rule so that its ore results become the vein's own filler.
     *
     * <p>Deliberately a wrapper rather than a reimplementation. Everything that decides whether a
     * cell is in a vein, whether it is material, and what filler that vein uses stays vanilla and
     * keeps running; only the answer is inspected on the way out. A cell vanilla declined
     * ({@code null}) is still declined, so the generator's default-block fallback is unchanged.
     */
    // remap = false: this environment runs official Mojang mappings, so the method name at
    // runtime is the name written here. The other server mixins in this package take the
    // same position for the same reason.
    @Inject(method = "create", at = @At("RETURN"), cancellable = true, remap = false)
    private static void britannia$removeOreFromVeins(
            DensityFunction veinToggle,
            DensityFunction veinRidged,
            DensityFunction veinGap,
            PositionalRandomFactory oreRandom,
            CallbackInfoReturnable<NoiseChunk.BlockStateFiller> callback) {

        NoiseChunk.BlockStateFiller vanilla = callback.getReturnValue();
        if (vanilla == null) {
            return;
        }
        callback.setReturnValue(context -> {
            BlockState produced = vanilla.calculate(context);
            return NoiseVeinOreAuthority.substitute(produced);
        });
    }
}
