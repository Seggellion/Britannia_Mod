package com.seggellion.britannia_mod.banner.crafting;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItemFactoryResult;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.registry.BannerRecipeRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One data-driven shapeless recipe implementation. JSON selects only the design and provisional area cost;
 * material and mount are always derived from the current authoritative inputs.
 */
public final class BannerCraftingRecipe extends CustomRecipe {
    public static final int MAX_FABRIC_UNITS = 6;

    private final int schemaVersion;
    private final BannerDefinitionId definitionId;
    private final int fabricUnits;
    private final BannerCraftingEnvironment environment;

    public BannerCraftingRecipe(int schemaVersion, BannerDefinitionId definitionId, int fabricUnits) {
        this(schemaVersion, definitionId, fabricUnits, BannerCraftingEnvironment.production());
    }

    public BannerCraftingRecipe(
            int schemaVersion,
            BannerDefinitionId definitionId,
            int fabricUnits,
            BannerCraftingEnvironment environment) {
        super(CraftingBookCategory.MISC);
        if (schemaVersion != BannerDyeingConstants.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported banner crafting schema: " + schemaVersion);
        }
        if (fabricUnits < 1 || fabricUnits > MAX_FABRIC_UNITS) {
            throw new IllegalArgumentException("fabricUnits must be between 1 and " + MAX_FABRIC_UNITS);
        }
        this.schemaVersion = schemaVersion;
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.fabricUnits = fabricUnits;
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public BannerDefinitionId definitionId() {
        return definitionId;
    }

    public int fabricUnits() {
        return fabricUnits;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return evaluate(input).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return evaluate(input).orElse(ItemStack.EMPTY);
    }

    private Optional<ItemStack> evaluate(CraftingInput input) {
        if (!environment.registryAvailable().getAsBoolean()
                || input.ingredientCount() != fabricUnits + 2) {
            return Optional.empty();
        }
        RegistrySnapshot snapshot = environment.snapshot().get();
        BannerDefinition definition = snapshot.banners().find(definitionId).orElse(null);
        if (definition == null
                || definition.dimensions().widthBlocks() * definition.dimensions().heightBlocks() != fabricUnits) {
            return Optional.empty();
        }

        int patterns = 0;
        int fabrics = 0;
        int mounts = 0;
        FabricMaterialId materialId = null;
        MountId mountId = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean pattern = stack.getItem() == environment.patternItem().get();
            CraftingIdentityResolution<FabricMaterialId> fabric =
                    environment.fabricResolver().resolve(stack, snapshot, environment.tagLookup());
            CraftingIdentityResolution<MountId> mount =
                    environment.mountResolver().resolve(stack, snapshot, definition, environment.tagLookup());
            int classifications = (pattern ? 1 : 0) + (fabric.successful() ? 1 : 0) + (mount.successful() ? 1 : 0);
            if (fabric.failure() == CraftingIdentityFailure.AMBIGUOUS_INPUT
                    || mount.failure() == CraftingIdentityFailure.AMBIGUOUS_INPUT
                    || classifications != 1) {
                return Optional.empty();
            }
            if (pattern) {
                patterns++;
                BannerDefinitionId patternDefinition = stack.get(environment.patternComponent().get());
                if (patternDefinition == null || !patternDefinition.equals(definitionId)) {
                    return Optional.empty();
                }
            } else if (fabric.successful()) {
                fabrics++;
                FabricMaterialId resolved = fabric.identity().orElseThrow();
                if (materialId != null && !materialId.equals(resolved)) {
                    return Optional.empty();
                }
                materialId = resolved;
            } else {
                mounts++;
                MountId resolved = mount.identity().orElseThrow();
                if (mountId != null && !mountId.equals(resolved)) {
                    return Optional.empty();
                }
                mountId = resolved;
            }
        }
        if (patterns != 1 || fabrics != fabricUnits || mounts != 1
                || materialId == null || mountId == null) {
            return Optional.empty();
        }
        BannerItemFactoryResult result = environment.factory().craftedMaterialBanner(
                definitionId, materialId, Optional.of(mountId), snapshot, true);
        return result.stack().map(ItemStack::copy);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        if (evaluate(input).isEmpty()) {
            return remaining;
        }
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.getItem() == environment.patternItem().get()) {
                ItemStack pattern = stack.copy();
                pattern.setCount(1);
                remaining.set(index, pattern);
            } else if (stack.hasCraftingRemainingItem()) {
                remaining.set(index, stack.getCraftingRemainingItem());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= fabricUnits + 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BannerRecipeRegistry.BANNER_CRAFTING_SERIALIZER.get();
    }
}
