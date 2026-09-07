# UltimaCraft gameplay bugfix handoff

Final classification: **PASS WITH EXTERNAL RUNTIME ACCEPTANCE PENDING**, using M10’s permitted pending exit. All local automated and clean-release gates have passed. Fifteen issues have implemented functional changes; BUG-01 remains pending controlled shader reproduction and any correction it supports. BUG-04, BUG-06 and BUG-07 are excluded artwork. The full watering-can artwork is **PENDING_USER_ASSET**.

The exact remaining acceptance work is named below. Tests establish server behavior, state conservation and local HTTP settlement; they do not establish physical client gestures or visible shader/HUD results. M10 uses the playbook’s permitted bounded-audit exit because concurrent desktop input prevented the controlled comparison.

## Workspaces and local commits

| Repository | Isolated worktree | Branch | Starting HEAD | Validated source HEAD |
|---|---|---|---|---|
| NeoForge | `C:/projects/britannia/mod/Britannia_Mod/.claude/worktrees/patch18-gameplay-bugfixes` | `codex/patch18-gameplay-bugfixes` | `421e27853dde4099d1d794568e33e6709507a53b` | `4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67` |
| Rails | `/home/dusti/ultimacraft-website/.claude/worktrees/patch18-gameplay-bugfixes` | `codex/patch18-gameplay-bugfixes` | `a9425ca41afa9b4ba7539426044e7b50e70a3966` | `4e67f4c41c4d6f531c673e11fd6b2dbd71b59e18` |

Rails ending HEAD is `0646020b331a241222e7c38251e1f0749503661a` (documentation closure, clean worktree). The ending mod documentation SHA is recorded in the final immutable local snapshot, `tmp/gameplay-bugfixes/release/FINAL_HANDOFF.md`, after this documentation commit exists. The artifact’s embedded source HEAD remains the clean tested source above; closing documentation changes do not alter its code/resources.

| Mod milestone | Full local commit | Subject |
|---|---|---|
| M0 | `a95538d862a6c9316d7f45077fbc620e3241d097` | docs(gameplay): freeze bugfix contract and isolated baseline |
| M1 | `f31550eefb215e198afc60a35dec355661a5f173` | fix(farming): restore timed soil and hydrate from either bowl hand |
| M2 | `558b5236ef3d4db55e282e3c3b5193f7cd50ea4d` | fix(crafting): resolve hand roles and merge compatible outputs first |
| M3 | `c6496588042b8109266d55443c3b1fe68ee062e7` | fix(display-case): eject transactionally and rotate the complete case |
| M4 | `cabc1050dba8d3d9865623fe9beacc2f7f89033d` | fix(farming): unify species gates and commit paid harvest outcomes |
| M5 | `b8101aed6c2bb6a14835fbb8abf4ddd2510fa041` | fix(farming): acknowledge committed planting and expose synchronized plot state |
| M6 | `2ec14a9722a6c3dee24428e13fed1afb3055627b` | fix(worldgen): suppress only random pumpkin and melon patches |
| M7a | `5c195339a33f8e79fcdd1d4589ceec45c23a4ae4` | fix(economy): map approved produce and verify real Rails buyback |
| M7b | `934790ec7ec45b193cdff299a876dceb2b611603` | fix(economy): recover trader sales by durable receipt and current player |
| M8 | `ea8499f764fcd059edf4d4b0a0cfc98c0d9db8ea` | fix(fences): derive stable connections and preserve paths with full-height collision |
| M9 | `65bc1a6a21f04df2a222c971c1494b1729c275d4` | fix(decorations): allow Creative substrates while preserving structure validity |
| M10 bounded audit | `991fb1f33e6f85b38df2b4385d9c97ee5a652333` | docs(gameplay): record clean shader baseline and remaining client acceptance |
| M11 test closure | `4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67` | test(gameplay): reconcile full-suite contracts and durable refund checks |

| Rails milestone | Full local commit | Subject |
|---|---|---|
| M7a | `6cc948f9a814add4f4a7b8a4f122514d9ed45bf8` | fix(economy): backfill approved produce and route economic NPC sales |
| M7b | `a506d67d1b478789c2a68094b5d081a4e9afb924` | fix(economy): resolve committed sale receipts after response loss |
| M11 test isolation | `f5ec7915d6189fa2eb7208479b2eb137ed1fd67b` | test(admin): scope refusal and reconciliation assertions to their operations |
| M11 regression correction | `4e67f4c41c4d6f531c673e11fd6b2dbd71b59e18` | fix(regression): remove redundant page queries and scope restore audits |
| M11 Rails documentation closure | `0646020b331a241222e7c38251e1f0749503661a` | docs(gameplay): close Rails regression and record production read-only evidence |

