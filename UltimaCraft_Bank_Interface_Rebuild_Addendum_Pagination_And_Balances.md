# Addendum — Vault pagination and currency balances on the buttons

**Status:** Complete, awaiting owner review
**Baseline:** NeoForge `banking` @ `0fd78cc`
**Authority:** Owner improvements, 2026-08-04.

---

## 1. A hundred stored items — what was actually missing

The owner asked that 100 items be storable and expected pagination. The audit found **storage
and transport already handle any count**: nothing count-caps a deposit (the weight limit is the
only ceiling), Rails' `banking/open` sends *every* available item in one page (`next_cursor` is
always null in the controller — verified in code, not the docs), and the client parses up to
10,000. The row-scroll engine (`BankGridScroll`, Milestone 9) also handles any count via the
wheel. What was missing was **visibility**: nothing told the player more rows existed, and there
was no clickable way to move.

**Recommendation taken: pagination as presentation over the existing scroll, not a replacement
of it.** A "page" is one full grid view (five visible rows = 45 items, so 100 items are three
pages). Two arrow buttons flank a "1 / 3" line on the chest's lid, directly above the grid they
page; the wheel keeps working, and both drive the same scroll position. `BankGridPager` is plain
arithmetic over `BankGridScroll` — page count, current page, snap-forward/back — with the one
subtle case tested: the last page clamps so the view stays full, and the clamped position always
reads as the last page however it was reached.

The lid's pager line is **reserved even when unpaged** (the arrows and text simply don't render):
making the line conditional would couple the lid height to the row count, whose computation
depends on the lid height — a circularity not worth eleven pixels. The plain no-chest fallback
gains the same third row. When one page holds everything, the pager is invisible entirely — a
"1 / 1" with two dead arrows would be noise.

## 2. Balances on the currency buttons

Each denomination button now carries its live balance on a second line — "Gold" over "1.25k" —
so the vault's holdings are visible without visiting the Balance screen. The rows grew from 20
to 26 pixels to hold two lines honestly (`CURRENCY_ROW_HEIGHT`), and the control column still
fits its panel across the whole size sweep.

`BankBalanceAbbreviation` owns the formatting, plain and tested:

- below 1,000: the plain number;
- then k / m / b with at most two decimals, trailing zeros trimmed — "1k", "1.25k", "2.14b".
  The owner specified the thousands tier; the higher tiers follow because the requirement is
  *"the text fits within the button frame"* and the balance column is int32, so 2,147,483,647 is
  a value the button will eventually be handed. Seven characters is the provable worst case;
- decimals are **truncated, never rounded up** — a money display must not overstate what the
  player has, so 1,999 reads "1.99k", not "2k".

The button reads what the screen tells it each frame (`BankCurrencyButton` renders two lines and
computes nothing); the screen reads the session, so a refresh push changes the number the same
frame it changes the balance.

## 3. Tests

```
New JUnit tests             12   (6 pager arithmetic, 6 abbreviation rules)
Full JUnit suite           652 pass, 0 fail   (was 640)
All GameTests              333 run, 332 pass  (unchanged -- client-only work; the sole failure
                                               remains the pre-existing bootstrap flake)
```

The pager tests walk the owner's own case throughout — 100 items, 9 columns, 5 rows: three
pages, next/previous with both clamps, a wheel position between anchors, and a refresh that
shrinks the vault under the pager. The abbreviation tests pin the owner's examples ("1k",
"1.25k"), trimming, truncation-not-rounding, the higher tiers, and the seven-character bound.
The lid-minimum layout test was strengthened from two reserved text rows to three.

## 4. Report

**Decisions made.** Pagination as a presentation of the tested scroll rather than a rewrite
(§1); the reserved lid line over a circular conditional (§1); the pager hidden entirely at one
page; k/m/b tiers beyond the requested k, because the real requirement is fitting the frame;
truncation over rounding for money; taller currency rows rather than squeezing two lines into 20
pixels.

**Not performed.** No Rails change (none needed — verified against the controller, not assumed).
No change to Rails' single-page delivery: if a vault ever grows toward the 10,000-item parse
bound, cursor-following becomes its own piece of work, and today's 100-item target is two orders
of magnitude away.

**Worth trying in-game:** deposit past 45 items and watch the pager appear with "1 / 2"; click
through pages and wheel-scroll between them (both move the same view); check the buttons read
your real balances and that a withdrawal updates them on the refresh; deposit to exactly one
page and watch the pager vanish.

---

## 5. Bank sounds (owner, same gate)

Four sounds, two already half-wired: `chest_open`/`chest_close` had their events and
`sounds.json` entries; `hit02.ogg`/`leather1.ogg` were on disk but unregistered. The new events
are named for what happened, not which file plays — `bank_deposit`, `bank_withdraw` — so
swapping the audio later is a `sounds.json` edit, not a code rename.

**Where each plays, and why there:**

- **Open** — at the Bank Box navigation click on Main, not in the Box's `init`, which re-runs on
  every window resize and would creak the lid at each one.
- **Close** — Back and a real close (Escape) alike, guarded so a screen that failed to open (the
  session-null init bail routes through `onClose` too) does not thud shut a box that never
  creaked open.
- **Deposit / withdraw** — on **success**, and the refresh push IS the success signal (the
  epic's own doctrine): a rejection arrives as a result payload and stays silent, so the thud
  never lies about an item that was refused. Captured for the same teller only. Cheque
  operations stay silent; nothing was asked for them, and both already produce their own visible
  outcome.
- **Coins** (`gold_coin`, owner follow-up) — whichever way they move, and including Deposit All
  Coins. This needed a bit the wire does not carry: `Operation` is coarse by Milestone 2's own
  choice (a currency deposit and an item deposit are both `DEPOSIT`), because the mutation lock
  never needed the distinction. The **sender** always knows, so it now says so when it claims the
  lock — the coin buttons and the sweep by construction, and a drag by inspecting the stack under
  the cursor with the same top-level-identity test the server routes on (a container holding
  coins is not a coin stack, so a shulker of gold thuds like the item it is). The routing
  decision itself stays server-side; the client only predicts which noise success will make.
  `BankMutationCue` owns the mapping, is Minecraft-free and tested; the handler maps a cue to a
  registered event in four lines.

Guarded by `BankSoundAssetsTest`, the `sounds.json` sibling of the translation-key test: a
typo'd event name or a missing `.ogg` plays silence rather than crashing, which is exactly the
defect no compiler would catch. All five events are pinned to their files, `gold_coin` included
— it pre-existed, and pinning it stops an unrelated edit from silently breaking banking's use of
it.

Tests after the coin follow-up: **658 JUnit pass, 0 fail**; GameTests 333 run, 332 pass (the
registry was touched, so the full suite was re-run; the sole failure remains the bootstrap
flake).
