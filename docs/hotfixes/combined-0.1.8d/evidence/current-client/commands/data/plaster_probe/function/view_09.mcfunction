gamemode spectator @s
tp @s 9.800 80 27.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 09: britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=lower] at 12,80,24", "color": "yellow"}
execute if block 12 80 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=lower] run say PLASTER_CASE_09_EXACT_LOWER_PASS
execute unless block 12 80 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=lower] run say PLASTER_CASE_09_STATE_NORMALIZED
execute if block 12 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=upper] run say PLASTER_CASE_09_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=upper] run say PLASTER_CASE_09_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=true,half=upper] run say PLASTER_CASE_09_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
