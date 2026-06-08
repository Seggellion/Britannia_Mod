# Quest Destroy Trigger Manual Test Checklist

Use this for the lava/event-volume quest item flow after changing quest metadata or identity linking.

1. Start Rails and the NeoForge dedicated/server-integrated environment with the same shard secret configured.
2. With a known working verified user, accept the quest that grants `magic_ring`.
3. Confirm the granted item has server logs like `Stamped quest destroy item` with `quest_id`, `ring_destroyed`, `quest_owner_uuid`, and the configured bounds.
4. Throw the item into lava inside the configured destroy volume.
5. Confirm NeoForge logs `Quest destroy candidate`, `Quest destroy advancement attempted`, and `Quest destroy Rails request attempted`.
6. Confirm Rails logs `[QuestTrigger] incoming`, resolved user/state/shard, applied effects, and `[QuestAchievement] award result`.
7. Confirm the player receives the vanilla advancement `britannia_mod:quest/ring_destroyed` and the website achievement exists.
8. Confirm the Rails victory/narrative node opens in the quest screen and shows the node text.
9. Confirm returned `stat_gain` client actions display Karma/Fame gain messages.
10. Confirm returned `achievement` client actions show the client toast/sound.
11. Confirm any returned `granted_items` are claimed through the existing quest reward packet.
12. Throw the same kind of freshly stamped quest item into lava outside the configured destroy volume.
13. Confirm no advancement is granted and no Rails trigger request is sent.
14. Confirm the item disappears immediately, the player sees `The quest item slips away from the flames...`, and NeoForge logs `Quest item safe return scheduled`.
15. Wait 10 seconds and confirm the player sees `The quest item returns to your pack.` and NeoForge logs `Quest item returned to inventory`.
16. Repeat with a full inventory; after 10 seconds the item should drop at the player's feet and NeoForge should log `Quest item dropped because inventory was full`.
17. Repeat with two players nearby; the stamped owner should receive the returned item.
18. Repeat with a player logging out during the 10-second delay if practical; if the server stays running, the item should return on that player's next server-side tick after login.
19. Drop a normal non-quest item and confirm it despawns normally.
20. Drop a quest item and force or shorten its despawn timer in a test environment.
21. Confirm NeoForge logs `Quest item despawn intercepted` and `Quest item safe return scheduled` with `reason=natural_despawn`.
22. Confirm no advancement is granted and no Rails trigger request is sent for natural despawn.
23. Wait 10 seconds and confirm the item returns to the owner inventory, or drops at the player's feet if the inventory is full.
24. Repeat natural despawn with the owner offline if practical; if the server stays running, the item should return on that player's next server-side tick after login.
25. Throw a normal non-quest item into lava; normal lava behavior should remain unchanged.
26. Throw a quest item with missing or malformed custom data; it should not be silently lost, and logs should explain the unresolved or malformed owner/context.
27. Repeat with an affected verified user.
28. For legacy items without stamped quest context, confirm NeoForge logs `legacy client fallback`; replace the item by re-accepting/re-granting the quest so the server-stamped flow is used.
29. Test an ambiguous case with two players near an unstamped legacy item; NeoForge should log `ambiguous_nearby_players` and avoid sending Rails a request.
