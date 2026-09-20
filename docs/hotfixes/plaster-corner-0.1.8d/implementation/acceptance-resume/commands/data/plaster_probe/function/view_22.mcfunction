gamemode spectator @s
tp @s 21.800 80 63.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 22: britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=false] at 24,80,60", "color": "yellow"}
execute if block 24 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=false] run say PLASTER_CASE_22_EXACT_LOWER_PASS
execute unless block 24 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=false] run say PLASTER_CASE_22_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
