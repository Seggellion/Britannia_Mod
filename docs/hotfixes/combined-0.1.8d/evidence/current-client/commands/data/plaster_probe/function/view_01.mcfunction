gamemode spectator @s
tp @s 9.800 80 -2.200 -45.000 10
tellraw @s {"text": "PLASTER CASE 01: britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=lower] at 12,80,0", "color": "yellow"}
execute if block 12 80 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_01_EXACT_LOWER_PASS
execute unless block 12 80 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_01_STATE_NORMALIZED
execute if block 12 81 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_01_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_01_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 0 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_01_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
