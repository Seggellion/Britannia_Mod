# Patch 18 — housing and Grabby Hands production smoke test

Sixteen checks for one person on the production or test shard. Roughly 20 minutes.

Everything here is either impossible to prove headlessly (a real mouse, a real client, real terrain)
or worth confirming once on real data. Everything else is covered by
`./gradlew test` and `./gradlew runGameTestServer`.

## Before you start

- Two accounts if you can. Step 7 needs a second player; the rest do not.
- A deed for each house type you intend to test (step 11).
- Stand **outside any city** unless a step says otherwise, in **Adventure mode** (`/gamemode adventure`).
- Have an ordinary pickaxe and an empty inventory slot or two.

Record a result for each. Any **FAIL** is a release blocker unless noted.

---

## Grabby Hands

### 1. Place and recover a wine bottle

1. Hold a wine bottle with known label/vintage. Right-click the ground.
2. Empty **both** hands (main and off).
3. **Sneak + right-click** the bottle.

**Expect:** the bottle is placed facing you; on pickup it vanishes, one bottle enters your
inventory, nothing drops on the floor, and its vintner/vintage/quality are unchanged.

**Also try:** sneak + right-click a bottle or chair that was part of the map, not placed by you.
**Expect:** it stays put and you are told *"That was not put there by a player. It cannot be moved."*
A silent nothing here is a FAIL — silent refusals are what made this look broken in the first place.

☐ pass ☐ fail — notes: ________________________________

### 1b. Place and recover a loose item

Right-click a table or floor while holding cheese (or any food/instrument). Then empty both hands
and sneak + right-click it.

**Expect:** the cheese sits on the surface, and comes back to you. This is the case that was
completely broken: the host block was never enrolled, so loose items could be set down and never
picked up.

☐ pass ☐ fail — notes: ________________________________

---

## House build rights

### 2. Walk into your own house from Adventure mode

Approach your property from an Adventure-mode city or the open world and step inside.

**Expect:** no message, no game-mode change, nothing visible. Your game mode still reads Adventure.
The change is only that step 3 now works.

☐ pass ☐ fail — notes: ________________________________

### 3. Break an ordinary block with a tool, inside your house

Place a block of your own inside, then break it with a pickaxe.

**Expect:** it breaks and drops normally, at normal speed, costing durability. **This is the step
that was broken in production** — the block would not break at all, with nothing in any log.

☐ pass ☐ fail — notes: ________________________________

### 4. Try the same bare-handed

Empty your main hand and swing at the same kind of block inside your house.

**Expect:** the block does **not** break, and you are told
*"You need a tool to take that apart. Bare hands will not do it."* The block must still be standing.

☐ pass ☐ fail — notes: ________________________________

### 5. Break a chest with several items in it

Put 3–4 distinguishable stacks in a Britannia chest inside your house. Break it with a tool.

**Expect:** the chest arrives in your inventory as an item; **nothing spills on the floor**; and when
you place it again the contents are exactly what you put in — same stacks, same counts, no extras.

Count the items before and after. A duplicate here is a serious FAIL.

Before: ______________________  After: ______________________

☐ pass ☐ fail — notes: ________________________________

### 6. Remove a metadata-bearing object

Place a wine bottle with a distinctive vintage inside your house, then break it with a tool.

**Expect:** the bottle comes back as an item with its vintner, vintage and quality intact — not a
blank bottle, and not nothing at all.

☐ pass ☐ fail — notes: ________________________________

### 7. Have another player try to modify your property

Second player, in Adventure mode, standing inside your house: ask them to break a block, place a
block, break the perimeter, and break a chest.

**Expect:** all four refused, with *"This is not your house."*

☐ pass ☐ fail — notes: ________________________________

### 8. Walk out of the property

Step outside your house and try to break a block in the open world with the same tool.

**Expect:** refused — *"You can only build inside your own house."* Ordinary world protection is
back. Also try reaching **from inside the doorway** at a block outside: also refused.

☐ pass ☐ fail — notes: ________________________________

---

## Deed and rotation

### 9. Left-click rotates the ghost

Hold a house deed and aim at open ground. **Left-click** four times.

**Expect:** the ghost turns 90° each time — 90, 180, 270, back to 0 — and the message reads
*"Rotated to N°"* once per click. Exactly one step per click.

☐ pass ☐ fail — notes: ________________________________

### 10. Right-click never rotates

Aim at ground where a house **cannot** be placed (against a cliff, or inside a city) so the
placement is refused, and **right-click ten times**.

**Expect:** the ghost's facing does **not change once**. This is the defect that shipped: both
buttons rotated, and the controls were indistinguishable.

Then aim at valid ground, rotate to a facing you can recognise, and right-click to place.

**Expect:** the house is built facing exactly the way the ghost showed.

☐ ghost never rotated on right-click ☐ it rotated — **FAIL**

Facing chosen: __________  Facing built: __________

---

## Placement

### 11. Place each historically problematic house type

Place, at minimum: **two-story villa**, **large patio**, **stone keep**. Then any small house, and
the castle if you have the deed.

**Expect:** each places, and step 12 works on each.

☐ villa ☐ patio ☐ keep ☐ small ☐ castle — notes: ________________________________

### 12. Open the house sign

Right-click the house sign on each house you placed.

**Expect:** the house management screen opens. **"Could not find the house controller." is a FAIL** —
that was the villa/patio/keep defect.

☐ pass ☐ fail — notes: ________________________________

### 13. Place beside harmless terrain

Find ground where the footprint itself is flat grass or sand, but there is a **tree, fence, flower
bed, path or a one-block step immediately outside the wall line**. Place a house there.

**Expect:** it places. The old rule demanded a clear, flat skirt one to two blocks wide all the way
round and refused this.

☐ pass ☐ fail — notes: ________________________________

### 14. Try to overlap two houses

Place a house. Then aim a second deed so its footprint would overlap the first, and place.

**Expect:** refused — *"That overlaps another house."*

Then place a second house so it **touches** the first without overlapping.

**Expect:** allowed. Houses may share a wall; a town needs that.

☐ overlap refused ☐ adjacent allowed — notes: ________________________________

---

## Persistence

### 15. Confirm the house reaches Rails

After placing a house, open the admin house list on the website (`/admin/houses`).

**Expect:** the new house is listed, with the **owner name filled in**, the shard, the region, and
the coordinates you placed it at. Check `/admin/houses/house_map` too — the placement should be
listed there.

Check the server log for `[housing] House <uuid> is durably recorded with Rails`. If instead you see
`retrying in ...`, Rails was unreachable; the house is queued and will arrive — confirm it appears
within a minute or so, and note it.

☐ appears ☐ queued then appeared ☐ never appeared — **FAIL**

House uuid: ______________________

### 16. Reconnect and confirm ownership survives

Log out **while standing inside your own house**, log back in, and immediately try step 3 again
(break a block with a tool, inside).

**Expect:** it works, without having to walk out and back in.

Then, if you can, have the shard restarted and repeat: the house must still be yours, its door must
still respond to your key, and step 3 must still work. This is what proves the region was rebuilt
from the Rails record rather than from memory.

☐ after relog ☐ after restart — notes: ________________________________

---

## If something fails

- Note the **exact** message shown, or that there was none.
- For anything about breaking or placing, note your game mode, what was in your **main hand**, and
  whether you were inside the house.
- For step 15, note the house uuid — it is the only durable name the house has, and it is what the
  outbox and Rails both key on.
