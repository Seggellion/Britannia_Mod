gamemode spectator @s
tp @s 15.200 80 9.800 45.000 10
tellraw @s {"text": "PLASTER CASE 05: britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=false,half=lower] at 12,80,12", "color": "yellow"}
execute if block 12 80 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_05_EXACT_LOWER_PASS
execute unless block 12 80 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=false,half=lower] run say PLASTER_CASE_05_STATE_NORMALIZED
execute if block 12 81 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_05_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 12 81 12 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=false,mirrored=false,half=upper] run say PLASTER_CASE_05_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
