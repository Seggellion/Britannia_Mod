#!/usr/bin/env python3
"""Vendor/Trader Milestone 17: generate the full RunUO vendor rollout.

Reads runuo_ultimacraft_mapping.json and emits:
  1. economic_vendor_rollout.json (next to the mapping) -- the reviewable,
     machine-readable rollout: every vendor type to seed (with entity
     presentation and active flag) and every Product+listing to seed (with
     provisional production requirements referencing ONLY canonical commodity
     keys from the Rails CommoditySeeder vocabulary). The same file is copied
     verbatim into the Rails repo as db/seeds/data/economic_vendor_rollout.json
     and consumed by db/seeds/economic_vendor_rollout.rb.
  2. In-place mapping updates: `vendor_rollout` on every vendor and
     `product_status` on every buy row, so the matrix reports every RunUO
     vendor and retail row as implemented or explicitly excluded (the
     Milestone 17 acceptance), enforced by RunuoRetailRolloutCoverageTest.

Requirements are PROVISIONAL family conventions (admin-editable Product data;
Milestone 19 section-2a calibration owns tuning). A row whose inputs cannot be
expressed with canonical commodity keys is excluded explicitly, never guessed.
"""
import json
import collections
import os

HERE = os.path.dirname(os.path.abspath(__file__))
MAPPING = os.path.join(HERE, "..", "runuo_ultimacraft_mapping.json")
ROLLOUT = os.path.join(HERE, "..", "economic_vendor_rollout.json")

GENERIC_VENDOR_ENTITY = "britannia_mod:vendor"

# Existing dedicated entities keep their presentation.
DEDICATED_ENTITIES = {
    "baker": "britannia_mod:baker",
    "tavernkeeper": "britannia_mod:tavernkeeper",
    "costermonger": "britannia_mod:costermonger",
}

# Vendor uc_keys that are Trader aliases (buyback-only; raw-commodity retail is
# the city commodity market's job, not Product retail).
TRADER_ALIAS_KEYS = {
    "fish_trader", "ore_trader", "fur_leather_trader", "wood_trader",
    "salvage_trader", "alcohol_trader", "stone_trader", "meat_trader",
    "grain_trader", "produce_trader",
}

# OQ-8 owner-approved dispositions for the ten REQUIRES_OWNER_MAPPING vendors.
OQ8_DISPOSITION = {
    "GolemCrafter": "merged_into:tinker_vendor",
    "GypsyMaiden": "merged_into:provisioner_vendor",
    "RealEstateBroker": "excluded_service",
    "Thief": "merged_into:provisioner_vendor",
    "Vagabond": "merged_into:jeweler_vendor",
    "VarietyDealer": "merged_into:provisioner_vendor",
    "EvilHealer": "merged_into:healer_vendor",
    "PricedHealer": "excluded_service",
    "Hamato": "excluded_guildmaster_territory",
    "Ryuichi": "excluded_guildmaster_territory",
}

# The three owner-decided per-profession hammers (items created in this
# milestone); their rows flip from MISSING_ITEM to seeded products.
HAMMER_ROWS = {
    ("SBCarpenter", "Hammer"): "britannia_mod:carpenter_hammer",
    ("SBStoneCrafter", "Hammer"): "britannia_mod:stonecrafter_hammer",
    ("SBTinker", "Hammer"): "britannia_mod:tinker_hammer",
}

# --- Canonical commodity vocabulary (Rails CommoditySeeder). Only these keys
# --- may ever appear in generated requirements.
CANONICAL_KEYS = {
    "grain|milled|flour", "grain|milled|oat_flour", "grain|milled|rye_flour",
    "grain|whole|barley", "grain|whole|oats", "grain|whole|rye", "grain|whole|wheat",
    "dairy|cheese|cheddar", "dairy|milk|cow_milk", "dairy|butter|cow_butter",
    "eggs|chicken|chicken_egg", "groceries|sweetener|honey", "groceries|sweetener|sugar",
    "meat|chicken|raw_chicken", "meat|beef|raw_beef", "meat|pork|raw_pork",
    "meat|lamb|raw_lamb", "meat|turkey|raw_turkey", "meat|venison|raw_venison",
    "produce|vegetable|cabbage", "produce|vegetable|carrots", "produce|vegetable|corn",
    "produce|vegetable|lettuce", "produce|vegetable|onion", "produce|vegetable|potato",
    "produce|vegetable|pumpkin", "produce|vegetable|squash", "produce|vegetable|tomato",
    "produce|fruit|apple", "produce|fruit|banana", "produce|fruit|berries",
    "produce|fruit|concord_grapes", "produce|fruit|peaches", "produce|fruit|pears",
    "wood|logs|oak", "stone|common|stone",
    "metal|ingots|iron", "metal|ingots|gold",
    "leather|processed|leather",
    # Owner roadmap pass 2026-08-11: the OQ-5 glass and reagent families.
    "glass|raw|sand",
    "reagents|raw|black_pearl", "reagents|raw|blood_moss", "reagents|raw|garlic",
    "reagents|raw|ginseng", "reagents|raw|mandrake", "reagents|raw|nightshade",
    "reagents|raw|spiders_silk", "reagents|raw|sulphurous_ash",
}

