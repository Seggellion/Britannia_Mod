time set noon
gamemode survival @s
clear @s
effect give @s minecraft:speed 30 3 true
tp @s 60.2 80 13.4 180 0
data modify storage plaster_probe:acceptance player_case set value "normal_south"
data modify storage plaster_probe:acceptance player_positions set value []
data modify storage plaster_probe:acceptance player_ground set value []
tag @s add plaster_walk
schedule function plaster_probe:walk_done_normal_south 15s replace
say KEYBOARD_PROBE_normal_south_START
