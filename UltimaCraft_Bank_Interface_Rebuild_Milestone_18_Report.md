# Milestone 18 — Scaling, multiplayer, and security validation

**Status:** Automated half complete; the gate needs the owner's matrices
**Baseline:** NeoForge `banking` @ `e4875e5`

> Milestone 18 is the first milestone whose acceptance gate **cannot be closed by code**. It
> demands screenshots at agreed scales, client and dedicated-server smoke notes, and performance
> observations — none of which this repo can produce, because no screen-test harness exists
> (Architecture Decision 0). So this milestone did the half that can be proven, and hands the
> other half over as a walkable checklist:
> [UltimaCraft_Bank_Interface_Rebuild_Milestone_18_Owner_Matrix.md](UltimaCraft_Bank_Interface_Rebuild_Milestone_18_Owner_Matrix.md).

---

## 1. The audit came first

283 banking GameTests already existed. Writing four new matrices from the playbook's wording
would mostly have re-proven them, so the milestone opened by auditing each matrix row against
what is actually pinned. That is where the value was: it turned a vague "validate everything"
into **seven genuine gaps** and a lot of rows that were already closed.

### Security matrix — the playbook's ten rows

| Row | Status before | Now |
| --- | --- | --- |
| Ineligible slot | Covered ×3 (quest-bound, nested currency, unsupported origin) | unchanged |
| Empty slot | Covered | unchanged |
| **Altered item after drag** | Only inside the deposit proxy's own suite, never through the packet | **new GameTest** |
| False stored UUID | Covered ×2 | unchanged |
| **False amount** | **No test anywhere** on three payloads | **new codec tests** |
| **Unsupported currency key** | Handled in code, never tested | **new GameTest + codec test** |
| Insufficient funds | Covered (Milestone 16) | unchanged |
| Overweight item | Covered (Milestone 17) | unchanged |
| Duplicate request | Covered | unchanged |
| **Stale banker entity** | Only a *forged* id; never a real teller walked away from | **new GameTest** |

### Concurrency matrix

Latency-during-request, duplicate packets, and authoritative-refresh-on-reopen were already
pinned (held futures, in-flight dedup, the Milestone 17 refresh flag). The genuine gap was
**two players racing one stored item**, which is the case where a mistake mints an item.

### Scaling matrix

The layout arithmetic is already swept across 180–1200 × 200–700. What was untested was the
**data** flowing through it: maximal balances, 255-character names, multi-byte names.

---

## 2. What was built

**Four new GameTests** (packet layer, real handlers):

- **An unsupported denomination dies locally.** `"platinum"` genuinely reaches the server — the
  withdrawal codec deliberately does not validate the key, unlike cheque issuance's — and is
  refused before any Rails call. The throwing fake is the proof no round trip was spent.
- **An item swapped mid-flight is never the one banked.** Prepare is held open, the slot is
  swapped diamond → 64 netherite ingots, then prepare completes. The fingerprint captured at
  press time refuses to match: the swap is cancelled, and all 64 ingots are still in the pack.
  This is the row that would be a duplication exploit if it regressed.
- **A real but out-of-range teller drops the request.** Not a forged id — a live teller the
  player walked 500 blocks from. Distance is re-checked on every packet, not merely when the
  screen opened.
- **Two players racing one stored item.** Both genuinely reach Rails; Rails arbitrates; the mod
  honours it per player. Asserted: **exactly five diamonds exist across both inventories**, and
  the loser is told the item is gone rather than being shrugged at.

**Eight new codec guard tests** for the three payloads that carry a *value* rather than only a
selection reference — the "false amount" row, tested at the layer that owns it. A modified
client's bytes die in the codec, before any proxy, Rails call, or inventory. Notable: the
stored-redemption packet has **nothing to forge** — no amount, no cheque id, just a row
reference — and the test pins that by component count.

**Ten new scaling tests** for maximal values and long names: `Integer.MAX_VALUE` formats as
`2,147,483,647` rather than overflowing; the withdrawal form accepts a maximal amount but names
one past it as too large rather than wrapping negative; a 255-character name survives intact
(truncation is the renderer's business, not the model's — losing characters here would lose them
in the tooltip too, where there is room); 255 four-byte emoji are counted as characters, not
bytes; and the status line gets a usable width at every size in the full sweep.

---

## 3. One decision worth recording

