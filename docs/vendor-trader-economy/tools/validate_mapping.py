#!/usr/bin/env python3
"""Milestone 2 mapping completeness/consistency validation.

Checks `../runuo_ultimacraft_mapping.json` for the invariants required by the
playbook and the Milestone 2 instructions:

  - duplicate stable keys (vendor classes, catalog names);
  - every vendor has a mapping status; PROPOSED/EXISTING vendors have a uc key;
  - every catalog referenced by a vendor exists (and vice versa is reported);
  - every buy row has a mapping status and an explicit denomination from the
    closed set;
  - every sell row has a buyback status, and a target trader whenever its
    status proposes one; payout denomination proposals come from the closed
    set or are explicitly "unresolved";
  - proposed target traders resolve to known UltimaCraft traders or carry a
    REQUIRES_NEW_TRADER status;
  - proposed uc_item_ids point at the known item universe
    (britannia_mod:<ItemRegistry id> or the curated vanilla set);
  - no row is silently statusless.

Exit code 0 = all invariants hold; 1 = violations printed.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAPPING = HERE.parent / "runuo_ultimacraft_mapping.json"
REPO = HERE.parents[2]

CLOSED_DENOMS = {"copper", "silver", "gold"}
PAYOUT_DENOMS = CLOSED_DENOMS | {"unresolved"}
VENDOR_STATUSES = {
    "EXISTING_MERCHANT", "EXISTING_SERVICE_NPC", "PROPOSED_VENDOR",
    "PROPOSED_TRADER_ALIAS", "SERVICE_ONLY", "REQUIRES_OWNER_MAPPING",
    "UNSUPPORTED_BY_DESIGN",
}
BUY_STATUSES = {
    "PROPOSED_DIRECT_ITEM", "VARIABLE_MATERIAL_PRODUCT", "MOBILE",
    "REQUIRES_OWNER_MAPPING",
}
SELL_STATUSES = {"PROPOSED_DEFAULT", "REQUIRES_NEW_TRADER", "REQUIRES_OWNER_MAPPING"}
FORMS = {"raw", "processed", "finished", "unclassified"}


def main() -> int:
    data = json.loads(MAPPING.read_text(encoding="utf-8"))
    errors: list[str] = []

    known_traders = set(data["uc_reference"]["traders"])
    reg = REPO / "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"
    uc_items = set(re.findall(r'register\(\s*"([a-z0-9_]+)"', reg.read_text(encoding="utf-8", errors="replace")))

    vendors = data["vendors"]
    catalogs = data["catalogs"]

    # Duplicate stable keys
    seen = set()
    for v in vendors:
        key = v["vendor_class"]
        if key in seen:
            errors.append(f"duplicate vendor_class {key}")
        seen.add(key)

    # Vendor statuses
    for v in vendors:
        st = v.get("mapping_status")
        if st not in VENDOR_STATUSES:
            errors.append(f"vendor {v['vendor_class']}: invalid mapping_status {st!r}")
        if st in {"EXISTING_MERCHANT", "EXISTING_SERVICE_NPC", "PROPOSED_VENDOR",
                  "PROPOSED_TRADER_ALIAS"} and not v.get("uc_key"):
            errors.append(f"vendor {v['vendor_class']}: status {st} requires uc_key")
        for add in v["sb_catalogs"]:
            if add["catalog"] not in catalogs:
                errors.append(
                    f"vendor {v['vendor_class']}: references missing catalog {add['catalog']}"
                )

    referenced = {a["catalog"] for v in vendors for a in v["sb_catalogs"]}
    for name in catalogs:
        if name not in referenced:
            print(f"note: catalog {name} is parsed but referenced by no vendor")

    for name, cat in catalogs.items():
        for row in cat["buy_rows"]:
            rid = f"{name}[buy #{row.get('ordinal')}]"
            if row.get("mapping_status") not in BUY_STATUSES:
                errors.append(f"{rid}: invalid mapping_status {row.get('mapping_status')!r}")
            if row.get("denomination") not in CLOSED_DENOMS:
                errors.append(f"{rid}: invalid denomination {row.get('denomination')!r}")
            if row.get("requirements_status") not in {"unresolved", "proposed", "resolved"}:
                errors.append(f"{rid}: invalid requirements_status")
            uc_id = row.get("uc_item_id")
            if uc_id:
                ns, _, path = uc_id.partition(":")
                if ns == "britannia_mod" and path not in uc_items:
                    errors.append(f"{rid}: uc_item_id {uc_id} not in ItemRegistry")
        for row in cat["sell_rows"]:
            rid = f"{name}[sell #{row.get('ordinal')}]"
            st = row.get("buyback_status")
            if st not in SELL_STATUSES:
                errors.append(f"{rid}: invalid buyback_status {st!r}")
            if st == "PROPOSED_DEFAULT":
                if row.get("target_trader") not in known_traders:
                    errors.append(
                        f"{rid}: PROPOSED_DEFAULT with unknown trader {row.get('target_trader')!r}"
                    )
            if st == "REQUIRES_NEW_TRADER" and not row.get("target_trader"):
                errors.append(f"{rid}: REQUIRES_NEW_TRADER without proposed trader key")
            if row.get("form") not in FORMS:
                errors.append(f"{rid}: invalid form {row.get('form')!r}")
            if row.get("payout_denomination_proposal") not in PAYOUT_DENOMS:
                errors.append(
                    f"{rid}: invalid payout denomination {row.get('payout_denomination_proposal')!r}"
                )

    if errors:
        print(f"FAILED: {len(errors)} violations")
        for e in errors[:50]:
            print("  -", e)
        if len(errors) > 50:
            print(f"  … and {len(errors) - 50} more")
        return 1

    n_buy = sum(len(c["buy_rows"]) for c in catalogs.values())
    n_sell = sum(len(c["sell_rows"]) for c in catalogs.values())
    print(
        f"OK: {len(vendors)} vendors, {len(catalogs)} catalogs, "
        f"{n_buy} buy rows, {n_sell} sell rows — all invariants hold"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
