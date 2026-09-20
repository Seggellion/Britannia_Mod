gamemode spectator @s
tp @s 15.200 80 75.200 135.000 10
tellraw @s {"text": "PLASTER CASE 25: britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=lower] at 12,80,72", "color": "yellow"}
execute if block 12 80 72 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_25_EXACT_LOWER_PASS
execute unless block 12 80 72 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_25_STATE_NORMALIZED
execute if block 12 81 72 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_25_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 72 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_25_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 72 britannia_mod:plaster_wall_and_support_blank[facing=south,shape=t_junction,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_25_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
