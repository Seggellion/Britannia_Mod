gamemode spectator @s
tp @s -2.200 80 9.800 -45.000 10
tellraw @s {"text": "PLASTER CASE 04: britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=true,half=lower] at 0,80,12", "color": "yellow"}
execute if block 0 80 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_04_EXACT_LOWER_PASS
execute unless block 0 80 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_04_STATE_NORMALIZED
execute if block 0 81 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_04_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 0 81 12 britannia_mod:plaster_wall_and_support_blank[facing=north,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_04_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
