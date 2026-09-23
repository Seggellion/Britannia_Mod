# Starfarer’s Medallion Phase 2 — Patch-18 Acceptance

2026-09-22 · P2-M6 closeout

## Verdict

**PASS WITH VISUAL ACCEPTANCE PENDING.** P2-M6 lore and automated gates pass. The owner approved advancing with the walking/running, swimming/crawling, and cape visuals explicitly deferred. Those cases are not visual passes, and this dirty-tree jar is **not** a deployable release candidate.

## Final P2-M6 change

- The translatable name is `Starfarer’s Medallion`.
- The always-visible tooltip contains, in order:
  1. `Bestowed upon travelers who answered a call from beyond the skies of Britannia.`
  2. `Struck for those who answered the call.`
  3. `Squadron 42 - Manchester, 2956`
- The obsolete `October 2026` occasion is gone. The tooltip remains three restrained lines beyond the item name; no item behavior, renderer, model, or texture changed in P2-M6.
- The contract test pins all four exact English strings and the tooltip key order.

## Patch-18 Git state

| Check | Result |
| --- | --- |
| Checkout / branch | `C:\projects\britannia\mod\Britannia_Mod` / `patch-18` |
| Starting and ending HEAD | `c1f2b5b819834ab971a2fc65b86fcd484d31e7c9` |
| `origin/patch-18` | Same SHA; ahead/behind `0/0` |
| Patch-19 isolation | `git merge-base --is-ancestor patch-19 HEAD` exits 1; `HEAD...patch-19` has 3 Patch-18-side and 5 Patch-19-side commits. No Patch-19-only commit entered HEAD. |
| Dependency isolation | No `build.gradle` or `gradle.properties` diff; no Patch-19 dependency introduced. |
| Working tree | Dirty with the uncommitted Phase 2 code/assets/tests and project notes. No Phase 2 commit, push, merge, or deploy. |

## Automated verification

| Gate | Result |
| --- | --- |
| Focused `StarfarersMedallionContractTest` | PASS: 22 tests, 0 failures/errors/skips. Includes asset, lore, wearable, and server/client boundary contracts. |
| Full `test -PallowDirty --no-configuration-cache --quiet` | PASS: 4,114 tests across 512 suites; 0 failures, 0 errors, 23 skips. |
| Dedicated `runGameTestServer --no-configuration-cache --quiet` | PASS: all 1,301 required GameTests. The seven P2-M4 wearable lifecycle GameTests remain included. |
| `build -PallowDirty --no-configuration-cache --quiet` | PASS. This also builds the dedicated-server-safe common artifact. |
| `git diff --check` | PASS. Git emitted only LF/CRLF conversion warnings. |
| Built-jar localization inspection | PASS: all four approved strings are present in `assets/britannia_mod/lang/en_us.json` inside the jar. |

The GameTest server logged non-fatal external skill-service fetch/credential warnings also seen in prior runs; its final summary reported all required tests passing. Existing compile deprecation warnings remain outside this project’s scope.

## Artifact

| Item | Final state |
| --- | --- |
| Canonical ID | `britannia_mod:starfarers_medallion` |
| Equipment | Vanilla `Equipable`, `CHEST`, owner-only; chestplate/elytra conflict accepted |
| Gameplay | Stack size 1; no armor, food, durability, or stat bonus |
| Renderer | Client-only vanilla layer on `PlayerModel.body`; same item model for every context; both wide and slim registrations |
| Model | `src/main/resources/assets/britannia_mod/models/item/starfarers_medallion.json`; 9 vanilla elements; SHA-256 `1156395AE69436B1C831F02FAF407046FE875C78990F363FED48D8D398E3116D` |
| Editable source | `src/main/resources/assets/britannia_mod/models/item/starfarers_medallion.bbmodel`; SHA-256 `B7F577EB5FC1B813079981A6B477461B9A474E20C3D6B215DD1D06EE8BA8696A` |
| Texture | `src/main/resources/assets/britannia_mod/textures/item/starfarers_medallion.png`; 64×64 RGBA; SHA-256 `6C03491D2063DB4DA4E6394B6D513D04A03292DB0D0EC6CBC9C416150F4DFA52` |
| Placeholder | Gold-ingot model reference removed |
| Local jar | `build/libs/britannia_mod-0.1.8c-all.jar`; SHA-256 `7D9674BF1B0F242A9E211B6B787415E3C860EB1ECB89E66A7A584FB86938D607`; **dirty-tree build, not for deployment** |

