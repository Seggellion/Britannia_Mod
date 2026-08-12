# QuestEngine Live Validation Runbook (L1–L10)

The automated suite stops at the HTTP boundary. The gametest harness cannot answer a quest
trigger, so **no automated test has ever walked a player into a zone, picked an item up, or burned
one and watched Rails answer.** Everything in Milestone 6 up to the request and after the response
is pinned; the round trip itself is not.

This runbook is that round trip. It is owner-executed against a real Rails instance with two
accounts, and **Milestone 6 stays conditionally approved until every row passes with evidence**
(owner decision, 2026-08-12).

---

## Before you start

**Two accounts on one shard.** Call them **A** and **B**. Both need `shard_users` rows and a
verified `minecraft_uuid`; the whole point of several rows is that A and B are told apart
correctly.

**Log capture, both sides.** Every row below names the events that decide it. Have these running:

```bash
tail -f logs/latest.log | grep -E "quest_action_|quest_objective_|quest_journal_|quest_turn_in_"
```

```bash
tail -f log/production.log | grep -E "quest_turn_in_|quest_player_|quest_journal_served|QuestTrigger"
```

**The correlation id is the thread.** Every Minecraft line carries `request_uuid=`, and the Rails
line for the same action carries the same value. When a row fails, that id is how you find the
other half — start there rather than reading by timestamp.

**A row with no captured log evidence is not a pass.** "It looked right in game" is exactly how
the original defect survived for so long: the quest *looked* fine, and only one player could not
finish it.

**Recording**: for each row, file the correlation id, the matching Minecraft and Rails lines, and
the resulting `player_quest_states` row (`id`, `player_uuid`, `current_node_id`, `status`,
`completed`, `completion_request_uuid`).

A quick way to read the row:

```sql
SELECT id, player_uuid, quest_id, current_node_id, status, completed, completion_request_uuid
FROM player_quest_states WHERE player_uuid IN ('<A-uuid>', '<B-uuid>') ORDER BY id;
```

---

## L1 — Multiple simultaneous active quests

**Why**: before Milestone 6 the client drove objectives from a single "current quest" static, so
only the most recently touched quest could progress. This is the row that proves that is over.

1. A accepts three quests from different givers: one with a **location** objective, one with a
   **pickup** objective, one with a **destroy** objective. Leave all three active.
2. Confirm all three are in the journal: `GET /api/status/<A-uuid>` returns three
   `accepted_quests`, each with its own `triggers`.
3. Complete each objective in turn, in an order that is *not* the order they were accepted.

**Passes when** each objective fires exactly once, and every `quest_objective_detected` line names
the `quest_state_id` of the quest it belongs to — never another one's.

---

## L2 — Relog before objective completion ⭐

**Why**: this is the historical defect in its purest form. Before Milestone 6, the client state
that drove objectives was never restored at login, so after a relog **nothing fired at all** —
and it looked exactly like "this player's quest is broken".

1. A accepts a quest with a location objective.
2. A walks to just **outside** the zone. Do not enter it.
3. A quits to title (full disconnect, not just a dimension change) and rejoins.
4. A walks into the zone.

**Passes when** the objective fires after the relog: `quest_objective_detected … source=location`
followed by `quest_objective_applied`. Before M6 this produced silence.

---

## L3 — QuestGiver interaction between objectives

**Why**: interacting with a giver rewrites journal state. If that clears or duplicates a pending
objective, a quest can stall in a way that is invisible until someone reports it.

1. A completes objective 1 of a multi-step quest.
2. A returns to the quest giver and takes the next dialogue step (a real CHOOSE).
3. A completes objective 2.

**Passes when** both objectives fire, the dialogue step in between neither clears nor re-fires
either, and the journal shows exactly one row for the quest throughout.

---

## L4 — All three objective types after a relog

**Why**: L2 proves the mechanism for locations. Pickup and destroy travel the same path but
through different events, and "it works for locations" has never been evidence for the other two.

1. **Pickup**: A accepts a pickup quest, drops the required item on the ground, relogs, then picks
   it up.
2. **Destroy**: A accepts a destroy quest, relogs, then destroys the item in the required volume.

