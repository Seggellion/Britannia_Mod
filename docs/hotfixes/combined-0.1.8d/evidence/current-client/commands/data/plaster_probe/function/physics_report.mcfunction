say PLASTER_PHYSICS_POSITIONS_BEGIN
say PROBE_lower_e
data modify storage plaster_probe:acceptance physics.lower_e set from entity @e[tag=p_lower_e,limit=1] Pos
say PROBE_upper_e
data modify storage plaster_probe:acceptance physics.upper_e set from entity @e[tag=p_upper_e,limit=1] Pos
say PROBE_lower_w
data modify storage plaster_probe:acceptance physics.lower_w set from entity @e[tag=p_lower_w,limit=1] Pos
say PROBE_upper_w
data modify storage plaster_probe:acceptance physics.upper_w set from entity @e[tag=p_upper_w,limit=1] Pos
say PROBE_control_e
data modify storage plaster_probe:acceptance physics.control_e set from entity @e[tag=p_control_e,limit=1] Pos
say PROBE_control_w
data modify storage plaster_probe:acceptance physics.control_w set from entity @e[tag=p_control_w,limit=1] Pos
data modify storage plaster_probe:acceptance physics.main_s_lower set from entity @e[tag=p_main_s_lower,limit=1] Pos
data modify storage plaster_probe:acceptance physics.main_s_upper set from entity @e[tag=p_main_s_upper,limit=1] Pos
say PLASTER_PHYSICS_POSITIONS_END
