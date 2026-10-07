gamemode spectator @s
tp @s 3.200 80 63.200 135.000 10
tellraw @s {"text": "PLASTER CASE 20: britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=false] at 0,80,60", "color": "yellow"}
execute if block 0 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=false] run say PLASTER_CASE_20_EXACT_LOWER_PASS
execute unless block 0 80 60 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=false] run say PLASTER_CASE_20_STATE_NORMALIZED
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
tellraw @s {"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}
time set noon
