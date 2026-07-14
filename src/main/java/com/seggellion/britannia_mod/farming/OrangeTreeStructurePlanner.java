package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OrangeTreeStructurePlanner {
    public static final int CANOPY_RADIUS = 4;
    public static final int CANOPY_HEIGHT = 7;

    private OrangeTreeStructurePlanner() {
    }

    public static Plan plan(long treeSeed, int growthStep, BlockPos rootPos) {
        return plan(treeSeed, growthStep, rootPos, 1.0f);
    }

    public static Plan plan(long treeSeed, int growthStep, BlockPos rootPos, float fruitSaturation) {
        return plan(FruitTreeRegistry.byIdOrDefault(FruitTreeRegistry.DEFAULT_TREE_ID), treeSeed, growthStep, rootPos, fruitSaturation);
    }

    public static Plan plan(FruitTreeDefinition definition, long treeSeed, int growthStep, BlockPos rootPos) {
        return plan(definition, treeSeed, growthStep, rootPos, 1.0f);
    }

    public static Plan plan(FruitTreeDefinition definition, long treeSeed, int growthStep, BlockPos rootPos, float fruitSaturation) {
        int step = Math.max(1, Math.min(definition.maxGrowthStep(), growthStep));
        Set<BlockPos> trunks = new HashSet<>();
        Set<BlockPos> branches = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();

        addTrunk(definition, trunks, rootPos, step);
        addBranches(definition, branches, rootPos, step, treeSeed);
        addLeaves(definition, leaves, branches, rootPos, step, treeSeed);

        leaves.remove(rootPos);
        leaves.removeAll(trunks);
        leaves.removeAll(branches);

        List<BlockPos> fruitCandidates = step >= 9 ? fruitCandidates(rootPos, leaves) : List.of();
        Set<BlockPos> fruit = step >= 9 ? chooseFruit(treeSeed, fruitCandidates, fruitSaturation) : Set.of();
        leaves.removeAll(fruit);
        return new Plan(Set.copyOf(trunks), Set.copyOf(branches), Set.copyOf(leaves), Set.copyOf(fruit), Set.copyOf(fruitCandidates));
    }

    private static void addTrunk(FruitTreeDefinition definition, Set<BlockPos> trunks, BlockPos rootPos, int step) {
        int trunkBlocks = switch (step) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            default -> definition.trunkHeight();
        };
        for (int y = 1; y <= Math.min(definition.trunkHeight(), trunkBlocks); y++) {
            trunks.add(rootPos.above(y));
        }
    }

    private static void addBranches(FruitTreeDefinition definition, Set<BlockPos> branches, BlockPos rootPos, int step, long treeSeed) {
        if (step < 5) {
            return;
        }

        int horizontalReach = switch (step) {
            case 5 -> 1;
            case 6 -> 2;
            default -> definition.branchRadius();
        };
        BlockPos crownBase = rootPos.above(definition.trunkHeight());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= horizontalReach; distance++) {
                branches.add(crownBase.relative(direction, distance));
            }
        }

        if (step >= 6) {
            RandomSource random = RandomSource.create(treeSeed ^ 0x5f3759dfL);
            List<Direction> directions = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
            shuffle(directions, random);
            int count = step >= 7 ? 4 : 2;
            for (int i = 0; i < count; i++) {
                Direction direction = directions.get(i);
                branches.add(rootPos.above(definition.trunkHeight() + 1).relative(direction));
                branches.add(rootPos.above(definition.trunkHeight() + 2).relative(direction, 2));
            }
        }
    }

    private static void addLeaves(FruitTreeDefinition definition, Set<BlockPos> leaves, Set<BlockPos> branches, BlockPos rootPos, int step, long treeSeed) {
        if (step < 7) {
            return;
        }

        double radiusX = step == 7 ? definition.canopyRadiusX() * 0.52D : step == 8 ? definition.canopyRadiusX() * 0.80D : definition.canopyRadiusX() + 0.25D;
        double radiusZ = step == 7 ? definition.canopyRadiusZ() * 0.52D : step == 8 ? definition.canopyRadiusZ() * 0.80D : definition.canopyRadiusZ() + 0.25D;
        double radiusY = step == 7 ? definition.canopyRadiusY() * 0.45D : step == 8 ? definition.canopyRadiusY() * 0.72D : definition.canopyRadiusY() * 0.78D;
        BlockPos center = rootPos.above(definition.trunkHeight() + 1);
        int horizontalLimit = (int) Math.ceil(radiusX);
        int verticalLimit = (int) Math.ceil(radiusY);

        for (int dx = -horizontalLimit; dx <= horizontalLimit; dx++) {
            for (int dy = -verticalLimit; dy <= verticalLimit; dy++) {
                for (int dz = -horizontalLimit; dz <= horizontalLimit; dz++) {
                    BlockPos leafPos = center.offset(dx, dy, dz);
                    int relativeY = leafPos.getY() - rootPos.getY();
                    if (relativeY < 2 || relativeY > definition.trunkHeight() + definition.canopyRadiusY()) {
                        continue;
                    }

                    double nx = dx / radiusX;
                    double ny = dy / radiusY;
                    double nz = dz / radiusZ;
                    double distance = nx * nx + ny * ny + nz * nz;
                    double domePenalty = dy < -1 ? Math.abs(dy + 1) * 0.12D : 0.0D;
                    double threshold = 1.0D - domePenalty;
                    if (distance > threshold) {
                        continue;
                    }

                    boolean nearEdge = distance > 0.70D || Math.abs(dx) == horizontalLimit || Math.abs(dz) == horizontalLimit;
                    float keepChance = nearEdge ? 0.45f : 0.92f;
                    if (dy > 0 && distance < 0.85D) {
                        keepChance = Math.min(1.0f, keepChance + 0.08f);
                    }
                    if (dy < -1 && nearEdge) {
                        keepChance *= 0.65f;
                    }
                    if (noise(treeSeed, leafPos, 0x0ddc0ffeL) <= keepChance) {
                        leaves.add(leafPos);
                    }
                }
            }
        }

        for (BlockPos branch : branches) {
            int relativeY = branch.getY() - rootPos.getY();
            if (relativeY >= 2 && relativeY <= definition.trunkHeight() + 2) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos leafPos = branch.relative(direction);
                    if (!branches.contains(leafPos) && noise(treeSeed, leafPos, 0x51eeafL) < 0.80f) {
                        leaves.add(leafPos);
                    }
                }
            }
        }
    }

    private static List<BlockPos> fruitCandidates(BlockPos rootPos, Set<BlockPos> leaves) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos leaf : leaves) {
            int horizontalDistance = Math.abs(leaf.getX() - rootPos.getX()) + Math.abs(leaf.getZ() - rootPos.getZ());
            int relativeY = leaf.getY() - rootPos.getY();
            if (horizontalDistance >= 3 && relativeY >= 3 && relativeY <= 5) {
                candidates.add(leaf);
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                .thenComparingInt(pos -> pos.getY())
                .thenComparingInt(pos -> pos.getZ()));
        return candidates;
    }

    private static Set<BlockPos> chooseFruit(long treeSeed, List<BlockPos> candidates, float fruitSaturation) {
        RandomSource random = RandomSource.create(treeSeed ^ 0x04a11ceL);
        shuffle(candidates, random);
        float saturation = Math.max(0.0f, Math.min(1.0f, fruitSaturation));
        int fruitCount = Math.min(candidates.size(), Math.max(saturation > 0.0f ? 2 : 0, Math.round(candidates.size() * saturation)));
        Set<BlockPos> fruit = new HashSet<>();
        for (BlockPos candidate : candidates) {
            if (fruit.size() >= fruitCount) {
                break;
            }
            fruit.add(candidate);
        }
        return fruit;
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T value = values.get(i);
            values.set(i, values.get(j));
            values.set(j, value);
        }
    }

    private static float noise(long treeSeed, BlockPos pos, long salt) {
        long seed = treeSeed ^ salt;
        seed ^= pos.asLong() * 0x9E3779B97F4A7C15L;
        seed ^= ((long) pos.getY()) * 0xBF58476D1CE4E5B9L;
        return RandomSource.create(seed).nextFloat();
    }

    public record Plan(Set<BlockPos> trunks, Set<BlockPos> branches, Set<BlockPos> leaves, Set<BlockPos> fruit, Set<BlockPos> fruitCandidates) {
        public boolean owns(BlockPos pos) {
            return trunks.contains(pos) || branches.contains(pos) || leaves.contains(pos) || fruit.contains(pos) || fruitCandidates.contains(pos);
        }

        public int placementCount() {
            return trunks.size() + branches.size() + leaves.size() + fruit.size();
        }
    }
}
