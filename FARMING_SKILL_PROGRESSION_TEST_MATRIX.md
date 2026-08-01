# Farming Skill Progression Test Matrix

Milestone: 14 - Data Model and Registration Integration
Status: Approved

| Area | Required evidence | Automated coverage | Current result |
|---|---|---|---|
| Catalog coverage | 74 catalog species | Proposal/catalog/runtime reconciliation parser | PASS - focused suite |
| Proposal coverage | 74 approved rows; no missing or extra species | Proposal parser plus centralized roster validator | PASS - focused suite |
| Definition coverage | 74 explicit values: 67 crops and 7 flowers | Runtime definition reconciliation | PASS - focused suite |
| Planting mappings | 74 unique proposal/catalog item IDs; runtime common-setup resolution | Reconciliation test plus bootstrap validator | PASS - reconciliation and isolated server bootstrap |
| Exact values | Every runtime value equals its approved proposal row | Proposal parsed during test; no second Java value table | PASS - focused suite |
| Missing/extra species | Fail with identified sets | Central roster validator fixtures | PASS - focused suite |
| Duplicate/conflicting species | Duplicate ID and conflicting value fail with species/value/source | Central validator fixtures | PASS - focused suite |
| Duplicate/ambiguous item | Shared planting item fails with item and both species IDs | Central validator fixture | PASS - focused suite |
| Range | Negative and above 100 fail | Central value validator fixtures | PASS - focused suite |
| Non-finite float | NaN and positive/negative infinity fail | Central value validator fixtures | PASS - focused suite |
| Missing requirement/item | Missing metadata fails; zero remains valid | Central registration fixtures and explicit constructors | PASS - focused suite |
| Sentinels | Carrot/Lettuce/Green Onion/Wheat 0; seven flowers; Nightshade 85; Mandrake 90; Orfluer 95 | Proposal and runtime sentinel assertions | PASS - focused suite |
| Flowers | All seven definitions implement the shared field | Definition count and reconciliation | PASS - focused suite |
| Poppy separation | Ordinary 20; stage-7 mastery 100 | Independent constant/definition assertions and startup validation | PASS - focused suite |
| Fruit trees | Nine overlays resolve to crop-owned requirements without duplication | Architecture test and startup item check | PASS - focused suite |
| Vanilla boundary | No native planting interception/gating | Source boundary assertion and scoped diff audit | PASS - focused suite |
| Grape boundary | Crop metadata 80; existing grape planting/NBT/names untouched | Exact reconciliation and source boundary assertion | PASS - focused suite |
| Seed names | No name, tooltip, narration, or `a brown seed` implementation | Source boundary assertion and scoped diff audit | PASS - focused suite |
| Planting behavior | No denial, player eligibility, feedback, bypass, or automation policy | Source boundary assertion and scoped diff audit | PASS - focused suite |
| UI/sync | No player-specific naming, tooltip, narration, or skill sync | Scoped diff audit | PASS - focused suite |
| Creative/admin | No Milestone 15/16 enforcement or bypass consumer | Scoped diff audit | PASS - focused suite |
| Automation | No attributed/unattributed automation enforcement | Scoped diff audit | PASS - focused suite |
| Saves | No requirement field in NBT/components or migration | Scoped diff audit and compatibility analysis | PASS - inspection |
| Network | No packet or synchronization change | Scoped diff audit | PASS - inspection |
| Farming/flower regressions | Existing compile/resource/test suite | Full Gradle validation | PASS - clean full suite |
| Placeholder integrity | Corrective Milestone 11 generator check remains non-writing | `generate_flower_placeholders.py --check` | PASS - 182 files plus ledger |
| Whitespace | Repository diff check | `git diff --check` | PASS |

## Focused test command

~~~text
.\gradlew.bat test --tests com.seggellion.britannia_mod.farming.FarmingSkillRequirementTest --console=plain --no-configuration-cache
~~~

The focused suite passed after using the repository-standard Minecraft registry bootstrap. The complete clean test/resource/build checks, non-writing placeholder check, and final diff audit also passed. An isolated game-test dedicated server reached common setup and logged validated counts of 74 species, 67 crops, 7 flowers, and 74 planting items without opening the configured development world. The empty GameTest harness then reported that it had no test functions, and Gradle exited successfully. A pre-existing dedicated-dist warning for `TitleScreenBackgroundMixin` is unrelated to Milestone 14; the progression classes have no client imports.