**Passes when** each fires post-relog, with `source=pickup` and `source=destroy` respectively, and
`quest_objective_applied` shows `completed=true` where the node ends the quest.

---

## L5 — Simultaneous two-player progression

1. A and B both hold the same quest with a location objective.
2. Both walk into the zone together, as close to simultaneously as you can manage.

**Passes when** both progress: two `quest_objective_detected` lines with **different**
`player_uuid` values, both followed by `quest_objective_applied`, and neither blocking the other.
The in-flight guard is keyed per player and per quest state — if one player's entry suppressed the
other's, that is the bug this row exists to catch.

---

## L6 — Player isolation ⭐

**Why**: the closest live analogue of the original report. A completing must never touch B.

1. A and B both hold the same quest.
2. A completes it fully, including the turn-in.
3. Inspect B: journal, current node, and reward state.
4. B then completes the quest independently.

**Passes when** B's row is untouched by step 2 (same `current_node_id`, still active), B completes
in step 4, and B receives the reward **once**. Automated coverage exists
(`quest_multiplayer_isolation_test.rb`); this row proves it end to end with two real clients.

---

## L7 — A location objective fires once, not repeatedly ⭐

**Why**: the only row that can fail in a way no player would ever report. The pre-M6 client
re-sent the trigger every second for as long as someone stood in the zone.

1. A accepts a quest with a location objective.
2. A walks into the zone and **stands still for at least 60 seconds**.

**Passes when** there is exactly **one** `quest_objective_detected` line for that trigger key in
the whole window. Count them:

```bash
grep -c "quest_objective_detected.*trigger_key=<key>" logs/latest.log
```

A second line is the entire regression. Two mechanisms have to hold for this to pass — the
in-flight guard, and the objective being rewritten from the response's new node — so a failure
here is worth diagnosing rather than retrying.

---

## L8 — Reward integrity across a lost turn-in response

**Why**: Milestone 4's guarantee. Rails commits the completion; if the response never arrives, the
reward must not be lost and must not be granted twice.

1. A brings a quest to its turn-in.
2. Interrupt the response after Rails commits — kill the client mid-request, or block the response
   at the network layer. (`quest_turn_in_result … result=success` in the Rails log with no
   corresponding `quest_action_result` in the Minecraft log is the state you want.)
3. A rejoins and retries the turn-in.

**Passes when** the retry logs `quest_turn_in_replayed` with the same `request_uuid`, returns the
same `granted_items`, and A ends up with the reward **exactly once** in their inventory.

---

## L9 — Cold-journal recovery

**Why**: Milestone 5. A failed login bootstrap used to disable quests for the whole session.

1. Start the Minecraft server with **Rails stopped**.
2. A joins. The bootstrap fails — expect `World bootstrap did not complete` naming the quest
   journal as unloaded.
3. Start Rails.
4. A acts on a quest (interact, or an objective).

**Passes when** the action produces `quest_journal_refresh_requested` →
`quest_journal_refreshed` → the action proceeding. No `server_journal_not_loaded` refusal may
survive the recovery. (If you act before Rails is up, expect one
`server_journal_unavailable` and a cooldown — that is correct; retry after ~15 s.)

---

## L10 — Escort across a server restart

**Why**: Milestone 1. The escort's assignment used to be deleted within the first ticks after a
restart, permanently.

1. A activates an escort.
2. Restart the Minecraft server while the escort is loaded near where A logged out.
3. A rejoins.

**Passes when** the escort still carries its assignment tags and resumes following. Check the
entity's tags with `/data get entity <uuid>` — `escort_active`, `quest_escort_<A-uuid>`,
`quest_state_id_<id>` must all still be there. Their absence is the M1 regression, and it is not
recoverable in-world.

---

## If a row fails

Stop. Capture the correlation id and both sides' log lines, and record which row and which step.
A failing row becomes its own corrective milestone — **it is not a reason to re-approve Milestone
6 on the strength of the automated suite**, which by construction cannot see any of this.

## When every row passes

Record the evidence in the Milestone 8 completion report, then update the health check's final
verdict from *conditional* to its earned answer, and note in
`QUEST_REMEDIATION_LOG.md` that Milestone 6 moved from strongly-validated to production-proven.
