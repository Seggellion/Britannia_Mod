# Current clean candidate after three failed manual checks

Normal release command (JDK 21, isolated worktree):

```powershell
.\gradlew.bat clean build artifactIdentity runGameTestServer --no-configuration-cache --console=plain
```

The command succeeded with the complete JUnit and GameTest totals below. The resolved clean target was the isolated worktree's `build` directory. No test filter, release bypass, diagnostic init script or resource injection was used. Log: `tmp/gameplay-bugfixes/qa-followup/clean-release-full.log`.

| Field | Current candidate |
| --- | --- |
| Archive filename | `britannia_mod-0.1.8a-all-6414f54c.jar` |
| Archive path | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\patch18-gameplay-bugfixes\tmp\gameplay-bugfixes\release\britannia_mod-0.1.8a-all-6414f54c.jar` |
| Normal build output | `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\patch18-gameplay-bugfixes\build\libs\britannia_mod-0.1.8a-all.jar` |
| Size | 34,690,293 bytes |
| SHA-256 | `ec28f6afc371b7639b0007a9db264ff64e7ff83925c9c70dc6dc3bd841622641` |
| Embedded source HEAD | `6414f54cb6c31094f61314276c2bb2590f0574a9` |
| Embedded branch | `codex/patch18-gameplay-bugfixes` |
| Embedded dirty state | `false` |
| Build timestamp UTC | `2026-09-07T18:10:36.382371100Z` |
| JUnit | 3,498 total; 3,481 passed; 17 inherited skips; 0 failures/errors; 455 suites |
| Registered GameTests | All 1,156 required tests passed |

The final closure documentation is committed after this build; it does not change the embedded source identity. Exactly three entries differ from the old release: `DisplayCaseBlockEntity.class`, `MoongateBlockEntityRenderer.class`, and `britannia_mod_build.properties`. No entries were added/removed; all 7,372 packaged assets/data resources are byte-identical to the old candidate. GameTest classes and the generated empty test structure are excluded, as are diagnostic/QA datapack namespaces. The class bytes contain the optional empty-item serialization and the new entity-material reference.

Watering-can resources remain only the base model and texture; no full-can resource or override is present. **PENDING_USER_ASSET** is not a visual pass. Display and moongate visual gates are **PENDING_MANUAL_RETEST**.

Machine-readable identity and entry/asset inspection: `tmp/gameplay-bugfixes/qa-followup/release-identity.json`. The previous candidate and previous handoff are retained. The old handoff is `tmp/gameplay-bugfixes/release/FINAL_HANDOFF-M11-12ebe40c.md`.

The resource namespaces are `britannia_mod`, `c` and `minecraft`. Six pre-existing production shrine catalogue resource filenames contain `diagnostic`; they are referenced by `ShrineMonolithDefinitions` and are unchanged from the prior candidate. The audit distinguishes those filenames from forbidden diagnostic namespaces or injected test resources.

## Historical candidate identity (preserved)

# Clean local candidate identity

The normal `gradlew.bat clean build artifactIdentity --no-configuration-cache --console=plain` path passed in6m38s, including the full JUnit suite. Log: `tmp/gameplay-bugfixes/m11-clean-release.log`. The clean task's resolved target was verified as this isolated worktree's `build` directory. No diagnostic source-set injection, init script or disabled release verification was used. Gradle's ordinary content-keyed build cache supplied unchanged compilation inputs.

| Field | Exact value |
|---|---|
| Mod | britannia_mod0.1.8a |
| Source commit | 4ee0f5d90d70b2429c7d5cb8eacf100b838d0d67 |
| Branch | codex/patch18-gameplay-bugfixes |
| Embedded dirty flag | false |
| Build timestamp | 2026-09-07T15:03:26.277379Z |
| Bundled filename | britannia_mod-0.1.8a-all.jar |
| Bundled size | 34,690,262 bytes |
| Bundled SHA256 | e25d2596058f0ae78ba67bc87e4d2d76fc7e46fddbd4700f17c9b512d3373709 |
| Plain filename | britannia_mod-0.1.8a.jar |
| Plain size | 34,118,624 bytes |
| Plain SHA256 | ef272a9bea04304abb6cab21a6cd6415538d80f035520a83fa8db495db6b5219 |
| Runtime | Minecraft1.21.1,NeoForge21.1.72,Java21 |
| Bundled dependencies | GeckoLib4.6.6,nanohttpd2.2.0 |
| Produce manifest SHA256 | 9bd07850fce0bf25906daba76ac95e25bf389cd1fb7f0fff49c36dd44934e5bc |

The stable local copy is `tmp/gameplay-bugfixes/release/britannia_mod-0.1.8a-all-4ee0f5d9.jar`; the normal output also remains in `build/libs`. The archive is byte-identical to the normal bundled output. Use one bundled candidate, not both the plain and bundled JAR together. No server or original client JAR was replaced.

ZIP entry inspection found no `gametest` classes, generated empty test structure or external `gameplay_acceptance` fixture. JarJar metadata identifies the two bundled dependency JARs above. Every released entry matches the clean M10 baseline byte-for-byte except `britannia_mod_build.properties`; the M11 source changes are tests/documentation, and the shader renderer is unchanged. This comparison establishes code/resource identity, not visible Photon acceptance.

The clean build executed3,492JUnit tests in454suites:3,475passed,17existing skips,0failures/errors. The registered server gate separately passed all1,154required GameTests in2.244minutes (`m11-gametest-final.log`). The optional real Rails GameTest was not enabled in that default count; its earlier actual after-commit-response-loss/reconnect proof is recorded in M7_SALE_RECOVERY.md. The compiled-in GameTest configuration selector is excluded from the JAR.

The17inherited skips cover11banner/content-owner evidence cases and6farming/catalog/artifact-owner cases. Exact method names and machine-readable artifact/JUnit evidence are in `tmp/gameplay-bugfixes/m11-release-identity.json`. None was converted to a passing result or newly skipped for this project.

The packaged baseline used Eclipse Adoptium21.0.9 for the observed offline client. The Gradle GameTest launch log reports Microsoft JDK21.0.8; both satisfy the configured Java21 toolchain. The fresh client GPU/driver observation and the separate pending shader gate are documented in M10_SHADER_ACCEPTANCE.md. This is a reviewable local candidate, with runtime/user-art acceptance still pending as listed in the handoff; it is not a deployment authorization.