M7a and M7b are paired mod/Rails boundaries. M8, M9 and the M10 audit remain separate commits. M11 also resolved inherited source-test assumptions and public-page query overhead exposed by full regression; the query budgets were retained.

## Nineteen-issue status

| Issue | Report | Outcome / remaining acceptance |
|---|---|---|
| BUG-01 | Moongate with Photon | PENDING M10-SHADER-3WAY; renderer unchanged. Controlled reproduction and any supported correction remain. |
| BUG-02 | Planting feedback / plot HUD | Implemented: committed feedback and authoritative plot state. Graphical aiming, layout and reconnect pending. |
| BUG-03 | Empty fertilized soil expiry | Implemented: 1,200 online ticks, exact prior hoed state, unused fertilizer forfeited. Physical timing/restart pending. |
| BUG-04 | Lettuce artwork | EXCLUDED manual asset polishing; assets untouched. |
| BUG-05 | Bowl hydration | Implemented: either hand, +1 hydration, one custom empty bowl, free refusal on full/protected targets. Client input pending. |
| BUG-06 | Green-onion artwork | EXCLUDED manual asset polishing; assets untouched. |
| BUG-07 | Green-onion artwork | EXCLUDED manual asset polishing; assets untouched. |
| BUG-08 | Planting / harvest skill consistency | Implemented across 67 crops and 7 flowers; grapes require 80. Paid probabilistic outcomes and lifecycles tested. |
| BUG-09 | Fertilizer target restrictions | Implemented: prepared community or empty vanilla farmland inside the acting player’s owned house; live authority revalidated. |
| BUG-10 | Creative decorative substrates | Implemented across bounded family audit; technical footprint, collision, permissions and part integrity retained. Client/restart pending. |
| BUG-11 | History-dependent fence joins | Implemented: deterministic final topology, bounded loaded-state reconciliation; authored models unchanged. Visual comparison pending. |
| BUG-12 | Custom fence collision | Implemented: occupied arms/posts 1.5 high, outline 1.0; Survival/Adventure server movement probes pass. Keyboard movement pending. |
| BUG-13 | Dirt path under custom fence | Implemented: actual vanilla-shovel path survives custom fence and queued updates; oak control retained. Actual restart pending. |
| BUG-14 | Full watering-can state | Functional selection implemented: 12 full, 0–11 base, legacy missing data 12. PENDING_USER_ASSET and final display acceptance. |
| BUG-15 | Random landscape pumpkin / melon | Implemented: exactly three random features removed. Existing blocks, structures and intentional cultivation preserved. Normal-terrain survey/restart pending. |
| BUG-16 | Swapped-hand pseudo-crafting | Implemented: role-based three bowl recipes and seven pigments, one MAIN-phase commit. Physical input pending. |
| BUG-17 | Final crafted stack merge | Implemented: compatible stacks first, exact components/remainders and full-inventory conservation. Menu/reconnect display pending. |
| BUG-18 | Produce trader coverage / settlement | Implemented: all 36 additions, exact mapping, insert-only prices, real local Rails receipt/recovery proof. Production backfill/player acceptance requires separate release authority. |
| BUG-19 | Display-case decorator | Implemented: offhand exact world ejection; mainhand atomic whole-case rotation. Two-client display/restart pending. |

## Implemented decisions

Empty fertile soil expires after 1,200 online simulation ticks, restores the exact saved hoed state and loses the application and all unused fertile uses. Planting cancels the deadline; community preparation time pauses and resumes without a fresh allowance. Fertilizer accepts prepared community soil and empty vanilla farmland within the acting player’s registered owned-house bounds. Buckets, cans, bowls and rain retain hydration behavior; a bowl adds one hydration and returns one custom empty bowl, preserving initial fertilizer moisture.

