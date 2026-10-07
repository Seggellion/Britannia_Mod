time set noon
tag @a remove plaster_walk
data modify storage plaster_probe:acceptance walks.visible_west_jump.positions set from storage plaster_probe:acceptance player_positions
data modify storage plaster_probe:acceptance walks.visible_west_jump.ground set from storage plaster_probe:acceptance player_ground
say KEYBOARD_PROBE_visible_west_jump_DONE
