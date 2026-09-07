# Watering-can follow-up — PENDING_USER_ASSET

The failed visual check is expected with the available assets: there is one base model/texture and no full-state override. There is no competing override to reorder. The current repository, prior release JAR and checked user asset locations contain no distinct `watering_can_full` artwork. No artwork was created or edited.

Base resources under `src/main/resources/assets/britannia_mod/`:

| Path | SHA-256 |
| --- | --- |
| `models/item/watering_can.json` | `6851ee34d51109eabeebcdee4b2612c44ef13bbe012be054eefd65ca3f0e0b78` |
| `textures/item/watering_can.png` | `7daa80cc4d6d6f94d94b4ac3bd25deb2dd8fea48c5c55a44081a9928afcd9014` |

The intended user resource paths are `models/item/watering_can_full.json` and `textures/item/watering_can_full.png` under the same namespace. Once the real, distinct art exists, the base model needs this override:

```json
{"predicate":{"britannia_mod:full":1.0},"model":"britannia_mod:item/watering_can_full"}
```

The full model should reference the user's texture as `britannia_mod:item/watering_can_full`. A missing reference is deliberately not shipped. If additional overrides are introduced, the full override must win for the intended full-state combinations; no such ordering conflict exists today.

## Functional synchronization

`ClientModSetup.onClientSetup` already registers `britannia_mod:full` through `event.enqueueWork`. Its callback delegates to `WateringCanItem.fullModelState` and ignores entity/world/display context. Exactly 12 charges returns 1, 0–11 returns 0, and missing legacy data reads as 12. It is registered once per client startup; Minecraft rebuilds baked model overrides on resource reload without removing this property registration.

`setWaterCharges` replaces the stack's CUSTOM_DATA component while preserving unrelated custom data. Minecraft's `ServerPlayer` tick calls `containerMenu.broadcastChanges`, which compares component-bearing snapshots and sends `ClientboundContainerSetSlotPacket`; inventory initialization uses `ClientboundContainerSetContentPacket`. No extra mod packet or forced full inventory resend is missing. Dropping transfers the component-bearing stack to `ItemEntity.DATA_ITEM`, which uses the item-stack entity-data serializer; pickup returns it to the inventory synchronization path.

New `GameplayWateringCanSyncGameTests` capture actual outbound server slot/content packets, encode/decode them and update a receiving inventory replica. Coverage includes 0, 1, 11, 12 and legacy missing charge data; a change after an already synchronized full stack; vanilla offhand-swap handling; inventory clicks; dropping; the dropped entity-data packet; actual pickup; saved inventory loaded into a fresh player and sent through the reconnect content packet. Another GameTest refills from water and dispenses all 12 charges onto soil in each hand, checks every received state, and verifies empty use adds no water. Existing `PlantingPresentationTest` covers clamping, exact full threshold and custom-data preservation.

These tests cover the data that a renderer consumes. They do not demonstrate a distinct picture, an actual reconnect screen or a resource reload screen. The absence of distinct user art remains **PENDING_USER_ASSET** after successful functional checks. Final command totals and final-JAR inspection are recorded in `QA_FOLLOWUP_2026-09-07.md` and the updated release identity.
