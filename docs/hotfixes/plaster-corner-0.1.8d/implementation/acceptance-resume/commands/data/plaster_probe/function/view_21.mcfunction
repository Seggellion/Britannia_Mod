gamemode spectator @s
tp @s 9.800 80 63.200 -135.000 10
tellraw @s {"text": "PLASTER CASE 21: britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=true] at 12,80,60", "color": "yellow"}
execute if block 12 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=true] run say PLASTER_CASE_21_EXACT_LOWER_PASS
execute unless block 12 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=true] run say PLASTER_CASE_21_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