All 74 cultivated species use matching planting/harvest thresholds. Eligible success is `min(95%, 75% + 1% × Farming surplus)`. Failure destroys produce/byproducts, consumes normal durability/fertility, runs the existing practice opportunity, and follows annual/perennial/tree lifecycle. Creative/operator-level-2 success is deterministic and free. Native cultivation outside the custom system remains vanilla. Feedback follows committed planting, and plot HUD state requires authoritative server data.

The three bowl recipes and seven pigments resolve actual hand roles. One MAIN callback owns the gesture; the redundant OFF callback cannot craft twice. Compatible component-equal stacks receive output first. Display-case offhand use ejects the exact saved item into the world; mainhand/both tools rotate both cells under one guarded transaction with rollback.

Worldgen suppression removes only `minecraft:patch_pumpkin`, `minecraft:patch_melon` and `minecraft:patch_melon_sparse` from Overworld vegetal decoration. It neither scans nor deletes old chunks/blocks. Structure templates, native stems, custom crops, recipes and random tick speed are retained.

Fences converge from final topology, preserve intentional isolated facing, use 1.5-high occupied collision and retain an actual vanilla-shovel dirt path through a narrow custom-fence exception. Creative decoration bypasses gameplay substrate restrictions while bounds, loaded space, replaceability, block entities, collision, protection and structural parts remain authoritative.

## Produce policy, economics and live evidence

The byte-identical mod/Rails manifest contains 55 exact item IDs / 51 commodity keys and accounts for all 67 crop definitions: 50 supported and 17 excluded. SHA-256: `9bd07850fce0bf25906daba76ac95e25bf389cd1fb7f0fff49c36dd44934e5bc`.

All 36 approved additions are implemented: yellow_onion, green_onion, watermelon, beans, vanilla_melon, pineapple, strawberry, blueberry, raspberry, cranberry, blackberry, huckleberry, mulberry, elderberry, cherries, snow_peas, peas, turnips, lemon, lime, orange, olive, plum, bell_peppers, cucumbers, honeydew, cantaloupe, broccoli, cauliflower, rhubarb, celery, radish, parsnip, yam, rutabaga and grapes. Vanilla melon maps to `minecraft:melon_slice` / `produce|fruit|melon_slice`. Existing apple/carrot and other legacy mappings remain.

The complete exact item/category/subcategory/key/base-price/cap/comparable table is the handoff’s [produce mapping and prices annex](M7_PRODUCE_MAPPING_AND_PRICES.md). New prices use existing family/tier/lifecycle comparables: alliums 1.50, roots 1.60, gourds 2.20, ordinary annual/trellis produce 2.00, tree fruit 2.00, perennial grapes 2.50; caps follow existing 3,000 crop / 2,000 food conventions. Pineapple’s conservative 2.00 same-tier fallback is explicit. Existing prices, stock, buy flags, caps and curves are never reset. Existing dynamic quote/denomination/minimum/treasury/stock/revision controls remain.

Excluded crop definitions are wheat, rye, barley, oats, mustard, rice, garlic, ginseng, mandrake, nightshade, brown_mushroom, red_mushroom, cotton, flax, hemp, hops and tobacco. Seeds, cultivated flowers, processed food, reagents and textiles remain outside produce policy. Native carrot/potato produce-as-seed exceptions are intentional; beans are approved produce.

At `2026-09-07T08:28:11.16914Z`, an authenticated, read-only transaction against the verified UltimaCraft production app found the Britannia/Jhelom enabled produce post assigned to active **Zorah**, active/spawnable produce policy revision 2, and no Britannia override. Fifteen legacy rows existed; all 36 new keys were absent. Apple base/current 2/3 and carrots 1.8/2.7 were enabled controls. Exact post/assignment/NPC IDs and observations are in [production read-only evidence](M7_PRODUCTION_READONLY_EVIDENCE.md). No production sale or write was made. Deployment and the insert-only backfill are future separately authorized work.

Real local mod→Rails proof includes after-commit response loss, disconnect/rejoin from the actual dedicated-server player file, receipt lookup and one payout to the current UUID. The independent verifier found two receipts, quantities 3 and 2, grants 9 and 5 copper, stock 5 and treasury 486 from 500. Exact refund, replay, wrong policy and refusal cases also pass. [Sale recovery](M7_SALE_RECOVERY.md) describes uncertainty that remains deliberately pending instead of risking duplication, including unknown legacy DISPATCHED records and ambiguous retries.

## Tests and observed client evidence

