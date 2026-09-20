gamemode spectator @s
tp @s -2.200 80 45.800 -45.000 10
tellraw @s {"text": "PLASTER CASE 16: britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=false] at 0,80,48", "color": "yellow"}
execute if block 0 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=false] run say PLASTER_CASE_16_EXACT_LOWER_PASS
execute unless block 0 80 48 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=false] run say PLASTER_CASE_16_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
