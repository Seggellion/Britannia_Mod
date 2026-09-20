time set noon
fill 69 80 33 75 81 39 minecraft:air
setblock 72 81 35 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
setblock 72 80 35 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
setblock 71 81 36 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
setblock 71 80 36 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
gamemode creative @s
clear @s
item replace entity @s hotbar.0 with britannia_mod:plaster_wall_and_support_blank 8
tp @s 72.5 80 38.2 180 43.6
say ACTUAL_PLACE_reverse_arms_CLICK_FLOOR