| Gate | Executed result | Evidence |
|---|---|---|
| Clean normal release + full JUnit | 3,492 tests: 3,475 passes, 17 inherited skips, 0 failures/errors; 454 suites; build successful 6m38s | `m11-clean-release.log`, fresh XML and release identity JSON |
| Full registered NeoForge server | All 1,154 required GameTests passed in 2.244 minutes | `m11-gametest-final.log` |
| Real local Rails recovery + registered server | 1,142 GameTests and 27 focused JUnit passed, including opt-in real HTTP | `m7b-live-2.log`, [recovery evidence](M7_SALE_RECOVERY.md) |
| Relevant Rails economy gate | 208 runs, 1,974 assertions, 0 failures/errors, 1 existing opt-in parity-writer skip | `m7b-final-rails.log` |
| M11 Rails refusal/reconciliation correction | 18 runs, 81 assertions, 0 failures/errors/skips | `m11-rails-corrected-fresh.log` |
| M11 Rails query/auth/image/refusal correction | 55 runs, 337 assertions, 0 failures/errors/skips | `m11-rails-corrections.log` |
| Complete normal Rails regression | **3,190 runs, 51,848 assertions, 0 failures, 0 errors, 2 inherited conditional skips; all 435 normal test files covered.** | `m11-rails-final-partition-0.log` through `-7.log`; exact 435-file manifest |

All mod logs are under this worktree’s ignored `tmp/gameplay-bugfixes`; Rails logs are under the Rails worktree’s corresponding directory. [M11 regression evidence](M11_REGRESSION_EVIDENCE.md) records exact commands, earlier failures/corrections, skips and limits. The [scratchpad](ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md) retains chronological evidence and every focused milestone gate. Full default GameTests do not enable the optional external Rails fixture; its actual earlier run is recorded separately.

The packaged clean M10 baseline reached the welcome screen and a fresh normal singleplayer world. Fresh log evidence identifies Intel UHD Graphics, OpenGL 4.6, driver 32.0.101.5972. The normal noise world seed is `-6858865061500343773`. This is client startup/world-load evidence only. No moongate placement, three-way shader comparison, ordinary farming journey or physical multiplayer acceptance was completed. No occluded capture showing another app counts as Minecraft evidence.

## Remaining acceptance and smallest next actions

| Named gate | Exact remaining action |
|---|---|
| M10-SHADER-3WAY | With coordinated foreground desktop control, use the exact candidate in one fixed scene: no Iris/Sodium; Iris 1.8.0 + Sodium 0.6.0 shaders off; Photon v1.1. Compare placed/inventory/held, front/back, distance, animation, chunk edge and resource reload; verify player/mount teleport separately. Apply a renderer correction only if the comparison supports it, then repeat. [Setup and input hashes](M10_SHADER_ACCEPTANCE.md). |
| M11-ORDINARY-CLIENT | Physical prepare→fertilize→water→plant→HUD→grow→harvest journey; swapped recipes/final-stack menu; case rotate/eject; local produce sale; shovel/path/fence keyboard walk/run/jump alongside oak control; Creative decorative family placement. |
| M11-MULTIPLAYER-RELOAD | Two actual clients for simultaneous planting/harvest/ejection and display synchronization; inventory conservation after reconnect, chunk unload/load and actual server stop/start; normal and low-TPS fertilizer timing. Server dispatch/NBT round trips are separate evidence. |
| M6-NORMAL-TERRAIN | Survey known-seed newly generated affected/control biomes, and preserve pre-existing native fruit/stems through a real restart. The flat GameTest registry audit is not a natural distribution survey. |
| M5-FULL-CAN-ART | User supplies full-can artwork; wire the final resource override and observe inventory/mainhand/offhand/dropped/pickup/reconnect/resource reload at 0, 1, 11 and 12 charges. Functional predicate and component preservation already pass. |
| M7-LIVE-ROLLOUT | After separate release authority, deploy the paired code, execute only the documented insert-only rollout, then verify the Jhelom/Britannia catalog and ordinary player sale. Read-only pre-release evidence is already recorded. |

Client control was paused after repeated concurrent-input interruptions. At final read-only inspection, its former PID 9232 and all javaw processes were absent; the reason for exit was not observed. The isolated client files and test world remain available for the next acceptance run. No further permission for ordinary local tests is inferred from this note; a coordinated control window is the missing practical prerequisite. The local baseline client still contains the M10 baseline, whose code/resources match the final candidate except build metadata. Use the final candidate when completing acceptance.

