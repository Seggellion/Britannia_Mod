time set noon
tag @a remove plaster_walk
data modify storage plaster_probe:acceptance walks.phantom_east.positions set from storage plaster_probe:acceptance player_positions
data modify storage plaster_probe:acceptance walks.phantom_east.ground set from storage plaster_probe:acceptance player_ground
say KEYBOARD_PROBE_phantom_east_DONE
