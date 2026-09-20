gamemode spectator @s
tp @s 15.200 80 45.800 45.000 10
tellraw @s {"text": "PLASTER CASE 17: britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=true] at 12,80,48", "color": "yellow"}
execute if block 12 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=true] run say PLASTER_CASE_17_EXACT_LOWER_PASS
execute unless block 12 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=true] run say PLASTER_CASE_17_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
