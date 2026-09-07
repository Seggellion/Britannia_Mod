# Display-case ghost item follow-up

The original failed manual check remains recorded in `QA_FOLLOWUP_2026-09-07.md`. The server ejected the exact stack, but an empty top-level update tag was ignored by NeoForge 21.1.72's default `onDataPacket`. The renderer has no independent cache: its client block entity simply retained the old `displayedItem`. Only the lower/root cell owns that field; interactions with either cell resolve to it.

`DisplayCaseBlockEntity` now puts an explicit empty `DisplayedItem` compound in its update tag and uses `ItemStack.parseOptional` for missing/empty contents. Disk serialization remains compatible with missing legacy fields. Ejection still acknowledges insertion before clearing and rolls back on refusal. The existing update notification targets the root with flags 3; no extra upper-cell inventory or renderer reset is needed.

With JDK 21:

```powershell
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCaseBlockEntityTest' --no-configuration-cache --console=plain
```

Before the fix: **8 tests, 1 expected failure** at the occupied client replica after the empty live packet (`display-red.log`). This reproduces the missing client clearing behavior, rather than only asserting tag structure.

```powershell
.\gradlew.bat test --tests 'com.seggellion.britannia_mod.structure.DisplayCase*' runGameTestServer --no-configuration-cache --console=plain
```

After the fix: **12 JUnit tests passed; all 1,154 required registered GameTests passed**, build successful in 2m58s (`display-green.log`). An initial broader run found an old source-string assertion expecting `ItemStack.parse`; it was updated to `parseOptional` and the entire command rerun successfully.

The three display-case GameTests include 320 neighborhood/cell/sneak/mainhand combinations, duplicate gestures, exact legacy count/name/damage/custom-component conservation, rejected world spawns, reentry, two-player duplicate suppression, failed atomic rotation and malformed/denied cases. The matrix now encodes/decodes the registered block-entity packet and calls the real NeoForge handler on two occupied replicas after interaction. It also checks the upper cell has no second block entity, the next inserted item replaces the old one, and failed ejection still synchronizes the original item. JUnit covers empty disk/chunk/reconnect tags and repeated empty packets.

These are headless state/packet tests. Actual immediate blank rendering on two clients and actual chunk/reconnect/restart visual checks still require controlled client retesting.
