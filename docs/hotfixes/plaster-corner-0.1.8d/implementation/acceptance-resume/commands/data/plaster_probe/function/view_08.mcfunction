gamemode spectator @s
tp @s 3.200 80 21.800 45.000 10
tellraw @s {"text": "PLASTER CASE 08: britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=true,mirrored=true,half=lower] at 0,80,24", "color": "yellow"}
execute if block 0 80 24 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_08_EXACT_LOWER_PASS
execute unless block 0 80 24 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=true,mirrored=true,half=lower] run say PLASTER_CASE_08_STATE_NORMALIZED
execute if block 0 81 24 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_08_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
execute if block 0 81 24 britannia_mod:plaster_wall_and_support_blank[facing=east,shape=corner,branch_right=true,mirrored=true,half=upper] run say PLASTER_CASE_08_EXACT_UPPER_PASS
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