# Name-pattern input rules, checked in order against the lowercased RunUO type.
NAME_RULES = [
    (("bread", "muffin", "cake", "pie", "cookie", "pastry", "doughnut", "bun",
      "baguette", "croissant", "scone", "tart", "pizza", "quiche"), "grain|milled|flour"),
    (("ale", "beer", "liquor", "whiskey", "mead", "cider", "bottleof"), "grain|whole|barley"),
    (("cheese",), "dairy|cheese|cheddar"),
    (("egg",), "eggs|chicken|chicken_egg"),
    (("milk",), "dairy|milk|cow_milk"),
    (("honey",), "groceries|sweetener|honey"),
    (("chicken",), "meat|chicken|raw_chicken"),
    (("beef", "steak", "ribs", "brisket", "roast"), "meat|beef|raw_beef"),
    (("pork", "bacon", "ham", "sausage",), "meat|pork|raw_pork"),
    (("lamb", "mutton"), "meat|lamb|raw_lamb"),
    (("turkey",), "meat|turkey|raw_turkey"),
    (("venison",), "meat|venison|raw_venison"),
    (("cabbage",), "produce|vegetable|cabbage"), (("carrot",), "produce|vegetable|carrots"),
    (("corn", "earofcorn"), "produce|vegetable|corn"), (("lettuce",), "produce|vegetable|lettuce"),
    (("onion",), "produce|vegetable|onion"), (("potato",), "produce|vegetable|potato"),
    (("pumpkin",), "produce|vegetable|pumpkin"), (("squash", "gourd"), "produce|vegetable|squash"),
    (("tomato",), "produce|vegetable|tomato"),
    (("apple",), "produce|fruit|apple"), (("banana",), "produce|fruit|banana"),
    (("peach",), "produce|fruit|peaches"), (("pear",), "produce|fruit|pears"),
    (("grape",), "produce|fruit|concord_grapes"),
    (("backpack", "pouch", "bag", "belt"), "leather|processed|leather"),
    (("lantern", "key", "lockpick", "scissors", "tongs", "skillet", "pot", "pan",
      "kettle"), "metal|ingots|iron"),
    (("torch", "lute", "drum", "harp", "tambourine", "flute", "fishingpole",
      "shepherdscrook", "club", "walkingstick"), "wood|logs|oak"),
    # Reagent retail (mage/alchemist/herbalist rows) restocks from the city's
    # own reagent supply; glasswork consumes raw sand.
    (("blackpearl",), "reagents|raw|black_pearl"),
    (("bloodmoss",), "reagents|raw|blood_moss"),
    (("garlic",), "reagents|raw|garlic"),
    (("ginseng",), "reagents|raw|ginseng"),
    (("mandrake",), "reagents|raw|mandrake"),
    (("nightshade",), "reagents|raw|nightshade"),
    (("spiderssilk", "spidersilk"), "reagents|raw|spiders_silk"),
    (("sulfurousash", "sulphurousash"), "reagents|raw|sulphurous_ash"),
    (("bottle", "flask", "vial", "blowpipe", "glass", "jar"), "glass|raw|sand"),
]

