# Milestone 8 — In-Game Validation Package

Everything needed to stand the feature up and check it by hand, plus what Milestone 7 item 4
already proved live.

---

## 1. Environment setup — the part that cost the most time

The mod reaches Rails through **two independent auth layers**, and satisfying one does not satisfy
the other. This is the single most useful thing learned during live validation.

| Endpoint | Needs |
|---|---|
| `world_bootstrap/:shard` | `shard_name` + `shard_secret` |
| `service_npc_spawn_operations` | those **plus** `Minecraft-Server-Key` |

That asymmetry produces a genuinely confusing symptom: the spawn block populates its city and type
dropdowns and renders the whole Guildmaster readout correctly, while registration fails with
`authentication_blocked`. Everything looks configured because most of it is.

Create `run/config/britannia_mod-server.properties` (gitignored — it holds a shard secret):

```properties
shard_name=Britannia
shard_secret=<Shard#client_secret for that shard>
api_base_url=http://127.0.0.1:3000
allow_integrated_server=true
minecraft_server_key=<MinecraftServer#public_id, an active server on that shard>
```

Notes that are easy to get wrong:

- `shard_name` must match `ModConfig.SHARD_NAME` (`Britannia`).
- `api_base_url` is a **service origin**, no `/api` suffix needed — the resolver appends it.
- `allow_integrated_server=true` is required for a single-player client; a dedicated server does
  not need it.
- `minecraft_server_key` is the server's `public_id` UUID, not a secret. Rails matches it with
  `minecraft_servers.active.find_by(public_id: key.downcase)`.
- **Credentials load once at server start.** Editing this file mid-session does nothing, and a
  failed auth opens a back-off circuit — restart rather than retry.

### Symptom → cause

| On screen | Actually means |
|---|---|
| `Status: Registry unavailable`, both dropdowns empty | No bootstrap. Either no credentials at all, or the block was opened in the ~4s before the bootstrap completed. **An open screen does not self-update — press Refresh.** |
| Dropdowns populated, `Error: authentication_blocked` | `minecraft_server_key` missing or not matching an active server row |
| Magenta boxes on the GUI | Unrelated: `minecraft:textures/gui/widgets.png` missing in this dev environment |

---

## 2. What Milestone 7 item 4 proved live

Confirmed on a real client against Rails at `localhost:3000`, not by test:

- The bootstrap populates 23 cities and 14 service types (`bank_teller` + 13 guilds).
- The spawn block renders the **taught-skill list**, wrapped two per row — the layout built for the
  eleven-skill Ranger guild, seen working on the six-skill Bard guild.
- The **city economy readout** computes against real data and colours correctly:
  `Alcohol 0 / 5` and `Food 0 / 200` red, `Gold 9 / 1` green.
- The gold reading confirms the treasury decision works end to end: Rails
  `City#gold_amount` → bootstrap `treasury.gold` → mod supply map → eligibility.

---

## 3. Owner in-game script

Prerequisite: both seeds run (`db:seed:uo_skills`, then `db:seed:guildmaster_definitions`).

1. Place a Service NPC Spawn Block (creative, permission level 2).
2. Open it. Confirm the city and type dropdowns populate. If not, press **Refresh** once before
   investigating anything.
3. Select a city and a Guildmaster type. Confirm the **Teaches:** list matches that guild's RunUO
   roster, and that the **City economy** lines show real numbers.
4. Save. Confirm `Status: PENDING_REGISTRATION`, then `REGISTERED` once delivered.
5. **Give the city its supplies** — the seeded gate is 200 food, 1 gold, 5 alcohol. A city below
   any of them will not be staffed. This is the step most likely to look like a bug.
6. Run a staffing reconciliation from the Rails admin. Staffing is **admin-triggered**, not
   automatic; nothing spawns without it.
7. Confirm a Guildmaster appears at the post, with a random personal name and the nameplate
   `{Name} the {Guild} Guildmaster`.
8. Confirm a bank teller elsewhere still reads as a bare personal name — the regression guard.
9. Right-click the Guildmaster. Confirm the training screen lists its skills with prices, and that
   unaffordable or maxed rows are disabled with the correct reason.
10. Buy training with gold in hand. Confirm: coins decrease by exactly the quoted amount, the skill
    rises, and the **city treasury gold increases by the same amount**.
11. Buy with too little gold. Confirm refusal and that **no coins are taken**.
12. Train to 40.0. Confirm the row becomes "taught in full" and refuses further purchase.
13. Restart the server. Confirm the Guildmaster returns with the same guild and nameplate.
14. Drop the city below a minimum, reconcile, and confirm the Guildmaster is unassigned — the
    symmetric threshold decision.
15. Open a Banker and confirm normal banking throughout.

---

## 4. Automated evidence

```bash
gradlew test --rerun-tasks --no-configuration-cache
gradlew runGameTestServer --rerun-tasks --no-configuration-cache
```

Both flags are required per `docs/known_environment_baseline.md` §1.1/§1.3, or the run is silently
stale.

| Suite | Result |
|---|---|
| Mod JUnit | 1752+ tests, 0 failures, 0 errors |
| Mod GameTest | 349 required tests passed (count cross-checked against `@GameTest(` in source) |
| Rails | 1345 runs, 0 errors |

Rails runs need a non-superuser local role; see the loose-ends register for why two environment
guards fire otherwise.

---

## 5. Still outstanding

- Steps 5–7 and 10–14 above have **not** been performed — the feature has never been seen
  training a player in-game. That is the remaining substance of Milestone 8.
- `db/schema.rb` is at mode `666` from the ownership workaround; restore to `644`.
- A `MISSING` registry (Rails omitting the member) is silent — only `REJECTED` warns. An operator
  would see "Registry unavailable" with nothing in the log.
