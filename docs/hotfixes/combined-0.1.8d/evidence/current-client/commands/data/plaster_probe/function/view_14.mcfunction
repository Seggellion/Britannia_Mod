gamemode spectator @s
tp @s 21.800 80 33.800 -45.000 10
tellraw @s {"text": "PLASTER CASE 14: britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=lower] at 24,80,36", "color": "yellow"}
execute if block 24 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_14_EXACT_LOWER_PASS
execute unless block 24 80 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_14_STATE_NORMALIZED
execute if block 24 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_14_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 24 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_14_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 24 81 36 britannia_mod:plaster_wall_and_support_blank[facing=west,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_14_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
