package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.List;
import java.util.Locale;

public class MaterialQualityJewelryItem extends Item implements IItemExtension {

    // Custom-data keys (support both UpperCamel and lower_snake for resilience with loot JSON)
    private static final String KEY_QUALITY_INT  = "Quality";
    private static final String KEY_QUALITY_STR  = "quality";
    private static final String KEY_MATERIAL     = "Material";
    private static final String KEY_MATERIAL_STR = "material";
    private static final String KEY_TYPE         = "JewelryType";

    // This item instance represents one jewelry "shape" (ring, necklace, etc)
    private final JewelryType jewelryType;
    // The material to assume when none is specified (your Mongbat case = COPPER)
    private final UOMaterial defaultMaterial;

    public MaterialQualityJewelryItem(JewelryType type, UOMaterial defaultMaterial, Properties props) {
        super(props.stacksTo(1));
        this.jewelryType = type;
        this.defaultMaterial = defaultMaterial;
    }

    /* ---------- Material & Quality enums ---------- */

    public enum UOMaterial {
        COPPER("copper", 1),
        SILVER("silver", 2),
        GOLD("gold", 3),
        VALORITE("valorite", 4);

        private final String name;
        // model offset lets us build per-type model ids without hard-coding every combo
        private final int modelOffset;

        UOMaterial(String name, int modelOffset) {
            this.name = name;
            this.modelOffset = modelOffset;
        }
        public String id() { return name; }
        public int modelOffset() { return modelOffset; }

        public static UOMaterial byName(String s) {
            if (s == null) return null;
            String k = s.toLowerCase(Locale.ROOT);
            for (UOMaterial m : values()) if (m.name.equals(k)) return m;
            return null;
        }
    }

    public enum QualityTier {
        CRUDE(1, "Crude"),
        BASIC(2, "Basic"),
        FINE(3, "Fine"),
        EXCEPTIONAL(4, "Exceptional");

        private final int level;
        private final String label;

        QualityTier(int level, String label) { this.level = level; this.label = label; }
        public int level() { return level; }
        public String label() { return label; }

        public static QualityTier fromLevel(int lvl) {
            for (QualityTier qt : values()) if (qt.level == lvl) return qt;
            return BASIC;
        }

        public static QualityTier fromName(String s) {
            if (s == null) return BASIC;
            switch (s.toLowerCase(Locale.ROOT)) {
                case "crude":        return CRUDE;
                case "basic":        return BASIC;
                case "fine":         return FINE;
                case "exceptional":  return EXCEPTIONAL;
                default:             return BASIC;
            }
        }

        // Weighted random for loot (tweak as desired)
        // 5% Exceptional, 25% Fine, 50% Basic, 20% Crude
        public static QualityTier randomForLoot(RandomSource r) {
            int roll = r.nextInt(100);
            if (roll < 5)   return EXCEPTIONAL;
            if (roll < 30)  return FINE;
            if (roll < 80)  return BASIC;
            return CRUDE;
        }
    }

    public enum JewelryType {
        NECKLACE(2000),
        EARRINGS(2100),
        BRACELET(2200),
        RING(2300),
        BEADS(2400);

        private final int baseModel;
        JewelryType(int baseModel) { this.baseModel = baseModel; }
        public int baseModel() { return baseModel; }

        public static JewelryType byName(String s) {
            if (s == null) return null;
            String k = s.toUpperCase(Locale.ROOT);
            for (JewelryType t : values()) if (t.name().equals(k)) return t;
            return null;
        }
    }

    /* ---------- Public helpers (get/set) ---------- */

    public static int getQuality(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        // accept either an int "Quality" or a string "quality"
        if (tag.contains(KEY_QUALITY_INT)) return tag.getInt(KEY_QUALITY_INT);
        if (tag.contains(KEY_QUALITY_STR)) return QualityTier.fromName(tag.getString(KEY_QUALITY_STR)).level();
        return QualityTier.BASIC.level();
    }

