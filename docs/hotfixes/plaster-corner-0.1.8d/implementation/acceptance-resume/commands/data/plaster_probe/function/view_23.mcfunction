gamemode spectator @s
tp @s 33.800 80 57.800 -45.000 10
tellraw @s {"text": "PLASTER CASE 23: britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=true] at 36,80,60", "color": "yellow"}
execute if block 36 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=true] run say PLASTER_CASE_23_EXACT_LOWER_PASS
execute unless block 36 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=true] run say PLASTER_CASE_23_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