# Vendor-family fallback inputs (only where a canonical family exists).
FAMILY_FALLBACK = {
    "baker": "grain|milled|flour",
    "cook_vendor": "grain|milled|flour",
    "innkeeper_vendor": "grain|milled|flour",
    "tavernkeeper": "grain|whole|barley",
    "butcher_vendor": "meat|beef|raw_beef",
    "costermonger": "produce|vegetable|carrots",
    "miller_vendor": "grain|whole|wheat",
    "carpenter_vendor": "wood|logs|oak",
    "bard_vendor": "wood|logs|oak",
    "stone_crafter_vendor": "stone|common|stone",
    "tinker_vendor": "metal|ingots|iron",
    "jeweler_vendor": "metal|ingots|gold",
    "provisioner_vendor": "leather|processed|leather",
    "tailor_vendor": "leather|processed|leather",
    "cobbler_vendor": "leather|processed|leather",
    "leather_worker_vendor": "leather|processed|leather",
    "weaver_vendor": None,        # textile family pending (OQ-5)
    "glassblower_vendor": "glass|raw|sand",
    "alchemist_vendor": None,     # reagent family pending (OQ-5)
    "mage_vendor": None,
    "holy_mage_vendor": None,
    "herbalist_vendor": None,
    "healer_vendor": None,
    "scribe_vendor": None,        # scribe family pending (OQ-5)
    "mapmaker_vendor": None,
}

# Metal variable-material quantity by product shape (provisional, section 2a).
def metal_quantity(type_name):
    n = type_name.lower()
    if any(p in n for p in ("helm", "helmet", "coif", "bascinet", "sallet", "circlet")):
        return 1.5
    if any(p in n for p in ("shield", "buckler", "kite", "heater")):
        return 2.5
    if any(p in n for p in ("plate", "chain", "ring", "mail", "tunic", "leggings",
                            "legs", "arms", "gloves", "gorget", "gauntlet", "do",
                            "haidate", "suneate", "mempo", "kote")):
        return 3.0
    if any(p in n for p in ("tongs", "hammer", "shovel", "pickaxe", "axe" if "battle" not in n else "",
                            "sledge")) and "battle" not in n and "war" not in n:
        return 1.0
    return 2.0


def input_for(type_name, vendor_key):
    n = type_name.lower()
    for patterns, key in NAME_RULES:
        if any(p and p in n for p in patterns):
            assert key in CANONICAL_KEYS, key
            return key
    fallback = FAMILY_FALLBACK.get(vendor_key, None)
    if fallback:
        assert fallback in CANONICAL_KEYS, fallback
    return fallback