The currency-withdrawal codec does **not** validate its currency key, while cheque issuance's
does. That asymmetry looked like a defect during the audit and is not: an unknown key selects
nothing to convert for a cheque (so it must die at decode), whereas for a withdrawal it is
refused by the proxy's own registry lookup before any Rails call — a clean refusal either way,
and keeping the codec free of registry lookups keeps it a pure wire concern. A test now pins
both halves so this stays a decision rather than drifting.

---

## 4. Tests

```
New JUnit tests             18   (8 codec guards, 10 scaling/data-shape)
Full JUnit suite           631 pass, 0 fail   (was 613)
New GameTests                4
All GameTests              332 run, 331 pass  (was 328)
```

The one GameTest failure is the pre-existing order-dependent world-state bootstrap flake, which
is not a banking test and has failed identically in every run since Milestone 16 shifted the
batch ordering. Its task chip stands.

---

## 5. Milestone report

**Decisions made.** Audit before writing (§1); test the "false amount" row at the codec rather
than restaging it per endpoint; keep the two codecs' differing key-validation stances and pin
both (§3); assert the racing-withdrawal case by *counting items across both players*, which is
the property that actually matters, rather than by asserting call ordering.

**Tests run.** `./gradlew test` — 631 pass, 0 fail. `./gradlew runGameTestServer` — 332 run, 331
pass (pre-existing flake).

**Unresolved blockers.** None in code.

**Scope explicitly not performed — and why it cannot be.** The visual matrix, the interaction
matrix, the two-client and latency walkthroughs, screenshots, and performance observations all
require a running client and a human. They are specified row by row in the owner matrix document
with pass/fail columns and a sign-off table. **The Milestone 18 gate stays open until those are
walked** — this report closes only the half that code can close.

**Operational reminder.** `-Dbritannia.bank.item_envelope_version=3` must be set on the shard
before the manual matrices are walked, or the stored-cheque rows (I12, and the V7 vault half)
will correctly do nothing.

---

## 6. Defect found in the field — and why no test caught it

**The owner's first in-game check found every deposit broken.** Not cheque deposits — *every*
deposit, of any item. Rails answered 422 on an ordinary "Sulphurous Ash" stack.

**Cause, and it is mine.** The stored-cheque work added `V3_WITH_CHEQUE_LINK` to
`BankItemEnvelopeVersion`, and that class's number was being sent as the wire
`schema_version` on every deposit envelope. Rails caps
`SUPPORTED_SCHEMA_VERSIONS = [1, 2]` and defines no version 3 — it accepts `cheque_public_id`
independently of the version. Its validator says so in as many words:

> *"schema_version is the client's capability declaration, not a per-key allowlist, and coupling
> the two would invent a second negotiation mechanism."*

I coupled exactly those two things. Configuring level 3 therefore stamped `schema_version: 3` on
every envelope, cheque or not, and Rails rejected all of them with `UNSUPPORTED_SCHEMA_VERSION`.
Confirmed in the Rails log: `"item"=>{"schema_version"=>3, …}` → `422`.

**Fix.** The capability level and the wire value are now separate. `MAX_WIRE_SCHEMA_VERSION = 2`
mirrors Rails' constant with a citation, `emitted()` returns the capped wire value, and
`capabilityLevel()` exposes the raw level for diagnostics. The gates (`emitsIdentity`,
`emitsChequeLink`) still read the level, so the cheque link still ships at level 3 — it simply
declares `schema_version: 2`, which is what Rails wants. **The owner's setting is unchanged:
`-Dbritannia.bank.item_envelope_version=3` remains correct.**

**Why 332 green tests missed it, which matters more than the bug.** Every banking GameTest fakes
the Rails client port. That is the right design — it makes the suite fast, hermetic and able to
stage adversarial cases — but it means **no automated test in this repo can detect a mismatch
with the real Rails contract.** The envelope tests built their JSON by passing a version in
explicitly, so they asserted what the serializer does with a number, never what the production
path actually emits.

Two things now guard it: a test that the wire value never exceeds `MAX_WIRE_SCHEMA_VERSION`, and
one that a cheque-link envelope built from the *production* `emitted()` still declares a version
Rails accepts. Neither would have caught a Rails-side change, and nothing here can — which is the
argument for the live-shard smoke in the owner matrix, a row that has now justified itself on its
first run.

---

## 7. Acceptance gate

> *All material security, concurrency, scaling, and navigation checks pass.*

The security, concurrency, and data-scaling halves pass automatically and are listed above. The
visual, interaction, and multiplayer halves are yours to walk; anything that fails comes back as
a defect before Milestone 19 (legacy retirement), which is the last milestone in the epic.
