# Grabby Hands — production diagnostic runbook

One server test. Ten minutes. It ends with a copied block of text that names the failing layer.

> **Resolved 2026-08-26.** The production cause was an occupied off hand: the pickup gesture needs
> *both* hands empty, and a non-empty off hand produced no message at all. It now says so. This
> runbook is kept for the next time something in this area goes quiet.

**Do not run this test with an operator account.** Operators are exempt from vanilla spawn
protection and pass policy gates an ordinary player does not. Testing as staff is how this defect
stayed invisible in the first place. You need one operator (to run the commands) and one **normal,
non-OP** player (to make the gesture).

---

## Steps

1. Deploy the new JAR to the dedicated server.
2. **Restart the server.** Not `/reload` — see "Restart required" below.
3. In the startup log, find the block beginning `[grabby-hands][env]`. If a line at **ERROR** level
   says spawn protection is ARMED, note the radius.
4. As the operator:

   ```
   /grabby env
   ```

   Copy the output. Check `build: commit=…` matches the commit you deployed, and
   `artifact: … sha256=…` matches the file you uploaded. If either differs, **stop** — the server is
   running something else.
5. Have the affected **non-OP** player:
   - confirm they are in Adventure Mode (normal play, not forced by hand);
   - place a chair;
   - empty **both** hands;
   - sneak;
   - look directly at the chair and hold still.
6. While they hold that, as the operator:

   ```
   /grabby debug <affected-player>
   ```

7. Copy the **complete** output, including the last line, which reads either
   `FIRST REFUSING GATE: <layer> / <question>` or `ALL PRECONDITIONS PASS`.
8. Have the player attempt the shift + right-click. Record what happens: chair recovered, chair
   unchanged, player seated, or a message above the hotbar.
9. If step 8 did nothing and step 7 said `ALL PRECONDITIONS PASS`, run `/grabby debug` again
   immediately afterwards and copy that too — the difference between the two runs is the evidence.
10. Repeat steps 5–8 with a wine bottle in place of the chair.

Send back: the `/grabby env` output, both `/grabby debug` outputs, and what the player saw.

---

## Reading the result

| Last line of `/grabby debug` | What it means | What to do |
| --- | --- | --- |
| `FIRST REFUSING GATE: packet / vanilla spawn protection allows this position` | The use packet dies before any mod code runs. | Set `spawn-protection=0` in `server.properties`, restart, repeat the test. |
| `FIRST REFUSING GATE: provenance / this exact block was placed by a player` | That chair is authored scenery, not a player possession. | Confirm the player placed *that* chair themselves. If they did, it is a placement-provenance defect — send the output. |
| `FIRST REFUSING GATE: gesture / both hands empty` | **The original production cause.** The off hand held something. | Empty the off hand. The player now gets "Empty your off hand as well to pick that up." instead of silence. |
| `FIRST REFUSING GATE: gesture / …` (other) | Posture is not what we think — usually the server-side sneak flag. | Check the named line. |
| `FIRST REFUSING GATE: packet / not waiting on a teleport acknowledgement` | The connection is mid-teleport. | Have the player stand still a moment and retry. |
| `ALL PRECONDITIONS PASS` but nothing happens | The refusal is somewhere none of the gates model. | Send both debug outputs; we add targeted per-player instrumentation next. |
| `build:` / `sha256` mismatch | Stale or wrong JAR. | Fix the deployment first. Change nothing in source. |

---

## Restart required

`spawn-protection` is read from `server.properties` once, at boot, into
`DedicatedServerProperties`. Nothing re-reads it at runtime — not `/reload`, not a datapack reload.
**Changing it requires a full server restart.**

---

## Note on `spawn-protection=0`

This repository does not ship, generate, or document a `server.properties`; the file belongs to the
host. Vanilla's default is `16`, and that is what a fresh dedicated server uses unless somebody
changed it.

Vanilla spawn protection gates exactly two things in 1.21.1 — block use/placement
(`handleUseItemOn`) and the start of block breaking (`handleBlockBreakAction`) — for non-operators
within the radius of world spawn, in the Overworld only. Britannia already enforces both, and more
finely: every non-creative player is held in Adventure, breaking runs through the mining gate,
managed-deposit, managed-resource and structure-protection handlers, and placement runs through
`StructureProtectionHandler` and house build rights. Setting it to `0` removes a second, blunt,
invisible policy that Britannia's own systems cannot see or explain — it does not remove a
protection Britannia relies on.

That is still the server owner's call. The mod warns; it does not change it.
