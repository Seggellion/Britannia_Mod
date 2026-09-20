gamemode spectator @s
tp @s -2.200 80 39.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 12: britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=false,mirrored=false,half=lower] at 0,80,36", "color": "yellow"}
execute if block 0 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_12_EXACT_LOWER_PASS
execute unless block 0 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_12_STATE_NORMALIZED
execute if block 0 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_12_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 0 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_12_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
