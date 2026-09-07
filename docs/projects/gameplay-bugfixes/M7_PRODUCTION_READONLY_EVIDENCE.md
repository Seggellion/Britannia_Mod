# M7 production read-only observation

Observed 2026-09-07T08:28:11.16914+00:00 on the repository’s authenticated Heroku app ultimacraft. Executed BEGIN TRANSACTION READ ONLY, a15second local statement timeout, SELECTs, then ROLLBACK. PostgreSQL returned transaction_read_only=on. No production sale, seed, migration, data/policy change or deployment.

The first SELECT was rejected for the incorrect guessed assignment FK spawn_point_id. Verified NpcSpawnAssignment’s actual service_npc_spawn_point_id from local source and reran the SELECT. Both commands were read-only.

The produce_trader type is active and spawnable, kind trader, definition revision2, accepting exactly category produce. Britannia has no override row for this type, so its effective accepted categories inherit that global list.

Jhelom’s enabled registered post a21929dc-b942-45fe-ab09-2a45a538846f has active assignment a6854e71-9184-4fd7-9573-4243a895ffb4 to active WorldNpc18ff7ae5-1e95-4937-b98f-f2d9ef6e9303, named Zorah. The reported trader is therefore correctly assigned and has the intended category policy.

The city currently has15produce rows. All36approved new keys are missing; broccoli and orange have no rows. Apple and carrots remain enabled controls. This is direct live evidence of the catalog gap; the local fix has not been deployed or seeded there.

| Existing commodity | Base copper | Current copper | Weight / quantity | Buy enabled | Cap |
|---|---|---|---|---|---|
| produce/fruit/apple | 2.0 | 3.0 | 0 / 0 | True | 3000 |
| produce/fruit/banana | 2.5 | 3.75 | 0 / 0 | True | 2000 |
| produce/fruit/berries | 2.4 | 3.6 | 0 / 0 | True | 2000 |
| produce/fruit/concord_grapes | 3.0 | 4.5 | 0 / 0 | True | 2000 |
| produce/fruit/peaches | 2.8 | 4.2 | 0 / 0 | True | 2000 |
| produce/fruit/pears | 2.6 | 3.9 | 0 / 0 | True | 2000 |
| produce/vegetable/cabbage | 2.0 | 3.0 | 0 / 0 | True | 3000 |
| produce/vegetable/carrots | 1.8 | 2.7 | 0 / 0 | True | 3000 |
| produce/vegetable/corn | 2.0 | 3.0 | 0 / 0 | True | 3000 |
| produce/vegetable/lettuce | 1.8 | 2.7 | 0 / 0 | True | 3000 |
| produce/vegetable/onion | 1.5 | 2.25 | 0 / 0 | True | 3000 |
| produce/vegetable/potato | 1.6 | 2.4 | 0 / 0 | True | 5000 |
| produce/vegetable/pumpkin | 3.0 | 4.5 | 0 / 0 | True | 3000 |
| produce/vegetable/squash | 2.2 | 3.3 | 0 / 0 | True | 3000 |
| produce/vegetable/tomato | 2.0 | 3.0 | 0 / 0 | True | 3000 |

Missing keys: produce|fruit|blackberry, produce|fruit|blueberry, produce|fruit|cantaloupe, produce|fruit|cherries, produce|fruit|cranberry, produce|fruit|elderberry, produce|fruit|grapes, produce|fruit|honeydew, produce|fruit|huckleberry, produce|fruit|lemon, produce|fruit|lime, produce|fruit|melon_slice, produce|fruit|mulberry, produce|fruit|olive, produce|fruit|orange, produce|fruit|pineapple, produce|fruit|plum, produce|fruit|raspberry, produce|fruit|strawberry, produce|fruit|watermelon, produce|vegetable|beans, produce|vegetable|bell_peppers, produce|vegetable|broccoli, produce|vegetable|cauliflower, produce|vegetable|celery, produce|vegetable|cucumbers, produce|vegetable|green_onion, produce|vegetable|parsnip, produce|vegetable|peas, produce|vegetable|radish, produce|vegetable|rhubarb, produce|vegetable|rutabaga, produce|vegetable|snow_peas, produce|vegetable|turnips, produce|vegetable|yam, produce|vegetable|yellow_onion.

Deployment acceptance remains pending: separately review the paired local commits, deploy the approved mod/Rails code and run the insert-only rollout only with separate deployment authority. This observation is not permission to perform those actions.
