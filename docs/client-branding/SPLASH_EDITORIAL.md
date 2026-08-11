# UltimaCraft Splash Editorial Record

## Acceptance contract

The final corpus contains 160 original lines. Each line was reviewed as an independent title-screen message after the source pass in `SPLASH_RESEARCH.md`.

- UTF-8 without a byte-order mark.
- One trimmed, non-empty line per row.
- Ideal and enforced maximum: 55 Unicode code points; hard product ceiling: 70.
- No exact, case-insensitive, or punctuation-insensitive duplicates.
- No canonical match to any normal Minecraft 1.21.1 splash.
- No copied quote, profanity, malformed character, or unsupported precise claim.
- Normal Minecraft random selection remains responsible for rotation.

## Composition

| Editorial family | Lines | Share | Purpose |
|---|---:|---:|---|
| Virtues, lore, people, dungeons, and world | 42 | 26.25% | Establish the Ultima voice and foundational vocabulary. |
| Cities, landmarks, and local travel | 50 | 31.25% | Give Britannia geographic texture rather than repeating generic fantasy jokes. |
| Classic play, NPCs, magic, banking, and skills | 40 | 25.00% | Reward player recognition of day-to-day systems. |
| Implemented UltimaCraft mechanics | 18 | 11.25% | Tie the corpus to this project instead of only to franchise history. |
| Branding, display, and build meta | 10 | 6.25% | Add a restrained modern layer and title-screen self-awareness. |
| **Total** | **160** | **100%** | |

## Scoring rubric

Every accepted line was scored from 0 to 2 on six dimensions. Acceptance required at least 9/12 and an accuracy score above 0.

| Dimension | 0 | 1 | 2 |
|---|---|---|---|
| Accuracy | Incorrect or unsupported | Safe whimsical nod/general truth | Directly grounded in a recorded source |
| Relevance | Generic filler | Broad fantasy/series relevance | Specific Ultima or UltimaCraft recognition |
| Humor/charm | Flat or awkward | Pleasant | Distinct comic turn or warm character |
| Clarity | Confusing | Understandable with context | Immediate at splash-reading speed |
| Brevity/fit | Exceeds hard limit | Fits but reads long | Compact and visually comfortable |
| Originality | Derivative/cliche | Familiar construction | Fresh wording or project-specific angle |

The accepted set's weakest dimension is intentionally allowed to be a 1, but no accepted line falls below 9 total. Direct factual jokes generally score 2 for accuracy and relevance; playful character asides score 1 for accuracy because they are framed as obvious invention rather than canon claims.

## Representative decisions

| Candidate | A | R | H | C | B | O | Total | Decision |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| `Moonglow has teleporters for its teleporters.` | 2 | 2 | 2 | 2 | 2 | 2 | 12 | Accept; concise exaggeration rooted in the city's teleporter maze. |
| `Minoc has ore and no guest rooms.` | 2 | 2 | 2 | 2 | 2 | 2 | 12 | Accept; two official city facts make the turn. |
| `No guards. No refunds. Buccaneer's Den.` | 2 | 2 | 2 | 2 | 2 | 2 | 12 | Accept; accurate lawless-city premise and strong rhythm. |
| `The Guardian left a voicemail.` | 1 | 2 | 2 | 2 | 2 | 2 | 11 | Accept; unmistakably whimsical antagonist nod, no factual claim. |
| `Copper, silver, gold: sorted.` | 2 | 2 | 1 | 2 | 2 | 2 | 11 | Accept; grounded in project currency and useful tonal variety. |
| `The splash survived GUI scale four.` | 2 | 2 | 1 | 2 | 2 | 2 | 11 | Accept; milestone-specific self-reference validated live. |
| `Another adventure awaits!` | 1 | 0 | 0 | 2 | 2 | 0 | 5 | Reject; generic fantasy filler. |
| `Trammel is objectively better.` | 0 | 2 | 1 | 2 | 2 | 1 | 8 | Reject; unsupported value claim and faction bait. |
| `Iolo said a famous line from a game.` | 0 | 2 | 0 | 1 | 2 | 0 | 5 | Reject; quotation risk and no useful joke. |
| `This extremely detailed splash explains every bank transfer operation.` | 2 | 1 | 0 | 1 | 0 | 1 | 5 | Reject; too long and unsuitable for rotated display. |

## Automated validation

`ClientBrandingSplashCorpusTest` enforces encoding, count, whitespace, non-empty content, the 55-code-point limit, control-character exclusion, exact/case/canonical uniqueness, and absence from the normal vanilla 1.21.1 corpus. The vanilla comparison fixture stores only sorted 64-bit SHA-256 prefixes of canonical lines: 441 unique canonical hashes derived from all 446 source rows. It does not redistribute vanilla splash copy.

The validator supplements editorial review; it cannot prove humor, factual accuracy, or originality by itself. Those judgments are recorded here and supported by the research ledger.
