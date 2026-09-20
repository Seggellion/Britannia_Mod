time set noon
gamemode survival @s
clear @s
effect give @s minecraft:speed 30 3 true
tp @s 47.6 80 84.2 270 0
data modify storage plaster_probe:acceptance player_case set value "visible_west_jump"
data modify storage plaster_probe:acceptance player_positions set value []
data modify storage plaster_probe:acceptance player_ground set value []
tag @s add plaster_walk
schedule function plaster_probe:walk_done_visible_west_jump 15s replace
say KEYBOARD_PROBE_visible_west_jump_START
