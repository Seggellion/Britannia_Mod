#!/usr/bin/env python3
"""Vendor/Trader Milestone 21 Phase A: re-audit the excluded_pending_item rows.

The Milestone 2 item-universe extraction only matched `register("...")` calls,
so every item registered through a helper (cookedFood and friends -- the whole
produce/food catalog, ~175 ids) was invisible to the audit and its rows were
misclassified MISSING_ITEM. Owner directive for this milestone: be mindful of
items that already exist. This script:

  1. builds the TRUE current universe (DeferredHolder-aware ItemRegistry scan
     + blacksmithing craftables + the curated vanilla set);
  2. matches each pending row's RunUO type against it (exact snake_case, then
     a reviewed alias table for naming differences);
  3. rewrites matched rows in the mapping (uc_item_id + mapping_status), so a
     rollout regeneration seeds them with zero new items.

Run before any item creation; print what remains genuinely missing.
"""
import json
import os
import re
import collections

HERE = os.path.dirname(os.path.abspath(__file__))
MAPPING = os.path.join(HERE, "..", "runuo_ultimacraft_mapping.json")
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))

# Curated vanilla ids the economy may reference (mirrors the parse script's set).
VANILLA = {
    "bread", "apple", "carrot", "potato", "baked_potato", "pumpkin", "pumpkin_pie",
    "cake", "cookie", "egg", "book", "paper", "map", "bowl", "bucket", "shears",
    "fishing_rod", "torch", "lantern", "barrel", "chest", "glass_bottle",
    # Wave 2: reviewed vanilla tool/armor aliases (a shop selling an iron
    # pickaxe is a faithful reading of RunUO's plain Pickaxe).
    "iron_pickaxe", "iron_shovel", "iron_axe", "shield",
}


def true_universe():
    text = open(os.path.join(REPO, "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"),
                encoding="utf-8", errors="replace").read()
    ids = set(re.findall(r'register\s*\(\s*"([a-z0-9_]+)"', text))
    # Helper-registered items (cookedFood etc.) declare a DeferredHolder whose
    # initializer's first string literal is the registry id.
    ids |= set(re.findall(r'DeferredHolder<[^>]*>\s+\w+\s*=\s*[\w.]+\(\s*"([a-z0-9_]+)"', text, re.S))

    craftables = os.path.join(REPO, "src/main/resources/data/britannia_mod/blacksmithing/craftables.json")
    if os.path.exists(craftables):
        data = json.load(open(craftables, encoding="utf-8-sig"))
        for recipe in data.get("recipes", []):
            out = recipe.get("output", "")
            if out.startswith("britannia_mod:"):
                ids.add(out.split(":", 1)[1])
        # BlacksmithItemRegistry also registers every distinct craftables
        # INGREDIENT key (minus the vanilla-mapped ones) as a mod item --
        # duplicating one of those in ItemRegistry leaves a null registry hole
        # (caught by the noItemIdIsRegisteredTwice gametest).
        vanilla_mapped = {"ingot", "board", "cloth", "bone", "boards_or_logs"}
        for recipe in data.get("recipes", []):
            for ingredient in recipe.get("ingredients", []):
                key = ingredient.get("key", "")
                if key and key not in vanilla_mapped:
                    ids.add(key)
    return ids


def snake(runuo_type):
    s = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", runuo_type)
    return s.lower()


