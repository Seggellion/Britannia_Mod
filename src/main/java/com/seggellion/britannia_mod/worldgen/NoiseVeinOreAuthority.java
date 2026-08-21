package com.seggellion.britannia_mod.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What Minecraft's noise ore veins are allowed to put in the ground.
 *
 * <h2>The hole this closes</h2>
 * Milestone 5 removed every vanilla economic-mineral <em>placed feature</em> from every Overworld
 * biome, and proved it: coal, gold, redstone, diamond and lapis reached exactly zero over 256
 * fixed-seed chunks. Copper and iron did not. They kept roughly four and three percent of their
 * vanilla volume, because Minecraft has a second, older path to the same blocks —
 * {@code OreVeinifier}, which the noise chunk generator runs while it is still deciding what each
 * cell is made of, long before any decoration step exists to be removed from.
 *
 * <p>No biome modifier can reach it. It is not attached to a biome, it is not a feature, and it is
 * switched on by {@code ore_veins_enabled} on the dimension's noise settings.
 *
 * <h2>The distinction this class is careful about</h2>
 * A vein is not made of ore. Reading {@code OreVeinifier}, a cell that lands inside a vein becomes
 * ore only when two further rolls succeed; every other material cell becomes the vein's
 * <b>filler</b> — granite for a copper vein, tuff for an iron one. The filler is the large majority
 * of the output, and it is real terrain: the granite banks and tuff pockets that make deep cave
 * systems look the way players expect.
 *
 * <p>So "remove the ore" and "remove the veins" are very different changes. This removes the ore.
 * A cell that vanilla would have made copper ore becomes the granite it was embedded in; a cell
 * that would have been deepslate iron ore becomes tuff. The vein keeps its shape, its size, its
 * position and its geology, and stops being a source of economy.
 *
 * <p>That is also why the substitute is the vein's own filler rather than {@code minecraft:stone}.
 * Painting stone through a granite bank would leave visible artificial tubes underground, which is
 * a worse outcome than the problem being solved.
 *
 * <h2>Scope</h2>
 * Overworld only, by construction rather than by a check: {@code NoiseChunk} builds the vein rule
 * at all only when the dimension's noise settings enable ore veins, and in 1.21.1 the Nether, the
 * End, the caves preset and the floating-islands preset all set that flag false. Nothing here can
 * run for them.
 *
 * <p>Nothing here touches an existing chunk either. This is a generation rule; a chunk already on
 * disk is never regenerated, so its geology is whatever it already was.
 */
public final class NoiseVeinOreAuthority {

    /**
     * Every block {@code OreVeinifier.VeinType} can emit that is economically meaningful, mapped to
     * the filler of the vein that would have produced it.
     *
     * <p>Written out rather than derived, because the mapping is the policy. The copper vein's
     * filler is granite and the iron vein's is tuff — those are the values in
     * {@code OreVeinifier.VeinType}, and a test pins that this map still agrees with them.
     */
    private static final Map<Block, Block> SUBSTITUTIONS = new LinkedHashMap<>();

    static {
        // Copper vein: ore and raw block both become the vein's own granite.
        SUBSTITUTIONS.put(Blocks.COPPER_ORE, Blocks.GRANITE);
        SUBSTITUTIONS.put(Blocks.RAW_COPPER_BLOCK, Blocks.GRANITE);
        // Iron vein: ore and raw block both become the vein's own tuff.
        SUBSTITUTIONS.put(Blocks.DEEPSLATE_IRON_ORE, Blocks.TUFF);
        SUBSTITUTIONS.put(Blocks.RAW_IRON_BLOCK, Blocks.TUFF);
    }

    private NoiseVeinOreAuthority() {
    }

    /** The blocks the noise-vein path may no longer produce. */
    public static Set<Block> deniedBlocks() {
        return Set.copyOf(SUBSTITUTIONS.keySet());
    }

    /** The filler each denied block is replaced by. */
    public static Map<Block, Block> substitutions() {
        return Map.copyOf(SUBSTITUTIONS);
    }

    /**
     * Apply the authority to one state the vein rule produced.
     *
     * <p>Total: every input has an answer, and anything not denied is returned untouched. A
     * {@code null} means "this cell is not vein material", which is the vein rule's own way of
     * declining, and is passed straight through so the generator falls back to its default block
     * exactly as it would have.
     */
    public static BlockState substitute(BlockState produced) {
        if (produced == null) {
            return null;
        }
        Block replacement = SUBSTITUTIONS.get(produced.getBlock());
        return replacement == null ? produced : replacement.defaultBlockState();
    }

    /** Whether this state is one the noise-vein path is no longer allowed to place. */
    public static boolean isDenied(BlockState state) {
        return state != null && SUBSTITUTIONS.containsKey(state.getBlock());
    }
}
