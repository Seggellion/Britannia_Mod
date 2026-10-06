# Alligator 0.1.8d hotfix

Baseline: patch-18, 4435912197fba84630d6e5a0544f29fc3a736450; upstream origin/patch-18 c1f2b5b819834ab971a2fc65b86fcd484d31e7c9. Tracked/index clean initially. Four unrelated untracked owner kickoff/playbook documents are preserved. Nine existing worktrees are outside this task.

M0: versions remain Minecraft 1.21.1, NeoForge 21.1.72, GeckoLib 4.6.6, Java 21, Gradle 8.9. The existing recovery candidate was rehashed and copied before building to ignored tmp/alligator-0.1.8d/previous-candidate/. Size 36,107,339; SHA-256 C04F3092FA577E4C98C4C4EE16FEBD5008403CCC98A0EBC064D860E3AC8981C6.

Tests use a dedicated 16×12×16 template and disposable tmp/alligator-0.1.8d directories. Exact NeoForge sources show namespace filtering, but no CLI test-name filter. Focused tests use the britannia_alligator template namespace; the default full gate includes both namespaces. The process-local runner removes credential/live-integration environment variables and refuses a credential-bearing run directory. Production API callers resolve credentials through ServerAuthRegistry; absent credentials prevent requests. Existing service regression fixtures install their own local mocks.

M1: pre-fix downward navigation failure reproduced in baseline.log: tick 200, feet Y -51.445 versus requested -57, reachable=false, vertical input=0. Expanded baseline-matrix.log also reproduces diagonal/depth/ascent failures. Baseline twenty-entity run: 1,200 ticks, 1,100 samples, median wall tick 11.552 ms, final rolling average server tick 12.817 ms. The land fixture's physical endpoint measurement remains under diagnosis; no land baseline pass claimed yet.

M2–M7: pending. Existing medallion motion/cape/swimming visual hold and Alligator client visual acceptance remain pending. No push, deployment or installed binary replacement is authorized.
