#!/usr/bin/env python3
"""Milestone 2 RunUO vendor audit generator.

Reads the pinned read-only RunUO reference checkout (runuo/runuo @
71b2794f12eb6f948b1c5598ae8b350401a22d4d) and produces the project-owned
machine-readable mapping source `../runuo_ultimacraft_mapping.json`.

The pinned checkout is the completeness authority (owner decision).
`runuo_vendor_catalog.json` is reference input only and is NOT read here.

Usage:
    python parse_runuo_vendors.py [--runuo C:/projects/runuo-reference] \
                                  [--repo <mod repo root>]

The script never writes into the RunUO checkout.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

PINNED_COMMIT = "71b2794f12eb6f948b1c5598ae8b350401a22d4d"

# --------------------------------------------------------------------------
# C# scanning helpers
# --------------------------------------------------------------------------

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def strip_block_comments(text: str) -> str:
    return re.sub(r"/\*.*?\*/", "", text, flags=re.S)


def balanced(text: str, start: int, open_ch: str = "(", close_ch: str = ")") -> int:
    """Index just past the matching close for the open at text[start]."""
    depth = 0
    i = start
    in_str = False
    while i < len(text):
        ch = text[i]
        if in_str:
            if ch == '"' and text[i - 1] != "\\":
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return -1


def method_body(text: str, header_re: str) -> str | None:
    m = re.search(header_re, text)
    if not m:
        return None
    brace = text.find("{", m.end())
    if brace < 0:
        return None
    end = balanced(text, brace, "{", "}")
    return text[brace + 1 : end - 1] if end > 0 else None


def split_args(arglist: str) -> list[str]:
    out, depth, cur, in_str = [], 0, [], False
    for i, ch in enumerate(arglist):
        if in_str:
            cur.append(ch)
            if ch == '"' and arglist[i - 1] != "\\":
                in_str = False
            continue
        if ch == '"':
            in_str = True
            cur.append(ch)
        elif ch in "({[":
            depth += 1
            cur.append(ch)
        elif ch in ")}]":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if cur:
        out.append("".join(cur).strip())
    return out


def line_conditions(body: str) -> list[tuple[str, str | None]]:
    """Yield (line, active_condition) pairs, tracking simple if/else blocks.

    Handles the shapes that occur in RunUO SBInfo/vendor initializers:
    single-statement ifs and braced if/else blocks, non-nested in practice.
    """
    results: list[tuple[str, str | None]] = []
    pending: str | None = None       # condition awaiting its statement/block
    block: str | None = None         # condition of the current braced block
    depth_at_block = 0
    depth = 0
    last_condition: str | None = None
    for raw in body.splitlines():
        line = raw.strip()
        m = re.match(r"(?:else\s+)?if\s*\((.*)\)\s*(\{)?\s*$", line)
        melse = re.match(r"else\s*(\{)?\s*$", line)
        if m:
            cond = m.group(1).strip()
            if line.startswith("else"):
                cond = f"!({last_condition}) && ({cond})" if last_condition else cond
            if m.group(2):
                block, depth_at_block = cond, depth
                depth += 1
            else:
                pending = cond
            last_condition = cond
            continue
        if melse:
            cond = f"!({last_condition})" if last_condition else "else"
            if melse.group(1):
                block, depth_at_block = cond, depth
                depth += 1
            else:
                pending = cond
            continue
        opens = line.count("{")
        closes = line.count("}")
        if line == "{" and pending is not None:
            block, depth_at_block = pending, depth
            pending = None
            depth += 1
            continue
        active = block if block is not None else pending
        if line and not line.startswith("//"):
            results.append((line, active))
            if pending is not None and not line.startswith("{"):
                pending = None
        elif line.startswith("//"):
            results.append((line, active))
        depth += opens - closes
        if block is not None and depth <= depth_at_block:
            block = None
    return results


# --------------------------------------------------------------------------
# SBInfo catalog parsing
# --------------------------------------------------------------------------

def parse_buy_call(kind: str, args: list[str]) -> dict:
    row: dict = {"buy_info_class": kind, "raw_args": args}

    def is_typeof(a: str) -> bool:
        return a.startswith("typeof")

    def typeof_name(a: str) -> str:
        m = re.search(r"typeof\s*\(\s*([\w\.]+)\s*\)", a)
        return m.group(1).split(".")[-1] if m else a

    def to_int(a: str):
        a = a.strip()
        try:
            return int(a, 0)
        except ValueError:
            return a  # expression (kept verbatim)

    i = 0
    if args and args[0].startswith('"'):
        row["name_override"] = args[0].strip('"')
        i = 1
    if kind == "AnimalBuyInfo":
        row["control_slots"] = to_int(args[i]); i += 1
    if i < len(args) and is_typeof(args[i]):
        row["type"] = typeof_name(args[i]); i += 1
    if kind == "BeverageBuyInfo" and i < len(args) and not re.match(r"^-?\d", args[i]):
        row["content"] = args[i]; i += 1
    if kind == "PresetMapBuyInfo":
        row["map_entry"] = args[0]
        row["price"] = to_int(args[1]) if len(args) > 1 else None
        row["amount"] = to_int(args[2]) if len(args) > 2 else None
        return row
    fields = ["price", "amount", "item_id", "hue"]
    for field in fields:
        if i < len(args):
            row[field] = to_int(args[i]); i += 1
    if i < len(args):
        row["constructor_args"] = args[i:]
    return row


def parse_sb_file(path: Path, root: Path) -> list[dict]:
    text = strip_block_comments(read(path))
    catalogs = []
    for cm in re.finditer(r"class\s+(\w+)\s*:\s*SBInfo\b", text):
        name = cm.group(1)
        cls_start = cm.start()
        nxt = re.search(r"class\s+\w+\s*:\s*SBInfo\b", text[cm.end():])
        cls_text = text[cls_start : cm.end() + (nxt.start() if nxt else len(text))]

        buy_rows, sell_rows, inactive = [], [], []
        buy_body = method_body(cls_text, r"public\s+InternalBuyInfo\s*\(\s*\)")
        if buy_body:
            ordinal = 0
            for line, cond in line_conditions(buy_body):
                if line.startswith("//"):
                    if re.search(r"Add\s*\(", line):
                        inactive.append({"section": "buy", "raw": line.lstrip("/ ").strip()})
                    continue
                for am in re.finditer(r"Add\s*\(\s*new\s+(\w+)\s*\(", line):
                    end = balanced(line, line.index("(", am.start()))
                    inner_open = line.index("(", am.end() - 1)
                    inner_end = balanced(line, inner_open)
                    args = split_args(line[inner_open + 1 : inner_end - 1])
                    row = parse_buy_call(am.group(1), args)
                    row["ordinal"] = ordinal
                    if cond:
                        row["condition"] = cond
                    ordinal += 1
                    buy_rows.append(row)
        sell_body = method_body(cls_text, r"public\s+InternalSellInfo\s*\(\s*\)")
        if sell_body:
            ordinal = 0
            for line, cond in line_conditions(sell_body):
                if line.startswith("//"):
                    if re.search(r"Add\s*\(", line):
                        inactive.append({"section": "sell", "raw": line.lstrip("/ ").strip()})
                    continue
                for sm in re.finditer(
                    r"Add\s*\(\s*typeof\s*\(\s*([\w\.]+)\s*\)\s*,\s*(\d+)\s*\)", line
                ):
                    row = {
                        "type": sm.group(1).split(".")[-1],
                        "price": int(sm.group(2)),
                        "ordinal": ordinal,
                    }
                    if cond:
                        row["condition"] = cond
                    ordinal += 1
                    sell_rows.append(row)
        catalogs.append(
            {
                "catalog": name,
                "source_file": str(path.relative_to(root)).replace("\\", "/"),
                "buy_rows": buy_rows,
                "sell_rows": sell_rows,
                "inactive_source_rows": inactive,
            }
        )
    return catalogs


# --------------------------------------------------------------------------
# Vendor class parsing
# --------------------------------------------------------------------------

def parse_vendor_file(path: Path, root: Path) -> list[dict]:
    text = strip_block_comments(read(path))
    vendors = []
    for cm in re.finditer(r"class\s+(\w+)\s*:\s*([\w\.]+)", text):
        name, base = cm.group(1), cm.group(2).split(".")[-1]
        cls_start = cm.start()
        nxt = re.search(r"\bclass\s+\w+\s*:", text[cm.end():])
        cls_text = text[cls_start : cm.end() + (nxt.start() if nxt else len(text))]
        body = method_body(cls_text, r"override\s+void\s+InitSBInfo\s*\(\s*\)")
        if body is None:
            continue
        adds = []
        for line, cond in line_conditions(body):
            if line.startswith("//"):
                continue
            for am in re.finditer(r"SBInfos\s*\.\s*Add\s*\(\s*new\s+(\w+)\s*\(", line):
                entry: dict = {"catalog": am.group(1)}
                if cond:
                    entry["condition"] = cond
                adds.append(entry)
        tm = re.search(r":\s*base\s*\(\s*\"([^\"]*)\"", cls_text)
        vendors.append(
            {
                "vendor_class": name,
                "base_class": base,
                "title": tm.group(1) if tm else None,
                "source_file": str(path.relative_to(root)).replace("\\", "/"),
                "sb_catalogs": adds,
                "init_sb_info_empty": not adds,
            }
        )
    return vendors


# --------------------------------------------------------------------------
# UltimaCraft mapping layer
# --------------------------------------------------------------------------

# Existing UltimaCraft economic/service NPC roles (verified from source; see
# COMPATIBILITY_MAP.md and PROJECT_FACTS.md).
UC_TRADERS = [
    "wood_trader", "fish_trader", "salvage_trader", "alcohol_trader", "ore_trader",
    "stone_trader", "meat_trader", "grain_trader", "produce_trader", "fur_leather_trader",
]
UC_MERCHANTS = ["baker", "tavernkeeper", "costermonger"]

# RunUO vendor class -> existing/proposed UltimaCraft mapping.
# status values:
#   EXISTING_MERCHANT / EXISTING_SERVICE_NPC : role already exists in UltimaCraft
#   PROPOSED_VENDOR   : new economic Vendor definition required (key proposed)
#   PROPOSED_TRADER_ALIAS : RunUO vendor's economic role is covered by a Trader
#   SERVICE_ONLY      : NPC is service-behavior only in UltimaCraft terms
#   REQUIRES_OWNER_MAPPING : owner must decide the role
VENDOR_MAP: dict[str, dict] = {
    "Alchemist": {"uc": "alchemist_vendor", "status": "PROPOSED_VENDOR"},
    "AnimalTrainer": {"uc": "animal_trainer_vendor", "status": "PROPOSED_VENDOR"},
    "GypsyAnimalTrainer": {"uc": "animal_trainer_vendor", "status": "PROPOSED_VENDOR"},
    "Architect": {"uc": "architect_vendor", "status": "PROPOSED_VENDOR",
                   "note": "ArchitectEntity exists in Minecraft but has no catalog service"},
    "Armorer": {"uc": "armorer_vendor", "status": "PROPOSED_VENDOR"},
    "Baker": {"uc": "baker", "status": "EXISTING_MERCHANT"},
    "Bard": {"uc": "bard_vendor", "status": "PROPOSED_VENDOR"},
    "Barkeeper": {"uc": "tavernkeeper", "status": "EXISTING_MERCHANT"},
    "Beekeeper": {"uc": "beekeeper_vendor", "status": "PROPOSED_VENDOR"},
    "Blacksmith": {"uc": "blacksmith_vendor", "status": "PROPOSED_VENDOR"},
    "Bowyer": {"uc": "bowyer_vendor", "status": "PROPOSED_VENDOR"},
    "Butcher": {"uc": "butcher_vendor", "status": "PROPOSED_VENDOR",
                 "note": "buyback side maps to meat_trader"},
    "Carpenter": {"uc": "carpenter_vendor", "status": "PROPOSED_VENDOR"},
    "Cobbler": {"uc": "cobbler_vendor", "status": "PROPOSED_VENDOR"},
    "Cook": {"uc": "cook_vendor", "status": "PROPOSED_VENDOR"},
    "CustomHairstylist": {"uc": None, "status": "SERVICE_ONLY",
                            "note": "appearance service; no economy catalog target"},
    "Farmer": {"uc": "costermonger", "status": "EXISTING_MERCHANT",
                "note": "produce retail overlaps Costermonger; buyback -> produce_trader"},
    "Fisherman": {"uc": "fish_trader", "status": "PROPOSED_TRADER_ALIAS",
                   "note": "fish buyback exists as fish_trader; retail rows need a fishmonger vendor decision"},
    "Furtrader": {"uc": "fur_leather_trader", "status": "PROPOSED_TRADER_ALIAS"},
    "Glassblower": {"uc": "glassblower_vendor", "status": "PROPOSED_VENDOR"},
    "GolemCrafter": {"uc": "golem_crafter_vendor", "status": "REQUIRES_OWNER_MAPPING"},
    "GypsyBanker": {"uc": "bank_teller", "status": "EXISTING_SERVICE_NPC"},
    "GypsyMaiden": {"uc": None, "status": "REQUIRES_OWNER_MAPPING"},
    "HairStylist": {"uc": None, "status": "SERVICE_ONLY"},
    "Herbalist": {"uc": "herbalist_vendor", "status": "PROPOSED_VENDOR"},
    "HolyMage": {"uc": "holy_mage_vendor", "status": "PROPOSED_VENDOR"},
    "InnKeeper": {"uc": "innkeeper_vendor", "status": "PROPOSED_VENDOR"},
    "IronWorker": {"uc": "blacksmith_vendor", "status": "PROPOSED_VENDOR"},
    "Jeweler": {"uc": "jeweler_vendor", "status": "PROPOSED_VENDOR",
                 "note": "buyback side relates to salvage_trader jewelry handling"},
    "KeeperOfChivalry": {"uc": None, "status": "SERVICE_ONLY"},
    "LeatherWorker": {"uc": "leather_worker_vendor", "status": "PROPOSED_VENDOR",
                        "note": "buyback -> fur_leather_trader"},
    "Mage": {"uc": "mage_vendor", "status": "PROPOSED_VENDOR",
              "note": "owner example: magery vendor with shard-configured requirements"},
    "Mapmaker": {"uc": "mapmaker_vendor", "status": "PROPOSED_VENDOR"},
    "Miller": {"uc": "miller_vendor", "status": "PROPOSED_VENDOR",
                "note": "grain retail overlaps grain_trader; decide vendor vs trader"},
    "Miner": {"uc": "ore_trader", "status": "PROPOSED_TRADER_ALIAS"},
    "Monk": {"uc": None, "status": "SERVICE_ONLY"},
    "Provisioner": {"uc": "provisioner_vendor", "status": "PROPOSED_VENDOR"},
    "Rancher": {"uc": "rancher_vendor", "status": "PROPOSED_VENDOR"},
    "Ranger": {"uc": "ranger_vendor", "status": "PROPOSED_VENDOR"},
    "RealEstateBroker": {"uc": "real_estate_vendor", "status": "REQUIRES_OWNER_MAPPING",
                           "note": "UltimaCraft has its own house-deed economy"},
    "Scribe": {"uc": "scribe_vendor", "status": "PROPOSED_VENDOR"},
    "Shipwright": {"uc": "shipwright_vendor", "status": "PROPOSED_VENDOR"},
    "StoneCrafter": {"uc": "stone_crafter_vendor", "status": "PROPOSED_VENDOR",
                       "note": "buyback -> stone_trader"},
    "Tailor": {"uc": "tailor_vendor", "status": "PROPOSED_VENDOR"},
    "Tanner": {"uc": "fur_leather_trader", "status": "PROPOSED_TRADER_ALIAS"},
    "TavernKeeper": {"uc": "tavernkeeper", "status": "EXISTING_MERCHANT"},
    "Thief": {"uc": None, "status": "REQUIRES_OWNER_MAPPING"},
    "Tinker": {"uc": "tinker_vendor", "status": "PROPOSED_VENDOR"},
    "Vagabond": {"uc": "jeweler_vendor", "status": "REQUIRES_OWNER_MAPPING",
                  "note": "RunUO gold/jewel buyer"},
    "VarietyDealer": {"uc": "variety_vendor", "status": "REQUIRES_OWNER_MAPPING"},
    "Veterinarian": {"uc": None, "status": "SERVICE_ONLY"},
    "Waiter": {"uc": "tavernkeeper", "status": "EXISTING_MERCHANT"},
    "Weaponsmith": {"uc": "weaponsmith_vendor", "status": "PROPOSED_VENDOR",
                      "note": "weapon buyback -> salvage/metal trader per owner rule"},
    "Weaver": {"uc": "weaver_vendor", "status": "PROPOSED_VENDOR"},
    "Banker": {"uc": "bank_teller", "status": "EXISTING_SERVICE_NPC"},
    "Healer": {"uc": "healer_vendor", "status": "PROPOSED_VENDOR",
                "note": "healing is service; catalog rows (bandages/potions) are retail"},
    "EvilHealer": {"uc": "healer_vendor", "status": "REQUIRES_OWNER_MAPPING"},
    "PricedHealer": {"uc": "healer_vendor", "status": "REQUIRES_OWNER_MAPPING"},
    "FortuneTeller": {"uc": None, "status": "SERVICE_ONLY"},
    "BardGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "BlacksmithGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "FisherGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "HealerGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "MageGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "MerchantGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "MinerGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "RangerGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "TailorGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "ThiefGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "TinkerGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "WarriorGuildmaster": {"uc": "guildmaster", "status": "EXISTING_SERVICE_NPC"},
    "BaseGuildmaster": {"uc": None, "status": "SERVICE_ONLY", "note": "abstract base"},
    "PlayerBarkeeper": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                          "note": "player-vendor subsystem, out of NPC scope"},
    "PlayerVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN"},
    "RentedVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN"},
    "SirHelper": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN", "note": "quest helper"},
    "Dryad": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN", "note": "quest mobile"},
    "Victoria": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN", "note": "quest mobile"},
    "BaseQuester": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN", "note": "quest base"},
    "BaseVendor": {"uc": None, "status": "SERVICE_ONLY", "note": "abstract base"},
    "BaseHealer": {"uc": None, "status": "SERVICE_ONLY", "note": "abstract base"},
    # RunUO faction subsystem — UltimaCraft has no faction system; whole family excluded.
    "BaseFactionVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                            "note": "RunUO faction subsystem"},
    "FactionBoardVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                             "note": "RunUO faction subsystem"},
    "FactionBottleVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                              "note": "RunUO faction subsystem"},
    "FactionHorseVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                             "note": "RunUO faction subsystem"},
    "FactionOreVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                           "note": "RunUO faction subsystem"},
    "FactionReagentVendor": {"uc": None, "status": "UNSUPPORTED_BY_DESIGN",
                               "note": "RunUO faction subsystem"},
    # New Haven ML-quest skill trainers that also carry SE retail catalogs.
    "Hamato": {"uc": None, "status": "REQUIRES_OWNER_MAPPING",
                "note": "ML-quest samurai trainer; sells SBSamurai gear"},
    "Ryuichi": {"uc": None, "status": "REQUIRES_OWNER_MAPPING",
                 "note": "ML-quest ninja trainer; sells SBNinja gear"},
    "IharaSoko": {"uc": None, "status": "SERVICE_ONLY",
                    "note": "Tokuno emissary; empty InitSBInfo"},
}

# Catalogs whose active rows are finished goods for form/denomination purposes.
FINISHED_GOODS_CATALOGS = {
    "SBAxeWeapon", "SBKnifeWeapon", "SBMaceWeapon", "SBPoleArmWeapon", "SBRangedWeapon",
    "SBSpearForkWeapon", "SBStavesWeapon", "SBSwordWeapon", "SBSEWeapons",
    "SBChainmailArmor", "SBHelmetArmor", "SBLeatherArmor", "SBSELeatherArmor",
    "SBMetalShields", "SBPlateArmor", "SBRingmailArmor", "SBStuddedArmor",
    "SBWoodenShields", "SBJewel", "SBVagabond", "SBWeaponSmith", "SBSEHats",
    "SBCobbler", "SBTailor", "SBBlacksmith", "SBSEArmor",
}

# Per-catalog proposed buyback trader targets. Every proposal has status
# PROPOSED_DEFAULT and requires owner review; catalogs absent here fall back
# to REQUIRES_OWNER_MAPPING per row.
CATALOG_BUYBACK_TRADER: dict[str, str] = {
    "SBBaker": "grain_trader",
    "SBMiller": "grain_trader",
    "SBFarmer": "produce_trader",
    "SBRancher": "produce_trader",
    "SBCook": "meat_trader",
    "SBSECook": "meat_trader",
    "SBButcher": "meat_trader",
    "SBFisherman": "fish_trader",
    "SBFurtrader": "fur_leather_trader",
    "SBTanner": "fur_leather_trader",
    "SBLeatherWorker": "fur_leather_trader",
    "SBCarpenter": "wood_trader",
    "SBSECarpenter": "wood_trader",
    "SBShipwright": "wood_trader",
    "SBBowyer": "wood_trader",
    "SBSEBowyer": "wood_trader",
    "SBStoneCrafter": "stone_trader",
    "SBMiner": "ore_trader",
    "SBSmithTools": "salvage_trader",
    "SBWeaponSmith": "salvage_trader",
    "SBAxeWeapon": "salvage_trader",
    "SBKnifeWeapon": "salvage_trader",
    "SBMaceWeapon": "salvage_trader",
    "SBPoleArmWeapon": "salvage_trader",
    "SBRangedWeapon": "salvage_trader",
    "SBSpearForkWeapon": "salvage_trader",
    "SBStavesWeapon": "salvage_trader",
    "SBSwordWeapon": "salvage_trader",
    "SBSEWeapons": "salvage_trader",
    "SBBlacksmith": "salvage_trader",
    "SBSEArmor": "salvage_trader",
    "SBChainmailArmor": "salvage_trader",
    "SBHelmetArmor": "salvage_trader",
    "SBLeatherArmor": "fur_leather_trader",
    "SBSELeatherArmor": "fur_leather_trader",
    "SBMetalShields": "salvage_trader",
    "SBPlateArmor": "salvage_trader",
    "SBRingmailArmor": "salvage_trader",
    "SBStuddedArmor": "fur_leather_trader",
    "SBWoodenShields": "wood_trader",
    "SBJewel": "salvage_trader",
    "SBVagabond": "salvage_trader",
    "SBTavernKeeper": "alcohol_trader",
    "SBBarkeeper": "alcohol_trader",
    "SBInnKeeper": "alcohol_trader",
    "SBWaiter": "alcohol_trader",
    "SBBeekeeper": "produce_trader",
    "SBSEFood": "meat_trader",
    "SBSEHats": "REQUIRES_NEW_TRADER:textile_trader",
    "SBTailor": "REQUIRES_NEW_TRADER:textile_trader",
    "SBWeaver": "REQUIRES_NEW_TRADER:textile_trader",
    "SBCobbler": "REQUIRES_NEW_TRADER:textile_trader",
    "SBAlchemist": "REQUIRES_NEW_TRADER:reagent_trader",
    "SBHerbalist": "REQUIRES_NEW_TRADER:reagent_trader",
    "SBMage": "REQUIRES_NEW_TRADER:reagent_trader",
    "SBHolyMage": "REQUIRES_NEW_TRADER:reagent_trader",
    "SBScribe": "REQUIRES_NEW_TRADER:scribe_trader",
    "SBTinker": "salvage_trader",
    "SBGlassblower": "REQUIRES_NEW_TRADER:glass_trader",
    "SBProvisioner": "REQUIRES_NEW_TRADER:provision_trader",
    "SBHealer": "REQUIRES_NEW_TRADER:reagent_trader",
    "SBBard": "REQUIRES_NEW_TRADER:provision_trader",
    "SBMapmaker": "REQUIRES_NEW_TRADER:scribe_trader",
    "SBRanger": "REQUIRES_NEW_TRADER:provision_trader",
    "SBThief": "REQUIRES_OWNER_MAPPING",
    "SBVarietyDealer": "REQUIRES_OWNER_MAPPING",
}

# Raw/processed/finished heuristic by RunUO type-name keywords, used ONLY to
# propose a default payout denomination tier (owner review required).
RAW_KEYWORDS = ("Log", "Ore", "Hides", "Wool", "Cotton", "Flax", "Feather", "Egg",
                 "RawFish", "Fish", "Wheat", "Carrot", "Cabbage", "Onion", "Lettuce",
                 "Pumpkin", "Squash", "Melon", "Grapes", "Peach", "Pear", "Apple",
                 "Lemon", "Lime", "Banana", "Coconut", "Gourd", "Hay", "Bone")
PROCESSED_KEYWORDS = ("Ingot", "Board", "Leather", "Cloth", "Flour", "Bread", "Cheese",
                       "Butter", "Thread", "Yarn", "Bandage", "Potion", "Oil", "Wine",
                       "Ale", "Liquor", "Cider", "Steak", "Leg", "Ribs", "Bacon", "Ham",
                       "Sausage", "Cooked", "Pie", "Cake", "Muffins", "Cookies", "Pizza")


def classify_form(type_name: str) -> str:
    for kw in PROCESSED_KEYWORDS:
        if kw.lower() in type_name.lower():
            return "processed"
    for kw in RAW_KEYWORDS:
        if kw.lower() in type_name.lower():
            return "raw"
    return "unclassified"


def payout_denomination_proposal(form: str, price: int | str) -> str:
    if isinstance(price, int) and price >= 500:
        return "gold"
    if form in ("processed", "finished"):
        return "silver"
    if form == "raw":
        return "copper"
    return "unresolved"


def snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def load_uc_items(repo: Path) -> set[str]:
    """Item-id universe: ItemRegistry (multiline-tolerant) + the dynamically
    registered Blacksmithing craftable outputs (BlacksmithItemRegistry reads
    data/britannia_mod/blacksmithing/craftables.json at runtime)."""
    base = repo / "src/main/java/com/seggellion/britannia_mod"
    items = set(re.findall(r'register\s*\(\s*"([a-z0-9_]+)"', read(base / "registry/ItemRegistry.java")))
    craftables = repo / "src/main/resources/data/britannia_mod/blacksmithing/craftables.json"
    if craftables.exists():
        data = json.loads(craftables.read_text(encoding="utf-8-sig"))
        for recipe in data.get("recipes", []):
            out = recipe.get("output", "")
            if out.startswith("britannia_mod:"):
                items.add(out.split(":", 1)[1])
    return items


# A small closed set of vanilla item ids that are plausible direct matches.
VANILLA_ITEMS = {
    "bread", "cake", "cookie", "egg", "leather", "bone", "book", "map", "saddle",
    "shears", "bucket", "apple", "carrot", "potato", "pumpkin", "melon", "wheat",
    "feather", "string", "arrow", "bow", "shield", "torch", "candle", "chest",
}


# Curated aliases: RunUO snake name -> UltimaCraft id (only applied when the
# target actually exists in the loaded item universe).
ITEM_ALIASES = {
    "mandrake_root": "mandrake",
    "bloodmoss": "blood_moss",
    "sulfurous_ash": "sulphurous_ash",
    "lamb_leg": "leg_of_lamb",
    "cheese_wheel": "cheese",
    "cheese_wedge": "cheese",
    "sack_flour": "flour",
    "bread_loaf": "bread",
    "pike": "pike_polearm",
    "short_music_stand": "music_stand",
    "bass_drum": "drums",
    "hammer": "black_smiths_hammer",
    "smith_hammer": "black_smiths_hammer",
    "interior_decorator": "interior_decorator_tool",
}

# Rows that belong to game systems the owner has not designed yet — surfaced
# as OWNER_REVIEW rather than silently invented as items.
MAGIC_SYSTEM_RE = re.compile(
    r"Potion$|Scroll$|Spellbook|Runebook|RecallRune|Wand$|NecromancerSpellbook|MagicWiz"
)
UNSUPPORTED_RE = re.compile(
    r"Contract|FactionExplosionTrap|FactionGasTrap|FactionSawTrap|FactionSpikeTrap"
    r"|Silver$|BroadcastCrystal|ReceiverCrystal"
)
SERVICE_RE = re.compile(r"^SpecialBeardDye$|^SpecialHairDye$|^HairDye$|^VacationWafer$")


def map_buy_row_item(type_name: str, uc_items: set[str], catalog: str) -> tuple[str | None, str, str | None]:
    """Returns (uc_item_id, mapping_status, note)."""
    if catalog.startswith("SBFaction"):
        return None, "UNSUPPORTED", "RunUO faction subsystem"
    if UNSUPPORTED_RE.search(type_name):
        return None, "UNSUPPORTED", "player-vendor/faction/communication subsystem"
    if SERVICE_RE.search(type_name):
        return None, "SERVICE_OR_MOBILE", "appearance/service consumable"
    if MAGIC_SYSTEM_RE.search(type_name):
        return None, "OWNER_REVIEW", "magic/alchemy consumable system not yet designed"
    if type_name.endswith("Deed"):
        return None, "OWNER_REVIEW", "deed economy decision (UltimaCraft house-deed system exists)"

    s = snake(type_name)
    exact = [s, ITEM_ALIASES.get(s, "")]
    for c in exact:
        if c and c in uc_items:
            status = "DIRECT_MATCH" if c == s else "LIKELY_MATCH"
            return f"britannia_mod:{c}", status, None
    if s in VANILLA_ITEMS:
        return f"minecraft:{s}", "DIRECT_MATCH", None
    near = [s + "s", s[:-1] if s.endswith("s") else "", s.replace("_loaf", "")]
    for c in near:
        if c and c in uc_items:
            return f"britannia_mod:{c}", "LIKELY_MATCH", None
        if c and c in VANILLA_ITEMS:
            return f"minecraft:{c}", "LIKELY_MATCH", None
    # conservative unique-substring candidate
    subs = [i for i in uc_items if (s in i or i in s) and min(len(s), len(i)) >= 5
            and abs(len(i) - len(s)) <= 8]
    if len(subs) == 1:
        return f"britannia_mod:{subs[0]}", "LIKELY_MATCH", None
    if len(subs) > 1:
        return None, "OWNER_REVIEW", f"multiple candidates: {sorted(subs)[:4]}"
    return None, "MISSING_ITEM", None


# OQ-4: per-catalog economic class for Trader payout denomination policy.
# Tiers (owner policy proposal): raw_resource -> copper; everything processed,
# crafted, consumable, or finished -> silver; any row >= 500 GP -> gold.
CATALOG_ECON_CLASS: dict[str, str] = {
    # provisions / prepared food / drink
    "SBProvisioner": "provisions", "SBInnKeeper": "provisions", "SBTavernKeeper": "provisions",
    "SBBarkeeper": "provisions", "SBWaiter": "provisions", "SBCook": "provisions",
    "SBSECook": "provisions", "SBBaker": "provisions", "SBFarmer": "raw_produce",
    "SBRancher": "raw_produce", "SBButcher": "provisions", "SBBeekeeper": "provisions",
    "SBFisherman": "raw_produce", "SBBard": "provisions", "SBRanger": "provisions",
    # magic / scribe supplies
    "SBMage": "magic_supplies", "SBAlchemist": "magic_supplies", "SBHerbalist": "magic_supplies",
    "SBHolyMage": "magic_supplies", "SBHealer": "magic_supplies", "SBKeeperOfChivalry": "magic_supplies",
    "SBScribe": "scribe_supplies", "SBMapmaker": "scribe_supplies", "SBFortuneTeller": "magic_supplies",
    "SBMonk": "provisions", "SBHairStylist": "service_goods", "SBVeterinarian": "magic_supplies",
    # tools / components / craft outputs
    "SBTinker": "tools_components", "SBSmithTools": "tools_components",
    "SBGlassblower": "glassware", "SBArchitect": "tools_components",
    "SBCarpenter": "wood_goods", "SBSECarpenter": "wood_goods", "SBShipwright": "wood_goods",
    "SBBowyer": "wood_goods", "SBSEBowyer": "wood_goods", "SBStoneCrafter": "stone_goods",
    "SBMiner": "raw_resource", "SBMiller": "processed_food",
    # textiles / leather
    "SBTailor": "textile_goods", "SBWeaver": "textile_goods", "SBCobbler": "textile_goods",
    "SBSEHats": "textile_goods", "SBTanner": "leather_goods", "SBLeatherWorker": "leather_goods",
    "SBFurtrader": "raw_resource",
    # equipment
    "SBWeaponSmith": "finished_equipment", "SBAxeWeapon": "finished_equipment",
    "SBKnifeWeapon": "finished_equipment", "SBMaceWeapon": "finished_equipment",
    "SBPoleArmWeapon": "finished_equipment", "SBRangedWeapon": "finished_equipment",
    "SBSpearForkWeapon": "finished_equipment", "SBStavesWeapon": "finished_equipment",
    "SBSwordWeapon": "finished_equipment", "SBSEWeapons": "finished_equipment",
    "SBChainmailArmor": "finished_equipment", "SBHelmetArmor": "finished_equipment",
    "SBBlacksmith": "finished_equipment", "SBSEArmor": "finished_equipment",
    "SBSEFood": "provisions",
    "SBLeatherArmor": "finished_equipment", "SBSELeatherArmor": "finished_equipment",
    "SBMetalShields": "finished_equipment", "SBPlateArmor": "finished_equipment",
    "SBRingmailArmor": "finished_equipment", "SBStuddedArmor": "finished_equipment",
    "SBWoodenShields": "finished_equipment", "SBSamurai": "finished_equipment",
    "SBNinja": "finished_equipment",
    # luxury
    "SBJewel": "jewelry_gems", "SBVagabond": "jewelry_gems",
    # owner-review stock
    "SBThief": None, "SBVarietyDealer": None, "SBAnimalTrainer": None,
    "SBHouseDeed": None, "SBRealEstateBroker": None, "SBBanker": None,
    "SBPlayerBarkeeper": None, "SBVagrant": None,
}

ECON_CLASS_DENOM = {
    "raw_resource": "copper", "raw_produce": "copper",
    "provisions": "silver", "processed_food": "silver", "magic_supplies": "silver",
    "scribe_supplies": "silver", "tools_components": "silver", "glassware": "silver",
    "wood_goods": "silver", "stone_goods": "silver", "textile_goods": "silver",
    "leather_goods": "silver", "finished_equipment": "silver", "service_goods": "silver",
    "jewelry_gems": "gold",
}


VARIABLE_MATERIAL_CATALOGS = {
    "SBAxeWeapon", "SBKnifeWeapon", "SBMaceWeapon", "SBPoleArmWeapon", "SBRangedWeapon",
    "SBSpearForkWeapon", "SBStavesWeapon", "SBSwordWeapon", "SBSEWeapons",
    "SBChainmailArmor", "SBHelmetArmor", "SBMetalShields", "SBPlateArmor",
    "SBRingmailArmor", "SBWeaponSmith", "SBBlacksmith", "SBSEArmor",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runuo", default="C:/projects/runuo-reference")
    ap.add_argument("--repo", default=str(Path(__file__).resolve().parents[3]))
    args = ap.parse_args()

    runuo = Path(args.runuo)
    repo = Path(args.repo)
    out_path = Path(__file__).resolve().parents[1] / "runuo_ultimacraft_mapping.json"

    head = (runuo / ".git").exists()
    scripts = runuo / "Scripts"
    if not scripts.exists():
        print(f"RunUO Scripts directory not found under {runuo}", file=sys.stderr)
        return 1

    uc_items = load_uc_items(repo)

    catalogs: dict[str, dict] = {}
    vendors: list[dict] = []
    for cs in sorted(scripts.rglob("*.cs")):
        text_probe = read(cs)
        if re.search(r"class\s+\w+\s*:\s*SBInfo\b", text_probe):
            for cat in parse_sb_file(cs, runuo):
                catalogs[cat["catalog"]] = cat
        if "InitSBInfo" in text_probe:
            vendors.extend(parse_vendor_file(cs, runuo))

    # Vendor mapping decoration
    for v in vendors:
        m = VENDOR_MAP.get(v["vendor_class"], {"uc": None, "status": "REQUIRES_OWNER_MAPPING"})
        v["uc_key"] = m.get("uc")
        v["mapping_status"] = m["status"]
        if m.get("note"):
            v["mapping_note"] = m["note"]
        v["vendor_can_buy_from_player"] = False  # owner rule: buyback via Traders only

    # Row mapping decoration
    for cat in catalogs.values():
        variable_material = cat["catalog"] in VARIABLE_MATERIAL_CATALOGS
        for row in cat["buy_rows"]:
            t = row.get("type")
            if row["buy_info_class"] == "AnimalBuyInfo":
                row["mapping_status"] = "SERVICE_OR_MOBILE"
                row["fulfillment"] = "MOBILE"
                row["uc_item_id"] = None
            elif row["buy_info_class"] == "PresetMapBuyInfo":
                row["mapping_status"] = "SERVICE_OR_MOBILE"
                row["fulfillment"] = "PRESET_MAP"
                row["uc_item_id"] = None
            elif t:
                uc_id, status, note = map_buy_row_item(t, uc_items, cat["catalog"])
                row["uc_item_id"] = uc_id
                if note:
                    row["mapping_note"] = note
                if variable_material:
                    row["mapping_status"] = "VARIABLE_MATERIAL_PRODUCT"
                    row["item_match"] = status  # keep the match verdict alongside
                else:
                    row["mapping_status"] = status
            else:
                row["mapping_status"] = "OWNER_REVIEW"
                row["mapping_note"] = "row without a parsed type"
            row["denomination"] = "gold"          # owner rule: RunUO GP -> Gold
            row["runuo_gp_price"] = row.get("price")
            row["requirements_status"] = "unresolved"  # Rails Product seeding, M3+
        target = CATALOG_BUYBACK_TRADER.get(cat["catalog"])
        finished_default = cat["catalog"] in FINISHED_GOODS_CATALOGS
        for row in cat["sell_rows"]:
            form = classify_form(row["type"])
            if form == "unclassified" and finished_default:
                form = "finished"
            row["form"] = form
            if target is None:
                row["target_trader"] = None
                row["buyback_status"] = "REQUIRES_OWNER_MAPPING"
            elif target.startswith("REQUIRES_NEW_TRADER:"):
                row["target_trader"] = target.split(":", 1)[1]
                row["buyback_status"] = "REQUIRES_NEW_TRADER"
            elif target == "REQUIRES_OWNER_MAPPING":
                row["target_trader"] = None
                row["buyback_status"] = "REQUIRES_OWNER_MAPPING"
            else:
                row["target_trader"] = target
                row["buyback_status"] = "PROPOSED_DEFAULT"
            row["valuation_strategy"] = (
                "salvage_material_quality_value" if row.get("target_trader") == "salvage_trader"
                else "current_commodity_value" if form != "unclassified"
                else "unresolved"
            )
            econ_class = CATALOG_ECON_CLASS.get(cat["catalog"])
            row["economic_class"] = econ_class
            proposal = payout_denomination_proposal(form, row.get("price"))
            if proposal == "unresolved" and econ_class:
                # OQ-4 class policy: class default, high-GP override to gold.
                if isinstance(row.get("price"), int) and row["price"] >= 500:
                    proposal = "gold"
                else:
                    proposal = ECON_CLASS_DENOM[econ_class]
            row["payout_denomination_proposal"] = proposal
            row["payout_denomination_status"] = (
                "PROPOSED_CLASS_POLICY" if proposal != "unresolved"
                else "OWNER_EXCEPTION"
            )

    result = {
        "schema_version": "1.0.0",
        "generated_by": "docs/vendor-trader-economy/tools/parse_runuo_vendors.py",
        "source": {
            "repository": "runuo/runuo",
            "pinned_commit": PINNED_COMMIT,
            "local_path": str(runuo),
            "git_checkout_present": head,
        },
        "owner_rules": {
            "runuo_gp_calibration": "1 RunUO GP = 1 UltimaCraft Gold",
            "vendor_retail_denomination_default": "gold",
            "canonical_ratios": {"copper": 1, "silver": 100, "gold": 10000},
            "vendors_never_buy_from_players": True,
            "settlement": "exact denomination, no conversion, no mixed baskets",
        },
        "uc_reference": {
            "traders": UC_TRADERS,
            "merchants": UC_MERCHANTS,
            "item_registry_count": len(uc_items),
        },
        "vendors": sorted(vendors, key=lambda v: v["vendor_class"]),
        "catalogs": dict(sorted(catalogs.items())),
    }

    out_path.write_text(json.dumps(result, indent=1), encoding="utf-8", newline="\n")

    n_buy = sum(len(c["buy_rows"]) for c in catalogs.values())
    n_sell = sum(len(c["sell_rows"]) for c in catalogs.values())
    n_cond = sum(
        1
        for c in catalogs.values()
        for r in c["buy_rows"] + c["sell_rows"]
        if r.get("condition")
    )
    n_inactive = sum(len(c["inactive_source_rows"]) for c in catalogs.values())
    print(f"vendors={len(vendors)} catalogs={len(catalogs)} buy_rows={n_buy} "
          f"sell_rows={n_sell} conditional_rows={n_cond} inactive_rows={n_inactive}")
    print(f"wrote {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