## Acceptance matrix

| Concern | Status | Evidence / limit |
| --- | --- | --- |
| Owner-only right-click and armor-slot equip, identity, death/drop/rescue, bank/display | PASS | Unit and GameTests; no new lifecycle authority or special casing. |
| Wide and slim standing views | PASS | In-client shader QA on separate owner-stamped Dev and AlexQA profiles. |
| Static front/back, angled/profile, elevated, crouched, and minecart riding | PASS | Saved P2-M5 shader QA views; upright coin and bail below chin, no obvious static overlap. |
| GUI/hotbar, first- and third-person hand, ground, item frame, creative search, display case | PASS | In-client P2-M5 views and asset contracts. |
| Helmet coexistence; chestplate/elytra mutual exclusion | PASS | Leather helmet and CHEST-slot swaps checked in-client. Elytra flight while wearing the medallion is not applicable because both use CHEST. |
| Walking/running | DEFERRED WITH JUSTIFICATION | Available UI controls did not hold movement long enough for reliable animation capture. Owner approved advancing without calling it a pass. |
| Swimming/crawling | DEFERRED WITH JUSTIFICATION | Low-clearance crawl silhouette appeared, but camera collision obscured the medallion. Owner approved advancing without calling it a pass. |
| Cape interaction | DEFERRED WITH JUSTIFICATION | Offline QA profiles had no cape. Owner approved advancing without calling it a pass. |
| Multiplayer tracking/respawn visual | DEFERRED WITH JUSTIFICATION | No second client/profile session was available for this optional manual view; server lifecycle coverage passed. |
| Final tooltip appearance in-client | PASS | On a retry in the saved Dev QA world, the inventory tooltip displayed the approved name, lore, provenance, and occasion in order. The lore wrapped cleanly and the tooltip fit within the 685×416 window. |

An earlier tooltip-check `runClient` attempt was interrupted after the UI helper failed twice to activate the returned game window; its exit code 1 reflected that interruption, not a failed automated test. The retry waited for the main menu, opened the QA world, verified the tooltip visually, saved the world, and quit normally (`runClient` exit 0). An Iris shader preprocessor warning (`#endif without #if`) was logged during the interrupted attempt, but the retry rendered the game and tooltip successfully.

## Release hold / next action

At the original P2-M6 checkpoint, the implementation and automated gate were complete, but a release candidate still needed a clean, intentional commit/build and the remaining visual acceptance or an explicit release waiver. No commit or push had been requested or performed at that checkpoint. Do not deploy the local jar.

## Local-commit continuation — 2026-09-22

With the owner's explicit approval, the 12 audited Phase 2 implementation, asset, and test files were committed locally on `patch-18` as `4a0feb03707e5c584551f722a33e96a5b01ca3c7` (`feat(starfarer): complete Patch-18 wearable medallion`). The code and asset contents match the previously passing P2-M6 build; `git diff --cached --check` passed before commit. No unrelated file was staged.

Before the separate documentation commit, the branch was one commit ahead of `origin/patch-18`, with no push or deployment. `patch-19` was still not an ancestor of HEAD; `HEAD...patch-19` was 4/5. The owner-supplied kickoff and playbook remain untracked and were deliberately excluded from the implementation and documentation commits. Because untracked files count as dirty for `verifyDeployableJar`, the existing jar remains a dirty-tree local build, **not a release candidate**. No post-commit clean build is claimed. Walking/running, swimming/crawling, cape, and optional multiplayer visuals remain deferred, not passed; release remains on hold pending visual acceptance or an explicit release waiver.
