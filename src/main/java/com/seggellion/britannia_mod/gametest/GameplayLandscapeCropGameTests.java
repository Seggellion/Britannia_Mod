package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayLandscapeCropGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Set<String> REMOVED = Set.of("minecraft:patch_pumpkin", "minecraft:patch_melon", "minecraft:patch_melon_sparse");

    @GameTest(template = TEMPLATE)
    public static void everyLoadedBiomeRemovesOnlyTheThreeLandscapeRoutes(GameTestHelper h) {
        var references = new TreeMap<String, Integer>(); int biomes = 0;
        for (var holder : h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            var biome = holder.value(); var original = biome.modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings();
            var oldVegetation = features(original, GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
            var current = features(biome.getGenerationSettings(), GenerationStep.Decoration.VEGETAL_DECORATION.ordinal());
            var expected = new LinkedHashSet<>(oldVegetation);
            if (holder.is(BiomeTags.IS_OVERWORLD)) {
                biomes++;
                for (var id : REMOVED) if (expected.remove(id)) references.merge(id, 1, Integer::sum);
            }
            h.assertTrue(current.equals(expected), "vegetation drift in " + holder.unwrapKey().orElseThrow().location() + ": expected " + expected + " but got " + current);
            for (var step : GenerationStep.Decoration.values()) h.assertTrue(Collections.disjoint(features(biome.getGenerationSettings(), step.ordinal()), REMOVED), "landscape route survives in " + holder.unwrapKey().orElseThrow().location());
        }
        h.assertTrue(biomes > 30, "biome registry audit was empty");
        h.assertTrue(references.equals(Map.of("minecraft:patch_pumpkin", 46, "minecraft:patch_melon", 2, "minecraft:patch_melon_sparse", 1)), "pre-modifier routes changed: " + references);
        com.mojang.logging.LogUtils.getLogger().info("[gameplay M6] audited {} Overworld biomes; original routes {}; remaining=0; other vegetation unchanged", biomes, references);
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void structureAuthoredPumpkinsAndMelonsRemainInAllThirteenTemplates(GameTestHelper h) throws Exception {
        var resources = h.getLevel().getServer().getResourceManager().listResources("structure", id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".nbt"));
        var preserved = new TreeSet<String>();
        for (var entry : resources.entrySet()) try (var input = entry.getValue().open()) {
            var tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()); var text = tag.toString();
            if (text.contains("minecraft:pumpkin") || text.contains("minecraft:melon")) preserved.add(entry.getKey().toString());
        }
        h.assertTrue(preserved.size() == 13, "structure fruit routes changed: " + preserved);
        com.mojang.logging.LogUtils.getLogger().info("[gameplay M6] preserved structure-authored fruit/stems in {}", preserved);
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void intentionalNativeSeedAndBonemealCultivationStillProducesBothFruit(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(3, 2, 3));
        var player = ManagedResourceTestPlayers.survival(level, "LandscapeStemControl");
        // Above the flat template fixtures, with real sky light; never change randomTickSpeed.
        var pos = new BlockPos(base.getX(), 200, base.getZ());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(pos.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
            level.setBlock(pos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        h.runAfterDelay(3, () -> {
            try {
                for (boolean melon : new boolean[]{false, true}) {
                    // SurvivalZoneHandler can change the joined player to Adventure during the light wait.
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5);
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setBlock(pos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                    var seed = new ItemStack(melon ? Items.MELON_SEEDS : Items.PUMPKIN_SEEDS, 2); player.setItemInHand(InteractionHand.MAIN_HAND, seed);
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
                    var planted = player.gameMode.useItemOn(player, level, seed, InteractionHand.MAIN_HAND, hit);
                    var stem = melon ? Blocks.MELON_STEM : Blocks.PUMPKIN_STEM;
                    var fruit = melon ? Blocks.MELON : Blocks.PUMPKIN;
                    h.assertTrue(level.getBlockState(pos).is(stem) && seed.getCount() == 1, "native seed planting changed: result=" + planted + " mode=" + player.gameMode.getGameModeForPlayer() + " state=" + level.getBlockState(pos) + " soil=" + level.getBlockState(pos.below()) + " count=" + seed.getCount());
                    var meal = new ItemStack(Items.BONE_MEAL, 8);
                    for (int i = 0; i < 4 && level.getBlockState(pos).getValue(StemBlock.AGE) < 7; i++) BoneMealItem.applyBonemeal(meal, level, pos, player);
                    h.assertTrue(level.getBlockState(pos).getValue(StemBlock.AGE) == 7 && meal.getCount() < 8, "bonemeal no longer matures stem");
                    var random = RandomSource.create(20260907L);
                    for (int i = 0; i < 64 && level.getBlockState(pos).is(stem); i++) level.getBlockState(pos).randomTick(level, pos, random);
                    int fruits = 0;
                    for (var direction : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(direction)).is(fruit)) fruits++;
                    h.assertTrue(fruits == 1 && level.getBlockState(pos).getBlock() instanceof AttachedStemBlock, "intentional native fruit failed; brightness=" + level.getRawBrightness(pos, 0));
                    var saved = NbtUtils.writeBlockState(level.getBlockState(pos));
                    h.assertTrue(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), saved).equals(level.getBlockState(pos)), "existing attached stem state changed on serialization");
                }
                h.succeed();
            } finally {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(pos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos.offset(dx, -1, dz), Blocks.AIR.defaultBlockState(), 3);
                }
                level.getServer().getPlayerList().remove(player);
            }
        });
    }

    private static Set<String> features(BiomeGenerationSettings settings, int step) {
        if (settings.features().size() <= step) return Set.of();
        var result = new LinkedHashSet<String>();
        for (var feature : settings.features().get(step)) result.add(feature.unwrapKey().map(key -> key.location().toString()).orElse("inline"));
        return result;
    }
}
