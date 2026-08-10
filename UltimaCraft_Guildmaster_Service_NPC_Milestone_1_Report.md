# Milestone 1 Report — Canonical Guildmaster Service Type and Skill Binding

Branch `feature/guildmaster-service-npc`, worktree `C:\projects\britannia\mod\britannia_mod_guildmaster`,
baseline `50061f07`. **Mod side only.** No Rails changes, nothing committed, nothing pushed.

---

## 1. What this milestone establishes

Guildmaster is now a first-class Service NPC service type on the mod side, with a stable,
index-free skill binding and a working role title.

The shape follows the Milestone 0 recon and the owner's Decision 1 (guilds teach a *group* of
skills, per RunUO), not the design document's original single-skill/skill-index model:

- **The service discriminator stays the Rails-published `service_npc_types` registry.** No
  `ServiceNpcKind` enum was created — that would have been the parallel framework the design
  explicitly forbids.
- **`guild.train` joins the closed service-key allow-list** alongside `bank.open` and
  `bank.create_check`.
- **The taught-skill binding is a list of `skills.slug` values on the service type.** No index is
  persisted anywhere, so an admin adding or unpublishing a skill in Rails can never silently
  retarget a spawned Guildmaster.
- **The role title renders as `{Personal Name} the {Guild} Guildmaster`,** and only for
  Guildmasters — every existing bank teller's nameplate is byte-identical to before.

---

## 2. Files changed

### Modified (5)

| File | Change |
|---|---|
| `service/ServiceNpcTypeDefinition.java` | New `List<String> taughtSkillSlugs` component, defensively copied. Added a 9-arg convenience constructor delegating to the canonical 10-arg one. |
| `service/ServiceNpcRegistryParser.java` | `guild.train` added to `SUPPORTED_SERVICE_KEYS`; new `parseTaughtSkillSlugs` for the optional `taught_skill_slugs` member; new `SKILL_SLUG` pattern; cross-validation between `guild.train` and the taught list. |
| `service/ServiceActionDispatcher.java` | `guild.train` added to `SUPPORTED_SERVICES` with its own `TRAINING_UNAVAILABLE_MESSAGE`. |
| `entity/ServiceNpcEntity.java` | `getRoleTitle()` resolves the published role; new `updateDisplayName()` override; `setServiceNpcTypeKey` and `readAdditionalSaveData` re-render the nameplate. |
| `entity/CitizenEntity.java` | `UO_STYLE` widened `private` → `protected`. Visibility only. |

### Added (3 main, 4 test)

`service/guild/GuildmasterCapability.java`, `service/ServiceNpcDisplayName.java`,
`gametest/GuildmasterServiceNpcGameTests.java`;
`service/ServiceNpcRegistryParserGuildmasterTest.java`,
`service/guild/GuildmasterCapabilityTest.java`,
`service/ServiceNpcDisplayNameTest.java`.

### Deliberately untouched

`ServiceNpcSpawnBlock`, `ServiceNpcSpawnBlockEntity`, `ServiceNpcSpawnMenu`,
`ServiceNpcSpawnScreen`, every spawn payload and the Rails spawn wire contract,
`ServiceNpcAssignmentReconciler`, `SkillManager`, and all banking code. Those belong to
Milestones 2–6.

---

## 3. Design decisions worth reviewing

**The record got a second constructor instead of 24 call-site edits.** `ServiceNpcTypeDefinition`
is constructed in 18 files — six times in `ServiceNpcSpawnConfigurationValidatorTest` alone, plus
every banking GameTest's registry fixture. None of them are about taught skills. A 9-arg overload
defaulting to `List.of()` keeps this milestone's diff out of eleven banking test files it has no
business touching. Arity alone disambiguates.

**`taught_skill_slugs` is optional forever, not just for now.** Absent or JSON-null parses as the
empty list. This is load-bearing: a rejected registry falls back to the *empty snapshot wholesale*,
so if the mod ever required this member, an older Rails server would take bank tellers down with
it. Two tests pin this.

**Slugs use their own pattern, not `DEFINITION_KEY`.** Rails' friendly_id parameterises "Animal
Taming" to `animal-taming`. `DEFINITION_KEY` rejects hyphens, so validating slugs with it would
have made every multi-word skill unpublishable. `SKILL_SLUG` is
`[a-z0-9]+(?:[-_][a-z0-9]+)*` — hyphens *and* underscores, because `slug` is a plain
admin-editable column.

**The parser rejects incoherent pairings in both directions.** A type allowing `guild.train` with
no taught skills is rejected, and so is a type declaring taught skills without allowing
`guild.train`. Neither shape can reach the cache.

**The nameplate change is scoped to Guildmasters on purpose.** `ServiceNpcEntity` inherits
`CitizenEntity.updateDisplayName()`, which renders the personal name alone — a teller reads
`"Aldric"`, never `"Aldric the Bank Teller"`. Applying the combined form to all Service NPCs
would have renamed every existing teller in every existing world. That is a visible regression this
milestone had no mandate to make, so `updateDisplayName()` returns to `super` whenever there is no
published role. `ServiceNpcAssignmentReconcilerGameTests:111` asserts a teller's nameplate directly
and is the tripwire.

