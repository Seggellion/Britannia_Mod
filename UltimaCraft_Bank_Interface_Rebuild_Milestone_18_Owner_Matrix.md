# Milestone 18 — the owner's matrices

**What this is:** the part of Milestone 18 that cannot be automated, laid out so it can be walked
in one sitting. The playbook's acceptance gate requires evidence I cannot produce — screenshots at
agreed scales, client and dedicated-server smoke notes, performance observations — because no
screen-test harness exists in this repo (Architecture Decision 0: `gameTestServer` is a dedicated
server with no client bootstrap).

**What is already proven automatically** is listed in the milestone report; don't re-walk it by
hand. The rows below are the ones where a machine genuinely cannot answer.

**Before starting:** set `-Dbritannia.bank.item_envelope_version=3` on the shard, or stored
cheques will deposit unlinked and the vault will offer no cashing gesture.

Mark each row **pass / fail / n-a** and add a note where it matters. Anything marked fail comes
back to me as a defect.

---

## 1. Visual matrix

Each row at **GUI scale 2 and 4**, windowed and fullscreen. Scale 1 and 3 are worth a glance but
the epic's layout sweep already covers 180–1200 × 200–700 arithmetically; what you are looking for
here is what the arithmetic cannot see — text colliding with art, controls under the frame,
unreadable contrast.

| # | Case | Screen | Look for | Result |
| --- | --- | --- | --- | --- |
| V1 | Empty vault | Bank Box | Grid structure still drawn; "vault is empty" reads; chest art not squashed | |
| V2 | Full vault (45+ items, scrolled) | Bank Box | Scroll works; no row clipped by the chest interior; selection ring follows the item | |
| V3 | Maximal balance (millions in all three) | Balance | Sentence wraps inside the parchment, never under the buttons | |
| V4 | Longest item name (255 chars) | Bank Box | Tooltip stays on screen at every edge; name does not overflow the panel | |
| V5 | Longest status message | all four | Wraps above the controls, never behind them | |
| V6 | Narrow window (chest hidden) | Bank Box | Plain fallback layout; controls reflow beneath the hotbar; nothing overlaps | |
| V7 | Cheque tints | pack + vault | Gold/silver/copper visibly distinct; none renders invisible | |
| V8 | Disabled controls | Bank Box | Greyed controls still readable, not vanished | |

**Screenshots wanted:** V1, V2, V3, V6 at scale 2 and scale 4 — eight images is enough evidence
for the gate.

---

## 2. Interaction matrix

| # | Case | Expected | Result |
| --- | --- | --- | --- |
| I1 | Back from each of the three sub-screens | Returns to Main; status line does **not** follow you | |
| I2 | Escape from every screen | Closes banking entirely; never acts as Back | |
| I3 | Rapid navigation (spam Main → Box → Back ×10) | No stuck screen, no lost session, no duplicated widgets | |
| I4 | Drag precision | A 3px twitch still counts as a click; a real drag never deposits the wrong slot | |
| I5 | Invalid drop (drag to the pack, or outside) | Nothing happens; item stays put; no packet sent | |
| I6 | Selection during scrolling | Ring follows the **item**, not the cell, as rows move | |
| I7 | Amount-field focus | Typing goes to the field; Tab/click order is sane; field locks while pending | |
| I8 | Tooltip bounds | Never leaves the screen at any edge or corner | |
| I9 | Pending controls | Every control disables during a request and re-enables on the answer | |
| I10 | Close during request | Escape mid-request closes and **stays closed** when the answer lands | |
| I11 | Double-click a pack cheque | Cashes it | |
| I12 | Double-click a stored cheque | Cashes it from the vault; row disappears; balance rises | |
| I13 | Double-click an ordinary stored item | Nothing happens | |

---

## 3. Multiplayer and latency matrix

Needs two clients on one dedicated server, both at the same teller.

| # | Case | Expected | Result |
| --- | --- | --- | --- |
| M1 | Latency during deposit | Controls stay locked; result or refresh always arrives; after ~10s the "taking longer than usual" line appears | |
| M2 | Latency during withdrawal | Same | |
| M3 | Two clients, same account, one deposits | The other's view corrects on its next refresh; no phantom item | |
| M4 | Both withdraw the same stored item | Exactly one gets it; the other reads "that item is no longer here" | |
| M5 | Balance changes while Create Cheque is open | Amount revalidates against the new balance; Confirm disables if it no longer fits | |
| M6 | Reconnect after an uncertain result | On reopen the account shows the truth; nothing was invented client-side | |
| M7 | Screen reopen | Always an authoritative fetch, never a cached view | |

**Performance note wanted:** with a 45+ item vault open, is the Bank Box smooth at scale 2? Any
frame hitch on scroll or drag?

---

## 4. Security matrix — what to try by hand

The ten rows are covered automatically at the packet and codec layers (see the report). What is
worth doing by hand is only what a modified client could do that a test cannot easily stage:

| # | Case | Expected | Result |
| --- | --- | --- | --- |
| S1 | Walk out of range mid-request | Request already sent completes or is dropped; nothing is lost either way | |
| S2 | Log out mid-request | Same; on relog the account is consistent | |
| S3 | Kill the Rails service mid-request | Transport failure reaches the player as a readable line, not a hang | |

---

## 5. Sign-off

| Matrix | Walked by | Date | Outcome |
| --- | --- | --- | --- |
| Visual | | | |
| Interaction | | | |
| Multiplayer/latency | | | |
| Security (manual rows) | | | |

**Defects found:** _(list here; each becomes a fix before Milestone 19)_
