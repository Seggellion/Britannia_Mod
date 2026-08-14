# Milestone 3 — Shared banker dialogue framework

**Status:** Complete, awaiting owner review at the acceptance gate
**Architecture:** `UltimaCraft_Bank_Interface_Rebuild_Milestone_1_Architecture.md` (approved 2026-08-03)
**Baseline:** NeoForge `banking` @ `13e3071`

---

## 1. What was built

Four new classes, three new test classes, one additive method on an existing class, and fourteen
translation keys. No banking mutation, no packet, no screen.

| Class | Role | Tested by |
| --- | --- | --- |
| `BankDialogueLayout` | Every coordinate on the three dialogue screens | JUnit, 17 tests |
| `BankStatusPresenter` | `(Operation, Kind)` → translation key + severity | JUnit, 11 tests |
| `BankDialogueFrame` | Draws the parchment, portrait, name, body, status | — (drawing only) |
| `BankActionButton` | Right-hand action button with a pending state | — (drawing only) |
| `DialoguePresentation.text(Component)` | The overload that makes banking translatable | indirectly |

### The split is Decision 0 applied to layout

The playbook asks for layout tests at narrow and wide widths, at several GUI scales, with wrapped
text, with three buttons and with two, and with form controls that overlap nothing. None of that
needs a `Font` or a `GuiGraphics` — it is arithmetic. So the arithmetic is a plain record and the
frame does nothing but draw at the numbers it produces.

**On "multiple GUI scales":** GUI scale is not a parameter and should not be. Minecraft divides by
it before a `Screen` ever sees a coordinate, so a scaled width already encodes it — GUI scale 4 on
a 1024-wide window *is* 256 units. The test constants are named for the real window-and-scale
combinations they stand for (`W_1024_SCALE_4`, `W_1280_SCALE_3`, …) so a failure points at a
setting someone can reproduce, and the invariant is additionally checked at **every** width from
120 to 2000 rather than at sampled points.

---

## 2. A real defect found and fixed in passing

`DialogueLayout.calculate` computes the body wrap width as, in effect, `screenWidth - 343`:

```java
int textX = PORTRAIT_X + PORTRAIT_LAYOUT_SIZE + 25;      // 163
int buttonStartX = screenWidth - BUTTON_WIDTH - 20;      // screenWidth - 160
int maxTextWidth = buttonStartX - textX - 20;            // screenWidth - 343
```

**Below 343 scaled units this is negative**, and it reaches `graphics.drawWordWrap` as a negative
wrap width. A 1024-wide window at GUI scale 4 is 256 units; an 854-wide window at scale 4 is 213.
Both are settings a player can select.

`BankDialogueLayout` holds the line instead: it drops the portrait column when the text would
otherwise be squeezed below a usable width, and clamps to a positive width even after that.
`neverProducesTheNegativeWrapWidthTheLegacyLayoutDoes` is the regression guard, and the brute-force
sweep proves the no-overlap invariant across the whole range.

**`DialogueLayout` itself was deliberately not changed.** It is shared with the quest and service
dialogue screens, and altering their geometry is not this epic's business (Playbook §1 rule 5).
The bank screens simply stop using it. **Flagging it for the owner:** the same latent negative-width
bug is live today on every other dialogue screen in the mod at those GUI scales. That is worth its
own fix, outside this epic.

---

## 3. Localization — Playbook §3.1 satisfied and enforced

The blocker Milestone 0 §5 item 15 identified was structural: `DialoguePresentation.text(String)`
wraps a `String` in `Component.literal`, and every banking label reached the screen through it, so
a `Component.translatable` had nowhere to go.

`text(Component)` is that hole filled — additive, so every existing caller is untouched and
`DialogueViewModel` (all-`String`, shared with the quest system) does not have to change.

**Every user-visible string this milestone introduces is translatable, and a test proves it.**
`BankTranslationKeysTest` enumerates every key `BankStatusPresenter` can emit — all 7 wire kinds ×
4 operations, plus 7 client-side validation statuses — and asserts each exists in `en_us.json`, is
non-blank, and is not duplicated. A missing key does not crash; Minecraft renders the raw key on
the parchment, which no compiler and no other test here would catch.

**Key prefix corrected from Milestone 1.** D13 proposed `gui.britannia_mod.bank.*`; the repository
already had `screen.britannia_mod.service_npc_spawn.title`, so the epic follows
`screen.britannia_mod.bank.*` rather than introducing a second convention. Milestone 1 has been
amended.

---

## 4. Required states

**Buttons.** Vanilla `Button` already draws normal, hover, keyboard focus, pressed and disabled.
The sixth, pending, is genuinely different from disabled — disabled means "you cannot do this",
pending means "you already did this and the server has not answered" — and rendering them alike is
how a player ends up clicking twice. `BankActionButton` distinguishes them two ways, neither of
them colour: the label swaps to a caller-supplied pending label, and a marker is drawn at the
leading edge. The marker matters because a translation may render the two labels similarly.

