gamemode spectator @s
tp @s 21.800 80 27.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 10: britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=lower] at 24,80,24", "color": "yellow"}
execute if block 24 80 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_10_EXACT_LOWER_PASS
execute unless block 24 80 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=lower] run say PLASTER_CASE_10_STATE_NORMALIZED
execute if block 24 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_10_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 24 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_10_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 24 81 24 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=true,mirrored=false,half=upper] run say PLASTER_CASE_10_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
