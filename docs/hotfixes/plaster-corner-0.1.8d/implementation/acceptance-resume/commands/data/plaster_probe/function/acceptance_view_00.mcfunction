time set noon
gamemode spectator @s
tp @s 3.2 82 3.2 135.0 35
say ACCEPTANCE_SCENE_00_owner_cap
execute if block 0 80 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower] run say OWNER_EXACT_LOWER_PASS
execute if block 0 81 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper] run say OWNER_EXACT_UPPER_PASS
