# Milestone 10 — Stored-item render contract

**Status:** Complete, awaiting owner review at the acceptance gate
**Baseline:** NeoForge `banking` @ `6905a1f`
**Scope:** As reduced by the 2026-08-03 revision — `item_key` plumbing only. No Rails change, and
none was needed: Rails has sent the key since the item-identity program.

---

## 1. What was built

This closes Milestone 0's principal remaining-work finding: `item_key` was sent by Rails and
dropped on the floor by `BankingOpenResponseParser`. It now travels the whole path.

| Stage | Change |
| --- | --- |
| `BankItemSummary` | `+ @Nullable String itemKey`, blank canonicalized to absent |
| `BankingOpenResponseParser` | `+ lenientItemKey` — same lenient shape as `lenientName` |
| `BankAccountOpenedS2CPayload` | Carries it over the wire, nullable-string pattern, bounded |
| `BankItemIcon` *(new)* | `item_key` → the `ItemStack` the grid will draw, or the fallback |

The record's arity changed **without** a convenience overload, deliberately: every constructor
call site had to be found by the compiler and decide what to do with the key, rather than any of
them silently passing nothing.

---

## 2. One definition of "malformed", in one place

The parser and the wire pass the key through as an uninterpreted string. **`BankItemIcon` is the
single place that decides what an invalid key means**, and the answer is always the same: that one
cell draws the barrier icon; nothing ever fails the grid (design §9.5.2).

This is why the parser deliberately does *not* validate ResourceLocation syntax — it stays
Minecraft-free (so it remains plain-JUnit-testable), and two validators drifting apart would mean
a key the parser accepted and the resolver rejected, or worse the reverse. There is a test pinning
that a syntactically dubious key is carried through for the resolver to judge.

Two decisions inside the leniency worth naming:

- **An over-long key is dropped, not truncated.** `lenientName` truncates, and that is right for
  a name — losing letters. Truncating a registry id produces a *different id*, which could
  resolve to the wrong item's icon. Wrong icon beats no icon is false; unknown icon beats wrong
  icon is true.
- **`minecraft:air` resolves to the fallback, not to itself.** Air is a real registry entry that
  draws as nothing — it would render an occupied cell as empty, which is precisely the "my items
  disappeared" misreading the identity program existed to fix. Same guard for the registry's
  AIR-default lookup path (`getOptional`, never `get`).

---

## 3. The trust boundary, unchanged

`item_key` is render data travelling client-ward only (design §9.5.4). The stack `BankItemIcon`
builds exists to be drawn; it is never sent anywhere, never compared against anything
authoritative, and withdrawal continues to reference the public bank-item UUID. The server-side
security rules in Playbook Milestone 10 required no code: the server already ignores client claims
about registry ids because no packet field for one exists in the client→server direction.

Icons render from the registry id alone — §9.5.3, decided, closed, and honoured: no component
data, no render snapshot, no `payload` to the client. A renamed, enchanted, damaged sword draws as
a plain sword; `displayName` carries what makes it special.

---

## 4. Tests

```
BankingOpenResponseParserIdentityTest    +5   (present / absent / wrong type / over-long / dubious-but-carried)
BankAccountOpenedS2CPayloadTest          +1, 2 extended   (key round-trip; absent survives as absent; blank canonicalizes)
BankItemIconGameTests                     6   (new file — see below)
Full JUnit suite                        541 pass, 0 fail   (was 536)
All GameTests                           296 run, 295 pass  (was 290)
```

The resolver is the one part of the contract Decision 0's extraction cannot reach —
`BuiltInRegistries.ITEM` needs a bootstrapped game — so it is covered by GameTests: vanilla item
with count, this mod's own item, an unregistered id (the removed-mod case §9.5's compatibility
rules were written for), malformed strings, absent key, and the air case. The playbook's remaining
listed cases land with the grid itself in Milestone 11: stack-count *rendering*, custom-name
tooltip, and the fallback tooltip are presentation over this contract.

The one GameTest failure remains the pre-existing world-state bootstrap test.

---

## 5. Milestone report

**Files added.** `client/screen/bank/BankItemIcon.java`, `gametest/BankItemIconGameTests.java`.

**Files changed.** `BankItemSummary`, `BankingOpenResponseParser`, `BankAccountOpenedS2CPayload`,
plus their three test files.

**Decisions made.** Drop-don't-truncate for over-long keys; air-as-fallback; single locus of
validity in the resolver; barrier as the unknown icon (§2).

**Tests run.** `./gradlew test` — 541 pass. `./gradlew runGameTestServer` — 296 run, 295 pass.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No grid rendering — `BankBoxScreen`'s cells still draw empty. Milestone 11 binds
  `BankItemIcon.iconFor` into the stored-item grid, with tooltips and selection.
- No component-accurate icons, no render-snapshot field, no `payload` to the client (§9.5.3,
  closed).
- No Rails change of any kind.

---

## 6. Acceptance gate

> *Real bank contents can be rendered reliably in a grid.*

The contract for that sentence is delivered and proven: every stored item now reaches the client
carrying everything the grid needs (public id, weight, name, count, registry key), and the
key→icon step is tested against every failure mode §9.5's compatibility rules name — including
the two that would have rendered a full cell as an empty one. The grid that *draws* it is
Milestone 11, which is now pure presentation over a tested contract.

**Stopping here for owner review.** Milestone 11 (player inventory and bank grids — real contents,
tooltips, single-click selection) is next.
