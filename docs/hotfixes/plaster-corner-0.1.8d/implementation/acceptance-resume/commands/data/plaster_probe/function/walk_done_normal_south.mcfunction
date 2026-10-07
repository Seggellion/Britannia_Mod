time set noon
tag @a remove plaster_walk
data modify storage plaster_probe:acceptance walks.normal_south.positions set from storage plaster_probe:acceptance player_positions
data modify storage plaster_probe:acceptance walks.normal_south.ground set from storage plaster_probe:acceptance player_ground
say KEYBOARD_PROBE_normal_south_DONE