    public static void setQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putInt(KEY_QUALITY_INT, quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void setQuality(ItemStack stack, QualityTier tier) {
        setQuality(stack, tier.level());
    }

    public static UOMaterial getMaterial(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        String name = null;
        if (tag.contains(KEY_MATERIAL))     name = tag.getString(KEY_MATERIAL);
        else if (tag.contains(KEY_MATERIAL_STR)) name = tag.getString(KEY_MATERIAL_STR);
        return UOMaterial.byName(name);
    }

    public static void setMaterial(ItemStack stack, UOMaterial material) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString(KEY_MATERIAL, material.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void setJewelryType(ItemStack stack, JewelryType type) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        tag.putString(KEY_TYPE, type.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static JewelryType getJewelryType(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        if (tag.contains(KEY_TYPE)) return JewelryType.byName(tag.getString(KEY_TYPE));
        return null;
    }

    /* ---------- Initialization & model wiring ---------- */

    private static boolean hasInit(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        boolean hasMat = tag.contains(KEY_MATERIAL) || tag.contains(KEY_MATERIAL_STR);
        boolean hasQ   = tag.contains(KEY_QUALITY_INT) || tag.contains(KEY_QUALITY_STR);
        return hasMat && hasQ;
    }

    private void ensureInitializedForLoot(ItemStack stack, RandomSource rand) {
        if (hasInit(stack)) return;
        // default Mongbat material = copper, quality = weighted random
        setMaterial(stack, defaultMaterial);
        setQuality(stack, QualityTier.randomForLoot(rand));
        setJewelryType(stack, this.jewelryType);
        updateModelData(stack);
    }

    // build a consistent CustomModelData id: base per jewelry type + offset per material
    private void updateModelData(ItemStack stack) {
        UOMaterial mat = getMaterial(stack);
        JewelryType type = getJewelryType(stack);
        if (mat == null) mat = defaultMaterial;
        if (type == null) type = this.jewelryType;

        int modelId = type.baseModel() + mat.modelOffset();
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelId));
    }

    /* ---------- Hooks ---------- */

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            // When spawned by loot (no set_components), make it pick material/quality once
            ensureInitializedForLoot(stack, level.getRandom());
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if (!level.isClientSide) {
            // If nobody set components during crafting, default to type + material and BASIC quality.
            if (!hasInit(stack)) {
                setMaterial(stack, defaultMaterial);
                setQuality(stack, QualityTier.BASIC);
                setJewelryType(stack, this.jewelryType);
                updateModelData(stack);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);

        UOMaterial mat = getMaterial(stack);
        if (mat != null) {
            tooltip.add(Component.literal("Material: " + mat.id()));
        }

        int q = getQuality(stack);
        tooltip.add(Component.literal("Quality: " + QualityTier.fromLevel(q).label() + " (" + q + ")"));

        JewelryType type = getJewelryType(stack);
        if (type != null) {
            tooltip.add(Component.literal("Type: " + type.name().toLowerCase(Locale.ROOT)));
        }
    }

    /* ---------- Optional: helpers you can call from recipes or commands ---------- */

    /** Call this from your recipe assemble if you want deterministic craft quality */
    public static void applyCraftQuality(ItemStack stack, int craftSkill0to100) {
        QualityTier tier =
                (craftSkill0to100 >= 90) ? QualityTier.EXCEPTIONAL :
                (craftSkill0to100 >= 70) ? QualityTier.FINE :
                (craftSkill0to100 >= 50) ? QualityTier.BASIC : QualityTier.CRUDE;
        setQuality(stack, tier);
    }

    /** Force a material on an instance and refresh its model id */
    public static void applyMaterial(ItemStack stack, UOMaterial mat, JewelryType type) {
        setMaterial(stack, mat);
        if (type != null) setJewelryType(stack, type);
        // recompute model id
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CompoundTag t = cd.copyTag();
        // Trigger an update via setter so it recomputes model id
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        if (stack.getItem() instanceof MaterialQualityJewelryItem mqi) {
            mqi.updateModelData(stack);
        }
    }
}