**Status.** Five severities, each carrying a colour, a marker width **and** a bold flag, because
design §16 forbids colour being the only indicator. Reconciliation-required versus ordinary
rejection is the pair where that matters most — it is the one status in this system with real
consequences for being misread — and a test asserts both non-colour signals differ.

Two behaviours corrected relative to the legacy screens:

- **Status text now wraps.** `BankScreen` draws it with a single unwrapped `drawString`, so the
  ~150-character reconciliation message runs off the right edge at most widths. Milestone 0 §3.5
  recorded that as the most visible defect in the current UI.
- **`PENDING_DELIVERY` is no longer a rejection.** `BankScreen` maps it to the clean-rejection
  message, which both alarms the player and hides that Rails already confirmed and the cheque is
  real. It is now `INFORMATIONAL` with its own text.

---

## 5. Tests

```
BankDialogueLayoutTest      17 tests
BankStatusPresenterTest     11 tests
BankTranslationKeysTest      4 tests
                            --------
                            32 new

Full JUnit suite           444 tests, 0 failures, 0 errors   (was 412)
```

Playbook coverage: narrow screen, wide screen, multiple GUI scales, wrapped dialogue, three
right-side buttons, two right-side buttons, and form controls without overlap — all present, plus
the exhaustive width sweep and the translation-key checks.

GameTests were not re-run: this milestone adds client-only classes and one client method, and the
suite's one failure is already proven pre-existing and unrelated (Milestone 2 §5).

**Not covered, by decision:** `BankDialogueFrame` and `BankActionButton` contain only drawing
calls and are unreachable without a client — Decision 0. Their correctness is the owner visual
gate's to judge, at Milestones 4, 5 and 7 when real screens use them.

---

## 6. Milestone report

**Files added.**
- `client/screen/bank/BankDialogueLayout.java`
- `client/screen/bank/BankStatusPresenter.java`
- `client/screen/bank/BankDialogueFrame.java`
- `client/screen/bank/BankActionButton.java`
- `src/test/.../bank/BankDialogueLayoutTest.java`
- `src/test/.../bank/BankStatusPresenterTest.java`
- `src/test/.../bank/BankTranslationKeysTest.java`

**Files changed.**
- `client/gui/DialoguePresentation.java` — added `text(Component)`; nothing existing altered
- `assets/britannia_mod/lang/en_us.json` — 14 keys
- `UltimaCraft_Bank_Interface_Rebuild_Milestone_1_Architecture.md` — key prefix correction

**Decisions made.** Two, both within Milestone 1's locked architecture:
- The key prefix follows the repository's precedent (§3).
- `BankDialogueLayout` is a new class rather than a fix to `DialogueLayout`, to avoid changing
  quest and service dialogue geometry (§2).

**Tests run.** `./gradlew test` — 444 pass, 0 fail.

**Unresolved blockers.** None.

**Scope explicitly not performed.**
- No screen was built. `BankMainScreen`, `BankBalanceScreen` and the cheque rebuild are Milestones
  4, 5 and 7.
- `BankScreen` and `BankChequeIssuanceScreen` keep their hard-coded English and their own status
  rendering. They are replaced at Milestone 4 and deleted at Milestone 19; converting them now
  would be work thrown away.
- No `renderDialogue` overload on `DialoguePresentation` — `BankDialogueFrame.renderHeader`
  supersedes it for this epic, and adding an unused overload to a shared class is worse than not.
- `DialogueLayout`'s negative-width bug was **not** fixed (§2). Flagged, out of scope.
- The `DialoguePresentation` file still lives at `client/gui/` while declaring package
  `client.screen`. Not normalized — pre-existing, and out of scope per the corrected design note.

---

## 7. Acceptance gate

> *A reusable dialogue frame can render the three required screen shapes.*

The three shapes are three-buttons-no-form (Main), two-buttons-no-form (Balance), and
two-buttons-with-form (Create Cheque). All three are exercised in `BankDialogueLayoutTest`, and
each holds the no-overlap and on-screen invariants across the full width range.

**One honest qualification:** "can render" is demonstrated at the layout and vocabulary level, not
by a screenshot, because no screen exists to render yet — Milestones 4, 5 and 7 build those. That
ordering is the playbook's own. The first visual confirmation of this frame arrives at Milestone
4's gate, and if it looks wrong there, the fix lands in this frame rather than in the screen.

Playbook §3.1: **all user-visible strings introduced by this milestone are translatable**, enforced
by `BankTranslationKeysTest`.

**Stopping here for owner review.** Milestone 4 (Bank Main Screen) is next, and it carries the D9
refresh cutover — the point at which the session built in Milestone 2 starts driving what the
player sees.
