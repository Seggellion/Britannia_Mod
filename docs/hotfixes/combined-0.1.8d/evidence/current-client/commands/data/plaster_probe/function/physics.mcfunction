time set noon
gamemode spectator @s
tp @s 48.5 83 86 180 35
kill @e[tag=plaster_physics]
summon minecraft:item 49.5 80.2 84.35 {"Tags": ["plaster_physics", "p_lower_e"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:stone", "count": 1}, "Motion": [-0.12, 0.0, 0.0]}
summon minecraft:item 49.5 81.2 84.35 {"Tags": ["plaster_physics", "p_upper_e"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:dirt", "count": 1}, "Motion": [-0.12, 0.0, 0.0]}
summon minecraft:item 47.5 80.2 84.35 {"Tags": ["plaster_physics", "p_lower_w"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:glass", "count": 1}, "Motion": [0.12, 0.0, 0.0]}
summon minecraft:item 47.5 81.2 84.35 {"Tags": ["plaster_physics", "p_upper_w"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:sand", "count": 1}, "Motion": [0.12, 0.0, 0.0]}
summon minecraft:item 51.5 81.2 86.1 {"Tags": ["plaster_physics", "p_control_e"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:gravel", "count": 1}, "Motion": [-0.12, 0.0, 0.0]}
summon minecraft:item 49.5 81.2 86.1 {"Tags": ["plaster_physics", "p_control_w"], "NoGravity": true, "PickupDelay": 32767, "Age": -32768, "Item": {"id": "minecraft:cobblestone", "count": 1}, "Motion": [0.12, 0.0, 0.0]}
summon minecraft:item 48.5 80.2 85.5 {"Tags": ["plaster_physics", "p_main_s_lower"], "NoGravity": true, "PickupDelay": 32767, "Item": {"id": "minecraft:oak_planks", "count": 1}, "Motion": [0.0, 0.0, -0.12]}
summon minecraft:item 48.5 81.2 85.5 {"Tags": ["plaster_physics", "p_main_s_upper"], "NoGravity": true, "PickupDelay": 32767, "Item": {"id": "minecraft:birch_planks", "count": 1}, "Motion": [0.0, 0.0, -0.12]}
schedule function plaster_probe:physics_report 20t replace