# Reviewed aliases: RunUO type -> existing UltimaCraft id. Only entries verified
# against the real registry belong here; guessing is what Phase B is for.
ALIASES = {
    "CookedBird": "cooked_bird",
    "ChickenLeg": "chicken_leg",
    "LambLeg": "leg_of_lamb",
    "Ribs": "cut_of_ribs",
    "Ham": "ham",
    "Bacon": "slice_of_bacon",
    "Sausage": "sausage",
    "RoastPig": "roast_pig",
    "ApplePie": "apple_pie",
    "Peach": "peaches",
    "Pear": "pears",
    "Grapes": "concord_grapes",
    "Lemon": "lemon",
    "Lime": "lime",
    "Cantaloupe": "cantaloupe",
    "HoneydewMelon": "honeydew",
    "Squash": "squash",
    "Cabbage": "cabbage",
    "Onion": "onion",
    "Lettuce": "lettuce",
    "Carrot": "carrots",
    "Turnip": "turnip",
    "EarOfCorn": "corn",
    "Watermelon": "watermelon",
    "CheeseWheel": "cheese_wheel",
    "CheeseWedge": "cheese_wedge",
    "CheeseSlice": "cheese_slice",
    "FrenchBread": "baguette",
    "BreadLoaf": "bread",
    "Muffins": "muffin",
    "Cake": "cake",
    "Cookies": "cookie",
    "Torch": "torch",
    "Lantern": "lantern",
    "Bottle": "glass_bottle",
    "Book": "book",
    "BlankMap": "map",
    "EmptyWoodenBowl": "bowl",
    # Wave 2 (Milestone 21): verified against the full universe dump.
    "Chessboard": "chess_board",
    "Tambourine": "tamborine",
    "Key": "chest_key",
    "Hides": "raw_hide",
    "SheafOfHay": "straw",
    "Jug": "pitcher_empty",
    "RawBird": "raw_chicken",
    "RawLambLeg": "raw_leg_of_lamb",
    "RawRibs": "raw_pork_ribs",
    "RawFishSteak": "cooked_fish_steak",
    "Scissors": "scissors",
    "MapmakersPen": "scribes_pen",
    "TanBook": "tan_book",
    "MalletAndChisel": "stonecrafter_hammer",
    "TinkersTools": "tinker_hammer",
    "FletcherTools": "carpenter_hammer",
    "LightYarnUnraveled": "light_yarn",
    "GreenGourd": "squash",
    "YellowGourd": "squash",
    "Pickaxe": "iron_pickaxe",
    "Shovel": "iron_shovel",
    "FishingPole": "fishing_rod",
    "WoodenShield": "shield",
    "JarHoney": "jar_of_honey",
    # Wave 3 (Milestone 21): the metal armor craftables exist under the
    # platemail/ringmail/chainmail naming; these rows are variable-material
    # products and keep that status (only the item id resolves).
    "PlateChest": "platemail_tunic",
    "PlateLegs": "platemail_legs",
    "PlateArms": "platemail_arms",
    "PlateGloves": "platemail_gloves",
    "PlateGorget": "platemail_gorget",
    "PlateHelm": "plate_helm",
    "ChainCoif": "chainmail_coif",
    "ChainChest": "chainmail_tunic",
    "ChainLegs": "chainmail_leggings",
    "RingmailChest": "ringmail_tunic",
    "RingmailLegs": "ringmail_leggings",
    "RingmailArms": "ringmail_sleeves",
    "RingmailGloves": "ringmail_gloves",
    "Buckler": "buckler",
    "MetalKiteShield": "metal_kite_shield",
    "WoodenKiteShield": "tear_kite_shield",
    # Wave 4 (Milestone 21): SE metal armor also exists under the platemail/
    # chainmail naming; same varmat-preserving treatment as wave 3.
    "PlateDo": "platemail_do",
    "PlateHaidate": "platemail_haidate",
    "PlateHatsuburi": "platemail_hatsuburi",
    "PlateHiroSode": "platemail_hiro_sode",
    "PlateSuneate": "platemail_suneate",
    "ChainHatsuburi": "chainmail_hatsuburi",
    "DecorativePlateKabuto": "decorative_platemail_kabuto",
    "HeavyPlateJingasa": "heavy_platemail_jingasa",
    # Plain hatchet retails as the vanilla iron axe, like Pickaxe/Shovel.
    "Hatchet": "iron_axe",
}


def main():
    mod_ids = true_universe()
    universe = mod_ids | VANILLA
    mapping = json.load(open(MAPPING, encoding="utf-8"))

    recovered = collections.Counter()
    still_missing = collections.Counter()
    changed = 0

    for catalog in mapping["catalogs"].values():
        for row in catalog["buy_rows"]:
            if row.get("product_status") != "excluded_pending_item":
                continue
            runuo_type = row.get("type") or ""
            exact = snake(runuo_type)
            resolved = None
            status = None
            if exact in universe:
                resolved, status = exact, "DIRECT_MATCH"
            elif runuo_type in ALIASES and ALIASES[runuo_type] in universe:
                resolved, status = ALIASES[runuo_type], "LIKELY_MATCH"

            if resolved:
                # A mod registration always wins over a vanilla name collision
                # (e.g. bread exists in both; the economy uses the mod item).
                prefix = "britannia_mod:" if resolved in mod_ids else "minecraft:"
                row["uc_item_id"] = prefix + resolved
                # Variable-material rows resolved to a MOD item keep their
                # status: they gain an item id but still price through the
                # material machinery. Resolved to a VANILLA item they become
                # fixed products instead -- material variants cannot apply to
                # minecraft: ids, so varmat semantics would misprice them.
                if (row.get("mapping_status") != "VARIABLE_MATERIAL_PRODUCT"
                        or resolved not in mod_ids):
                    row["mapping_status"] = status
                row["mapping_note"] = (
                    "Milestone 21 re-audit: item existed all along; the Milestone 2 "
                    "universe extraction missed helper-registered items"
                )
                recovered[runuo_type] += 1
                changed += 1
            else:
                still_missing[runuo_type] += 1

    with open(MAPPING, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(mapping, fh, indent=1, ensure_ascii=False)
        fh.write("\n")

    print(f"recovered rows: {changed} across {len(recovered)} types")
    for name, count in recovered.most_common():
        print(f"  {count:2}x {name}")
    print(f"still missing: {sum(still_missing.values())} rows across {len(still_missing)} types")
    for name, count in still_missing.most_common(30):
        print(f"  {count:2}x {name}")


if __name__ == "__main__":
    main()
