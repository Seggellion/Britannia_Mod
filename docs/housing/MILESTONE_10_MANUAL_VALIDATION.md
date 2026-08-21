# Milestone 10 — manual validation checklist

The playbook's Milestone 10 asks for the nine steps to be walked **in a running game**. Everything
that can be proven without a human at a client has been, and is listed under each step as the
automated evidence. What remains needs a live shard: a Minecraft client, a server with working
Rails credentials, and somebody to click things.

Run this against a test shard with Rails reachable. Record a pass or a surprise for each line —
the playbook's third exit criterion is that surprises are written down, not normalised away.

## Before you start

```
/manageddeposit types
```

should list `britannia_mod:clay_deposit` and `britannia_mod:silica_sand_deposit`. Then put a small
deterministic set of resources somewhere reachable — a riverbank is the right sort of ground:

```
/manageddeposit place clay ~ ~-1 ~
/manageddeposit place silica ~ ~-1 ~2
/setblock ~ ~-1 ~4 britannia_mod:sandstone_deposit
```

Ordinary stone, calcite and iron ore are already in the ground; grain is already farmable. Nothing
else needs seeding. `/manageddeposit inspect <pos>` reports what stands there and, once worked, how
long until it returns.

---

## Step 1 — a city meets the economic conditions and staffs an Architect

Automated: `ArchitectSpawnGatingGameTests` (post registration, migration, no local threshold),
Rails `architect_staffing_integration_test` and `architect_spawn_gate_test` (eligibility, the
43,800-gold treasury floor, exactness).

Manual:
- [ ] A city below the floor holds no Architect.
- [ ] Raise it over the floor; the Architect appears as `britannia_mod:architect`.
- [ ] The NPC is stamped with `economic_npc_type_key = architect_vendor`.
- [ ] Close the assignment in Rails; the Architect leaves on Rails' dwell semantics, not a local timer.

## Step 2 — the player sees villa, patio and keep priced in gold

Automated: `ArchitectDeedVendorGameTests` (economic catalogue used when stamped, legacy not used,
unavailable rows dropped, gold denomination, seven-figure prices exact).

Manual:
- [ ] All ten deeds render with the right names.
- [ ] 43,800 / 136,500 / 152,800 / 665,200 / **1,022,800** display in full, in gold, untruncated.
- [ ] A deed the city cannot build is absent rather than shown broken.

## Step 3 — buys a villa deed; coin debited, treasury credited, commodities consumed

Automated: Rails `architect_deed_purchase_test`, `deed_recipe_contract_test`.

Manual:
- [ ] Buy the 43,800 small house: exact debit, exact treasury credit, recipe consumed.
- [ ] Buy the Two-Story Villa: same, and the sandstone / plaster / raw glass / thatch rows all drop.
- [ ] Buy the **Castle** — the seven-figure price is the strongest regression.
- [ ] Too little gold: no charge, no materials consumed, no deed.
- [ ] Too little of one material: no charge, no partial consumption, no deed.
- [ ] Exactly one deed item arrives, `britannia_mod:<structure>_deed`, no `deed_id`, not blessed.

## Step 4 — places the villa where they aimed, in a non-default rotation

Automated: `HouseEntranceAnchorTest` walks all ten styles × all four rotations — aim point, no
vertical shift, footprint spans (including the Keep's non-square 26×25), door inside its own box.

Manual:
- [ ] Place at least one house at each of the four facings; it lands on the aimed block.
- [ ] The Keep rotates without a clipped corner.
- [ ] Nothing sinks or floats.

## Step 5 — every door, exterior and interior, locks and unlocks with the key

Automated: `ShippedHouseDoorOwnershipTest` resolves all **23** shipped doors across the ten houses
in all four rotations through the real `HouseUtil.enclosingStructure` chain the lock uses, and
checks owner-yes / stranger-no at each. `HouseDoorLotBindingGameTests` and
`HouseDoorRedstoneGameTests` turn real keys in real locks at representative doors, including a deep
castle door and a metal double.

Manual, on the Villa (3 doors), Patio (4) and Castle (8, four doubles):
- [ ] Public house: doors open for anybody.
- [ ] Make it private: every door locks — both leaves of every double.
- [ ] The owner's key opens every one of them, including the ones deep inside.
- [ ] A second player is refused at every one of them.
- [ ] A button or pressure plate does not open a locked door.

## Step 6 — breaks a wall, cannot break a foundation

Note: the playbook says "enters creative". That was superseded by the M5 owner decision — build
rights are lent **without** creative mode.

Automated: `HouseOwnerBuildRightsGameTests` (owner breaks and places, non-owner cannot, perimeter
foundation refused to everyone, lot block refused, no game-mode change, proximity grants nothing).

Manual:
- [ ] Inside your own house you can break and place ordinary blocks.
- [ ] Your game mode still reads Adventure; no creative inventory, no flight, no instabuild.
- [ ] A perimeter foundation block refuses you, with a legible message.
- [ ] Another player can do none of it.

## Step 7 — digs a basement

Automated: `HouseOwnerBuildRightsGameTests.anownercutsthroughtheirowninteriorfloor`,
`HouseRegionResolutionTest.theBasementBelowAHouseStillBelongsToIt` (the `minY - 10` boundary).

Manual:
- [ ] Cut through the interior floor foundation (`brick_foundation_spruce` in a small house,
      `wooden_board_floor_foundation` where laid).
- [ ] Dig down; rights hold to ten blocks below the building.
- [ ] One block past that, the world's own rules apply again.

## Step 8 — the server restarts

Automated: `StructureRegionRehydrationGameTests` (a restored region gives back the door lock and
the right to dig, and still knows whose it is).

Manual — this is the step with the least automated proxy, so do it carefully:
- [ ] Stop and restart the server.
- [ ] The doors still unlock with the same key.
- [ ] The basement is still yours to dig.
- [ ] The region is intact — walk the boundary and check rights start and stop where they did.
- [ ] Ownership survived; the house is not orphaned.

## Step 9 — a city stripped of materials no longer offers the deed, legibly

Automated: `ArchitectDeedVendorGameTests` (unavailable rows dropped, deeds independently
available), Rails `ProductAvailability`.

Manual:
- [ ] Drain one commodity a house needs; that deed disappears from the catalogue.
- [ ] Other deeds are unaffected.
- [ ] The reason a player is given is legible.

---

## Also worth walking while you are there

- [ ] Sell one of each material to its live trader and watch the city commodity rise on the exact
      key: wood → Wood Trader; stone, common stone, sandstone, plaster, clay, raw glass → Stone
      Trader; thatch → Wood Trader; iron ingot → Salvager.
- [ ] Offer silica sand to the Stone Trader — it must be refused. Fire it first.
- [ ] Re-deed an Architect-bought house: an ordinary deed comes back, never a blessed one.
- [ ] Re-deed a website deed: the blessed identity comes back.
- [ ] After re-deeding, doors no longer resolve, build rights are gone, and the region is released.
- [ ] Work a clay bed and a silica bed; both go empty and both return later.
- [ ] Two houses at the same X/Z in different dimensions do not see each other.

## Accepted state, not defects

Placeholder textures on `silica_sand_deposit`, `sandstone_deposit`, `silica_sand`, `raw_glass` and
`plaster`; the Castle's stray `stone_foundation` at (3,10,26); the Keep's vanilla buttons and
plates; placement limited to grass and sand; processed-material pricing; the 300-second held-shop
window; resource abundance and the six-hour regeneration interval.
