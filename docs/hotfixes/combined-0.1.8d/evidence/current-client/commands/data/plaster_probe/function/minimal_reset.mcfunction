time set noon
gamemode creative @s
clear @s
fill 48 80 84 48 81 84 minecraft:air
setblock 47 81 84 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
setblock 47 80 84 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
setblock 46 81 84 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
setblock 46 80 84 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
setblock 48 81 83 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
setblock 48 80 83 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
setblock 48 81 82 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
setblock 48 80 82 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
item replace entity @s hotbar.0 with britannia_mod:plaster_wall_and_support_blank 8
tp @s 48.5 80 86.2 180 43.6
say MINIMAL_REPRO_ACTUAL_BLOCKITEM_CLICK_REQUIRED