**Two ordering hooks were genuinely required, not defensive.** The role is derived from
`serviceNpcTypeKey`, which arrives *after* the personal name on both paths that set it:
`ServiceNpcAssignmentReconciler.applyAssignmentData` sets the name first and the type key four
lines later, and `CitizenEntity.readAdditionalSaveData` renders the name before
`ServiceNpcEntity.readAdditionalSaveData` reads the type key back out of NBT. Without the
re-render in `setServiceNpcTypeKey` a fresh Guildmaster would spawn with a bare name; without the
one in `readAdditionalSaveData` it would lose its title on every chunk reload. Both are covered by
GameTests.

**`getRoleTitle()` is currently read by nothing.** `updateDisplayName()` queries
`GuildmasterCapability` directly, because it must distinguish "has a published role" from "fell
back to the generic label" and a plain `String` return cannot express that. The override is kept
so the inherited hook never reports something false about a Guildmaster.

---

## 4. Playbook item mapping

| Playbook Milestone 1 item | Status |
|---|---|
| 1. Extend the service-type abstraction with Guildmaster | Done — `guild.train` in the parser + dispatcher, `GuildmasterCapability` mirroring `BankingCapability` |
| 2. Service-specific Guildmaster payload with the canonical taught skill ID | Done — `taughtSkillSlugs` on the type; a **list**, per owner Decision 1 |
| 3. Reuse the canonical skill registry | Done — slugs only; no skill list is duplicated in mod code |
| 4. Deterministic skill index ↔ canonical skill mapping | **Dissolved, by design.** No index exists or is persisted. See §5. |
| 5. Serialization/deserialization for Guildmaster data | Done — parser in, existing `ServiceNpcTypeKey` NBT out |
| 6. Role/title generation | Done — `{Guild} Guildmaster` from the type's `display_name` |
| 7. Preserve random personal naming | Done by not touching it — names come from Rails `WorldNpcs::Create` via the reconciler |

---

## 5. Why there is no skill index (deviation from the design document)

Design §4.3 and Owner Requirement 5 call for the spawn block to select a skill by "canonical
index". Milestone 0 §6.4 proved no canonical order exists — `SKILL_DEFS` is a `ConcurrentHashMap`,
Rails' `skill_config` is `Skill.all` with no `order`, and `skills` is admin-editable CMS content
with no ordering column and no seed file.

Owner Decision 1 (guilds teach groups) removed the need for one entirely: the admin selects a
*guild* from the service-type dropdown that already exists, and the guild carries its own skill
set. The persisted identity on both the block entity and the entity is the existing
`ServiceNpcTypeKey` string.

This satisfies the design's actual persistence rule — *"prefer persisting the canonical skill ID on
the entity itself so registry reorderings do not silently retarget already-spawned Guildmasters"* —
more strictly than an index ever could.

---

## 6. Schema / cross-repo implications

**No Rails change has been made.** Milestone 1 is the mod half of the mod-first deployment ordering
confirmed in owner Decision 4. Rails must not publish `guild.train` or `taught_skill_slugs` until
this build is live, or the registry parse rejects and bank tellers break.

What Rails will need in Milestone 4:

1. `"guild.train"` added to `ServiceNpc::ActionRegistry::ACTIONS`.
2. A `service_npc_type_taught_skills` join table (13 of the 39 RunUO skills belong to 2+ guilds).
3. `ServiceNpcRegistrySerializer#serialize_npc_type` emitting
   `taught_skill_slugs: npc_type.taught_skills.order(:slug).pluck(:slug)`.
4. Twelve `ServiceNpcType` rows seeded from the RunUO groups, intersected with the real `skills`
   table (owner Decision 6 / addendum §C).

The mod side accepts all of that today and degrades cleanly to "no Guildmasters" without it.

---

## 7. Verification

Environment note: `gradle/wrapper/gradle-wrapper.jar` checked out as an unsmudged **Git LFS
pointer** in a fresh worktree, and `git lfs pull` did not resolve it (the object does not appear to
be in the local LFS store). Restored by copying the identical 43,504-byte jar from the owner's
`Britannia_Mod` tree. It now shows as modified in `git status` — the same state the other three
worktrees are already in — and **must be excluded from any commit**.

| Command | Result |
|---|---|
| `./gradlew compileJava compileTestJava --no-configuration-cache` | **BUILD SUCCESSFUL.** Only pre-existing `makeMockServerPlayerInLevel` deprecation warnings. |
| `./gradlew test --rerun-tasks --no-configuration-cache` | **BUILD SUCCESSFUL** — aggregated from `build/test-results/test/*.xml`: **1712 tests, 0 failures, 0 errors, 17 skipped, 204 classes.** The 17 skips are pre-existing. |
| `./gradlew runGameTestServer --rerun-tasks --no-configuration-cache` | **BUILD SUCCESSFUL** — `All 340 required tests passed :)` (`run/gametest/logs/latest.log`). Includes the three new Guildmaster tests; see §7.1 for how that was proven rather than assumed. |

