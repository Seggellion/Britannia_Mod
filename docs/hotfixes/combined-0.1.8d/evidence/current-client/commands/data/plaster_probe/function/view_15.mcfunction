gamemode spectator @s
tp @s 33.800 80 39.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 15: britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=lower] at 36,80,36", "color": "yellow"}
execute if block 36 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_15_EXACT_LOWER_PASS
execute unless block 36 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_15_STATE_NORMALIZED
execute if block 36 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_15_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 36 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_15_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 36 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_15_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
