time set noon
gamemode survival @s
clear @s
effect give @s minecraft:speed 30 3 true
tp @s 49.4 80 84.2 90 0
data modify storage plaster_probe:acceptance player_case set value "phantom_east"
data modify storage plaster_probe:acceptance player_positions set value []
data modify storage plaster_probe:acceptance player_ground set value []
tag @s add plaster_walk
schedule function plaster_probe:walk_done_phantom_east 15s replace
say KEYBOARD_PROBE_phantom_east_START
