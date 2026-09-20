gamemode spectator @s
tp @s 27.200 80 15.200 135.000 10
tellraw @s {"text": "PLASTER CASE 06: britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=true,half=lower] at 24,80,12", "color": "yellow"}
execute if block 24 80 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=true,half=lower] run say PLASTER_CASE_06_EXACT_LOWER_PASS
execute unless block 24 80 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=true,half=lower] run say PLASTER_CASE_06_STATE_NORMALIZED
execute if block 24 81 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=true,half=upper] run say PLASTER_CASE_06_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 24 81 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=true,half=upper] run say PLASTER_CASE_06_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