def main():
    with open(MAPPING, encoding="utf-8") as fh:
        mapping = json.load(fh)
    vendors = mapping["vendors"]
    catalogs = mapping["catalogs"]

    # ---- Vendor dispositions ------------------------------------------------
    catalog_vendor_keys = collections.defaultdict(set)  # catalog -> vendor uc_keys that retail it
    vendor_types = {}  # uc_key -> definition

    for vendor in vendors:
        status = vendor["mapping_status"]
        uc_key = vendor.get("uc_key") or ""
        cls = vendor["vendor_class"]
        if cls in OQ8_DISPOSITION:
            vendor["vendor_rollout"] = OQ8_DISPOSITION[cls]
            target = OQ8_DISPOSITION[cls]
            if target.startswith("merged_into:"):
                merged_key = target.split(":", 1)[1]
                for add in vendor["sb_catalogs"]:
                    catalog_vendor_keys[add["catalog"]].add(merged_key)
            continue
        if status in ("SERVICE_ONLY", "EXISTING_SERVICE_NPC"):
            vendor["vendor_rollout"] = "excluded_service"
            continue
        if status == "UNSUPPORTED_BY_DESIGN":
            vendor["vendor_rollout"] = "excluded_unsupported"
            continue
        if uc_key in TRADER_ALIAS_KEYS:
            vendor["vendor_rollout"] = "merged_into_trader:" + uc_key
            continue
        if uc_key == "animal_trainer_vendor":
            vendor["vendor_rollout"] = "excluded_pending_mobile_fulfillment"
            continue
        if uc_key == "architect_vendor":
            vendor["vendor_rollout"] = "excluded_service"
            continue
        # Implementable vendor: collect catalogs under its uc_key.
        for add in vendor["sb_catalogs"]:
            catalog_vendor_keys[add["catalog"]].add(uc_key)
        vendor_types.setdefault(uc_key, {
            "key": uc_key,
            "display_name": vendor.get("title") or cls,
            "profession_key": uc_key.removesuffix("_vendor"),
            "entity": DEDICATED_ENTITIES.get(uc_key, GENERIC_VENDOR_ENTITY),
            "existing": uc_key in DEDICATED_ENTITIES,
            "vendor_classes": [],
        })
        vendor_types[uc_key]["vendor_classes"].append(cls)
        vendor["vendor_rollout"] = "implemented:" + uc_key

    # ---- Row dispositions and products -------------------------------------
    products = {}  # item_id -> product definition
    row_counts = collections.Counter()

    for catalog_name, catalog in catalogs.items():
        vendor_keys = sorted(catalog_vendor_keys.get(catalog_name, ()))
        for row in catalog["buy_rows"]:
            status = row["mapping_status"]
            type_name = row.get("type") or ""
            hammer_item = HAMMER_ROWS.get((catalog_name, type_name))
            if hammer_item:
                row["uc_item_id"] = hammer_item
                row["mapping_status"] = "DIRECT_MATCH"
                status = "DIRECT_MATCH"

            if status in ("UNSUPPORTED_PENDING_MAGIC",):
                row["product_status"] = "excluded_pending_magic"
            elif status in ("SERVICE_EXISTING_SYSTEM",):
                row["product_status"] = "excluded_service"
            elif status == "SERVICE_OR_MOBILE":
                row["product_status"] = "excluded_pending_mobile_fulfillment"
            elif status == "UNSUPPORTED":
                row["product_status"] = "excluded_unsupported"
            elif status == "MISSING_ITEM":
                row["product_status"] = "excluded_pending_item"
            elif not vendor_keys:
                row["product_status"] = "excluded_no_retail_vendor"
            elif status == "VARIABLE_MATERIAL_PRODUCT":
                if not row.get("uc_item_id"):
                    row["product_status"] = "excluded_pending_item"
                else:
                    row["product_status"] = "seeded_material"
                    add_product(products, row, vendor_keys, material=True)
            elif status in ("DIRECT_MATCH", "LIKELY_MATCH"):
                input_key = None
                for vendor_key in vendor_keys:
                    input_key = input_for(type_name, vendor_key)
                    if input_key:
                        break
                if input_key is None:
                    row["product_status"] = "excluded_pending_commodity_family"
                else:
                    row["product_status"] = "seeded"
                    add_product(products, row, vendor_keys, input_key=input_key)
            else:
                row["product_status"] = "excluded_" + status.lower()
            row_counts[row["product_status"]] += 1

    # Vendors with no seeded products become inactive (registered, explicit).
    seeded_vendors = set()
    for product in products.values():
        seeded_vendors.update(product["vendors"])
    for definition in vendor_types.values():
        definition["active"] = definition["key"] in seeded_vendors

    rollout = {
        "generated_by": "docs/vendor-trader-economy/tools/generate_vendor_rollout.py",
        "milestone": 17,
        "pricing": "runuo_gp_price as GOLD (owner rule: 1 RunUO GP = 1 UltimaCraft Gold)",
        "vendor_types": sorted(vendor_types.values(), key=lambda v: v["key"]),
        "products": sorted(products.values(), key=lambda p: p["item_id"]),
        "row_status_counts": dict(sorted(row_counts.items())),
    }
    with open(ROLLOUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(rollout, fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    with open(MAPPING, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(mapping, fh, indent=1, ensure_ascii=False)
        fh.write("\n")

    active = sum(1 for v in vendor_types.values() if v["active"])
    print(f"vendor types: {len(vendor_types)} ({active} active)")
    print(f"products: {len(products)}")
    for status, count in sorted(row_counts.items()):
        print(f"  {status}: {count}")


def add_product(products, row, vendor_keys, material=False, input_key=None):
    item_id = row["uc_item_id"]
    type_name = row.get("type") or ""
    existing = products.get(item_id)
    if existing:
        for key in vendor_keys:
            if key not in existing["vendors"]:
                existing["vendors"].append(key)
        return

    title = "".join(
        (" " + c if c.isupper() else c) for c in type_name
    ).strip().title() or item_id.split(":", 1)[-1].replace("_", " ").title()
    definition = {
        "item_id": item_id,
        "title": title,
        "runuo_type": type_name,
        "price_gold": int(row.get("runuo_gp_price") or 1),
        "vendors": list(vendor_keys),
    }
    if material:
        definition["material_requirements"] = [
            {"material_family": "metal", "quantity": metal_quantity(type_name)}
        ]
    else:
        definition["commodity_requirements"] = [
            {"key": input_key, "quantity": 1.0}
        ]
    products[item_id] = definition


if __name__ == "__main__":
    main()
