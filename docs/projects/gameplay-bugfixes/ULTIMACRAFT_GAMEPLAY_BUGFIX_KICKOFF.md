Execute the attached ULTIMACRAFT_GAMEPLAY_BUGFIX_PLAYBOOK.md from the Britannia NeoForge repository at C:\projects\britannia\mod\Britannia_Mod.
This is an implementation project, not another discovery pass. Complete M0 through M11 autonomously, including local code changes, tests, client/manual checks available in the environment, documentation, and local commits. Do not stop between milestones to reconfirm decisions already written in the playbook. Continue independent milestones if one external check is unavailable.
Read the following documents completely before modifying code:
- ULTIMACRAFT_GAMEPLAY_BUGFIX_PLAYBOOK.md
- docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_DISCOVERY.md
- docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_SUPPLEMENTAL_DISCOVERY.md
- docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_PLANNING_INPUTS.md
- docs/projects/gameplay-bugfixes/ULTIMACRAFT_BUGFIX_ACCEPTANCE_DRAFT.md
If the playbook and kickoff prompt were supplied outside the repository, copy them into docs/projects/gameplay-bugfixes/ in the isolated project worktree. Preserve the original files and existing untracked farming documents.
1. Read every applicable AGENTS.md.
2. Verify the current NeoForge checkout, branch, full head, worktree status, registered worktrees, loader/build metadata, and divergence from discovery head 421e27853dde4099d1d794568e33e6709507a53b.
3. Create or safely reuse an isolated worktree with a dedicated local branch such as claude/patch18-gameplay-bugfixes, based on the current intended patch-18 head. Never discard or overwrite user work.
4. Do not touch C:\projects\atrevion\Britannia_Mod or any Fabric worktree.
5. When M7 begins, inspect /home/dusti/ultimacraft-website, then create an isolated Rails worktree and local branch based on the intended current release/public line. Preserve its existing modified/untracked files by leaving the original worktree untouched.
6. Keep NeoForge and Rails commits separate and record both SHAs for M7.
7. Do not push, merge, deploy, run production seeds/migrations, change production data/world settings, or replace a server JAR.
- Empty fertilized soil expires after 1,200 online simulation ticks and returns to its exact previous hoed state. Planting cancels the timer. Offline server time does not count. The fertilizer application and all unused fertile uses are lost on expiry.
- Fertilizer targets are prepared community plots and empty vanilla farmland inside the acting player's owned house only.
- Buckets, watering cans, bowls, and rain hydrate. A bowl adds one hydration and returns one custom empty bowl. Preserve initial fertilizer moisture.
- Planting and harvesting share the species threshold. Grapes and cultivated flowers are included. Native crops outside the custom system remain vanilla.
- Eligible harvest success is min(95%, 75% + 1% per Farming point above the requirement). Failure destroys produce and byproducts, uses normal durability/fertility cost, runs the existing skill-practice opportunity, and follows annual/perennial/tree lifecycle. Creative/admin uses deterministic free success for testing.
- Remove only the three random landscape pumpkin/melon features. Preserve structure-authored blocks, existing blocks, and intentional native/custom cultivation.
- Pseudo-crafting roles work in either hand and commit once. Compatible output stacks receive crafted items before an empty hand/slot fallback.
- The produce trader buys every approved custom-grown fruit and vegetable. Continue excluding grains, seeds, flowers, reagents, textiles, tobacco, and processed food. The reported vendor is in Jhelom on the Britannia shard.
- Offhand decorator use ejects the exact display-case contents into the world. Mainhand use rotates both cells atomically. Single-cell rotation or case dismantling is never valid.
- A custom fence preserves an existing vanilla-shovel-created dirt path. Fix the history-dependent L join and use vanilla-height collision.
- Creative players bypass decorative substrate restrictions while technical multiblock/space validity remains.
- BUG-04, BUG-06, and BUG-07 are excluded manual asset polishing. Do not edit those crop assets. The user supplies full watering-can artwork; implement and verify state selection without generating art.
- Maintain docs/projects/gameplay-bugfixes/ULTIMACRAFT_GAMEPLAY_BUGFIX_SCRATCHPAD.md after every milestone so the project survives context compaction.
- Maintain docs/projects/gameplay-bugfixes/ULTIMACRAFT_GAMEPLAY_BUGFIX_HANDOFF.md and update the acceptance draft only with evidence-backed results.
- Reproduce or preserve the discovery evidence before each fix. Use the smallest authoritative transaction/state boundary and revalidate live server state at commit.
- Write focused tests for meaningful behavior and conservation. Run broader suites when shared code changes create a concrete risk, and run the complete required gates in M11.
- Distinguish source proof, server test proof, client/manual proof, and live Rails evidence. Do not claim shader visibility, HUD behavior, physical jumping, hand input, display synchronization, natural world distribution, or production catalog state from headless tests.
- Generated diagnostic source sets and build outputs are not release artifacts. Produce the final JAR from normal clean build inputs and verify embedded git identity/dirty state.
- Use one or more focused local commits per milestone. Inspect each diff before committing and keep unrelated work out.
For M7, align the NeoForge commodity mapping and Rails catalog for the entire approved produce set listed in the playbook. Use existing commodity pricing conventions and conservative same-tier comparables, documenting every mapping and price. Do not invent a new economy formula or reset existing prices. Use Jhelom/Britannia read-only evidence when available; local completion may be handed off with named live acceptance pending.
For M10, first reproduce the current moongate with no shader, Iris/Sodium with shaders disabled, and Photon v1.1. Apply the smallest renderer correction supported by that comparison. Do not redesign its artwork.
Proceed through all milestones. Stop only when a blocker requires information or authority absent from the playbook and repositories. Before stopping, finish every independent milestone and document the exact blocker, evidence, and smallest decision or external action needed.
At completion, return the playbook's full handoff: verdict; all mod/Rails milestone commits; nineteen-row issue status with the three exclusions; exact tests and manual evidence; clean JAR identity; changed files and final git status; remaining user-asset or external acceptance; and confirmation that nothing was pushed, merged, deployed, seeded in production, or changed in Fabric.