Both flags are used per `docs/known_environment_baseline.md` §1.1/§1.3 — without `--rerun-tasks`
Gradle reports `UP-TO-DATE` without executing anything, and `runGameTestServer` additionally needs
`--no-configuration-cache` to avoid reusing a stale configuration.

**An earlier run of the full suite failed with 4 failures, all in the new parser test's own JSON
fixture** (it declared a duplicate `service_actions` entry when a case reused an existing service
key, and one assertion expected the wrong error wording). No production code was involved and no
pre-existing test failed at any point. Recorded here rather than quietly re-run.

### 7.1 GameTests

`GuildmasterServiceNpcGameTests` adds three tests, covering the only things JUnit cannot reach —
the entity and the registry meeting on a live level:

- `aGuildmasterRendersItsPersonalNameAndRole` — set in the reconciler's real order (name, then
  type key) and assert `"Marcus the Warrior Guildmaster"`.
- `aBankTellerNameplateIsUnchanged` — the regression guarantee, asserted directly.
- `theGuildmasterTitleSurvivesASaveLoadRoundTrip` — NBT round-trip keeps both the stable type-key
  binding and the rendered title.

The holder saves and restores `ServiceNpcRegistryCache` around each test, because it is a
process-wide static that the banking GameTests also install into.

**These three were proven to actually execute, not assumed.** `GameTestServer` reports only an
aggregate count and never names individual tests — grepping the run log (or the far more verbose
`debug.log`) for a *known-existing* test method name returns zero hits, so the absence of
"guildmaster" in the log was no evidence either way. Two independent checks were made:

1. **Arithmetic.** `grep -c '@GameTest('` across all 30 holders returns exactly **340**, and the
   run reported exactly **340** passed. Had the new holder not registered, 337 would have run.
2. **A deliberate-failure probe**, because the arithmetic alone could not rule out a contrived
   offset. `aBankTellerNameplateIsUnchanged`'s expectation was temporarily replaced with a
   sentinel and the suite re-run. It failed, exactly as intended:

   ```
   [Server thread/ERROR] [minecraft/LogTestReporter]: abanktellernameplateisunchanged failed at
       -11046289, -60, 7463227! a bank teller rendered as "Aldric"
       instead of "PROBE_SENTINEL_EXPECT_FAILURE"
   [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(
   ```

   This is worth more than proof of registration: it is direct runtime evidence for this
   milestone's central compatibility claim. A bank teller in a live level, with a Guildmaster
   published in the same registry, really does still render as bare `"Aldric"`. Note also that
   exactly **one** test failed, which independently confirms the other two passed.

   The sentinel was then reverted and the whole suite re-run from scratch:
   `All 340 required tests passed :)`, `BUILD SUCCESSFUL in 4m 52s`. That is the state the
   worktree is in now.

### 7.2 New test coverage

| Class | Tests | Covers |
|---|---|---|
| `ServiceNpcRegistryParserGuildmasterTest` | 17 | multi-skill parse and order; immutability; hyphen and underscore slugs; **bank teller unaffected in the same registry**; absent/null member; both incoherent pairings; duplicate, uppercase, spaced, leading-separator, blank and non-string slugs; non-array member; list bound; `guild.join` still rejected |
| `GuildmasterCapabilityTest` | 9 | live capability gate; teller is not a Guildmaster; inactive type; teaches-nothing type; membership vs. presence; **two guilds sharing one skill**; role title; null/blank/unknown keys; empty registry (the client's permanent state) |
| `ServiceNpcDisplayNameTest` | 5 | combination; no role title; blank personal name; both blank; trimming |

---

## 8. Risks carried forward

1. **Deployment ordering is now load-bearing.** This build must be live before Rails publishes
   `guild.train`. Unchanged from Milestone 0 §12.1 — recorded because it is now real rather than
   hypothetical.
2. **The RunUO → UltimaCraft slug mapping is still unresolved** (addendum §C). The mod validates
   slug *shape*, never existence — it cannot know whether `arms-lore` is a real skill. A
   Guildmaster seeded with a nonexistent slug will parse fine and then fail at training time.
   Milestone 4's seed must do the intersection.
3. **`updateDisplayName()` now runs on `setServiceNpcTypeKey`.** Any future caller that sets the
   type key in a hot loop would re-render the nameplate each time. Both current callers are
   one-shot.
4. **The LFS wrapper-jar quirk** will recur in any fresh worktree of this repo.

---

## 9. Milestone 1 gate

- Build passes.
- Focused tests pass.
- Existing Service NPC type tests remain green — 1712/1712, nothing modified in them.

**Stopping here as the playbook requires.** Milestone 2 (`ServiceNpcSpawnBlock` configuration —
taught-skill readout and the economic-eligibility readout from owner Decision 5) is not started.

Open from the addendum and still needed before Milestone 4: the real `skills` slug list or approval
for seed-time intersection; and confirmation that the symmetric economic threshold (below-threshold
**despawns** an existing Guildmaster, per Decision 2 of the second round) is intended to produce
visible flicker as city supplies oscillate.
