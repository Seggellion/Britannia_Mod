time set noon
setblock 83 81 12 britannia_mod:wooden_gate[facing=south,half=upper]
setblock 83 80 12 britannia_mod:wooden_gate[facing=south,half=lower]
execute if block 83 80 12 britannia_mod:wooden_gate[half=lower] run say VALID_TAGGED_GATE_83_80_12_PASS
execute if block 83 81 12 britannia_mod:wooden_gate[half=upper] run say VALID_TAGGED_GATE_83_81_12_PASS
setblock 84 81 11 britannia_mod:wooden_gate[facing=east,half=upper]
setblock 84 80 11 britannia_mod:wooden_gate[facing=east,half=lower]
execute if block 84 80 11 britannia_mod:wooden_gate[half=lower] run say VALID_TAGGED_GATE_84_80_11_PASS
execute if block 84 81 11 britannia_mod:wooden_gate[half=upper] run say VALID_TAGGED_GATE_84_81_11_PASS
setblock 84 81 12 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper]
setblock 84 80 12 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower]
say VALID_TAGGED_GATE_NEIGHBOR_RESULT
execute if block 84 80 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=straight,branch_right=false,mirrored=false,half=lower] run say VALID_GATE_NEIGHBOR_LOWER_NORTH_STRAIGHT
execute if block 84 81 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=straight,branch_right=false,mirrored=false,half=upper] run say VALID_GATE_NEIGHBOR_UPPER_NORTH_STRAIGHT
function plaster_probe:acceptance_view_05
