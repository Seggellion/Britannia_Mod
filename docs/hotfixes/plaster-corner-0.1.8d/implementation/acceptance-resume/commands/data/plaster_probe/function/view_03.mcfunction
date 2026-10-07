gamemode spectator @s
tp @s 39.200 80 -2.200 45.000 10
tellraw @s {"text": "PLASTER CASE 03: britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=false,half=lower] at 36,80,0", "color": "yellow"}
execute if block 36 80 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_03_EXACT_LOWER_PASS
execute unless block 36 80 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_03_STATE_NORMALIZED
execute if block 36 81 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_03_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 36 81 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_03_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
