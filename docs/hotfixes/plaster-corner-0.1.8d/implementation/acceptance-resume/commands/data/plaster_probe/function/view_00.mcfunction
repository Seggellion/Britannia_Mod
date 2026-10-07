gamemode spectator @s
tp @s 3.200 80 3.200 135.000 10
tellraw @s {"text": "PLASTER CASE 00: britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower] at 0,80,0", "color": "yellow"}
execute if block 0 80 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_00_EXACT_LOWER_PASS
execute unless block 0 80 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_00_STATE_NORMALIZED
execute if block 0 81 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_00_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 0 81 0 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_00_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