## Clean local candidate

`britannia_mod-0.1.8a-all.jar`: **34,690,262 bytes**, SHA-256 **`e25d2596058f0ae78ba67bc87e4d2d76fc7e46fddbd4700f17c9b512d3373709`**. Embedded HEAD `4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67`, branch `codex/patch18-gameplay-bugfixes`, dirty **false**, timestamp `2026-09-07T15:03:26.277379Z`. Runtime Minecraft 1.21.1 / NeoForge 21.1.72 / Java 21; bundled GeckoLib 4.6.6 and nanohttpd 2.2.0.

Stable archive: `tmp/gameplay-bugfixes/release/britannia_mod-0.1.8a-all-4ee0f5d9.jar`; normal build output remains in `build/libs`. [Full release identity](M11_RELEASE_IDENTITY.md) includes the plain JAR hash and ZIP inspection. No diagnostic source-set injections, GameTest classes, generated empty test structures or acceptance fixture namespace occur in the bundled candidate. Every ZIP entry matches the observed clean M10 baseline except `britannia_mod_build.properties`. This comparison does not establish Photon visibility.

## Existing worlds and rollback

Old private soil without timer/fertility tags remains untimed/untracked (`-1`). Old timed community soil with no saved origin restores hoed soil without inventing preparation time; missing prepared metadata receives one remaining tick, never a free 3,600. Planted crops/flowers and paid house plots do not expire. Optional owner/origin fields do not require a world migration.

M2 recipes and M3 display cases retain ordinary item/component and case storage formats. M4 reuses finite-use and legacy `-1` semantics. M5’s readiness version is packet-only; the can’s legacy missing charge remains full. Already crafted, stored or planted items require no conversion.

Worldgen changes affect new generation after registry loading, never existing blocks. Reverting the modifier restores future random patches and does not create/delete existing fruit. Fence reconciliation is bounded to loaded chunks and neighbors with four tasks per tick, with no forced chunk loads. Five recurring Creative families persist a false-default `creative_origin` blockstate (fern, hedge, blood, stalactite and triple `iron_fence_gate`); old states retain Survival substrate semantics. Removing the new code may restore substrate breakage on previously bypassed decorations; preserve world backups before any separately authorized release.

Keep M7 mod/Rails mapping, rollout and receipt API changes paired. Reverting code does not delete inserted commodity rows and must not reset prices. **Do not downgrade while sale journals contain pending receipts**: preserve the exact request, inventory/removal/delivery markers and journal, and reconcile uncertain outcomes against the authoritative receipt first. Full inventory intentionally waits; recovery never invents a world-drop payout. Dedicated player-file durability tests are not a hardware power-cut experiment or proof of the integrated host’s separate `level.dat` player snapshot.

## Changed files, status and project records

[Complete changed-file inventory](M11_CHANGED_FILES.md) enumerates every added/modified file in both isolated repositories. The final snapshot records exact ending documentation SHAs and final `git status --short`. Original NeoForge HEAD remains `421e27853dde4099d1d794568e33e6709507a53b` with no tracked diff; existing untracked documents remain. Original Rails HEAD remains `a9425ca41afa9b4ba7539426044e7b50e70a3966`; its existing Avatar article change (2 insertions / 2 deletions) and untracked files are preserved.

Authoritative records: [playbook](ULTIMACRAFT_GAMEPLAY_BUGFIX_PLAYBOOK.md), [kickoff](ULTIMACRAFT_GAMEPLAY_BUGFIX_KICKOFF.md), [scratchpad](ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md), [updated acceptance draft](ULTIMACRAFT_BUGFIX_ACCEPTANCE_DRAFT.md), [this handoff](ULTIMACRAFT_GAMEPLAY_BUGFIX_HANDOFF.md), [fence evidence](M8_FENCE_EVIDENCE.md), [Creative placement evidence](M9_CREATIVE_DECORATION_EVIDENCE.md), [shader acceptance](M10_SHADER_ACCEPTANCE.md).

Nothing was pushed, merged, deployed, seeded or migrated in production. No production data/world settings or server JAR were changed. Fabric, Atrevion and unrelated worktrees were untouched. Disposable local PostgreSQL databases and an isolated offline client were used for authorized testing; prior test databases and evidence were preserved.
