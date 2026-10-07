gamemode spectator @s
tp @s 39.200 80 51.200 135.000 10
tellraw @s {"text": "PLASTER CASE 19: britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=true] at 36,80,48", "color": "yellow"}
execute if block 36 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=true] run say PLASTER_CASE_19_EXACT_LOWER_PASS
execute unless block 36 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=true] run say PLASTER_CASE_19_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
