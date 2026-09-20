forceload add 0 96 63 111
fill 0 79 96 63 79 111 minecraft:smooth_stone
setblock 7 81 100 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
setblock 7 80 100 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
setblock 8 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=straight,branch_right=false]
setblock 8 79 100 minecraft:dirt
setblock 8 79 100 minecraft:smooth_stone
execute if block 8 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_00_PASS
execute unless block 8 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_00_FAIL
setblock 21 81 100 britannia_mod:plaster_wall_blank[facing=west,shape=straight,branch_right=false,half=upper]
setblock 21 80 100 britannia_mod:plaster_wall_blank[facing=west,shape=straight,branch_right=false,half=lower]
setblock 20 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=straight,branch_right=false]
setblock 20 79 100 minecraft:dirt
setblock 20 79 100 minecraft:smooth_stone
execute if block 20 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_01_PASS
execute unless block 20 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=north,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_01_FAIL
setblock 32 81 99 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
setblock 32 80 99 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
setblock 32 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=straight,branch_right=false]
setblock 32 79 100 minecraft:dirt
setblock 32 79 100 minecraft:smooth_stone
execute if block 32 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_02_PASS
execute unless block 32 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_02_FAIL
setblock 44 81 101 britannia_mod:plaster_wall_blank[facing=north,shape=straight,branch_right=false,half=upper]
setblock 44 80 101 britannia_mod:plaster_wall_blank[facing=north,shape=straight,branch_right=false,half=lower]
setblock 44 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=straight,branch_right=false]
setblock 44 79 100 minecraft:dirt
setblock 44 79 100 minecraft:smooth_stone
execute if block 44 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_03_PASS
execute unless block 44 80 100 britannia_mod:plaster_wall_and_support_blank_half[facing=east,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_03_FAIL
setblock 9 81 108 britannia_mod:plaster_wall_blank[facing=west,shape=straight,branch_right=false,half=upper]
setblock 9 80 108 britannia_mod:plaster_wall_blank[facing=west,shape=straight,branch_right=false,half=lower]
setblock 8 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=straight,branch_right=false]
setblock 8 79 108 minecraft:dirt
setblock 8 79 108 minecraft:smooth_stone
execute if block 8 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_04_PASS
execute unless block 8 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_04_FAIL
setblock 19 81 108 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=upper]
setblock 19 80 108 britannia_mod:plaster_wall_blank[facing=east,shape=straight,branch_right=false,half=lower]
setblock 20 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=straight,branch_right=false]
setblock 20 79 108 minecraft:dirt
setblock 20 79 108 minecraft:smooth_stone
execute if block 20 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_05_PASS
execute unless block 20 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=south,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_05_FAIL
setblock 32 81 109 britannia_mod:plaster_wall_blank[facing=north,shape=straight,branch_right=false,half=upper]
setblock 32 80 109 britannia_mod:plaster_wall_blank[facing=north,shape=straight,branch_right=false,half=lower]
setblock 32 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=straight,branch_right=false]
setblock 32 79 108 minecraft:dirt
setblock 32 79 108 minecraft:smooth_stone
execute if block 32 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_06_PASS
execute unless block 32 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=false] run say REOPENED_LONE_HALF_06_FAIL
setblock 44 81 107 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=upper]
setblock 44 80 107 britannia_mod:plaster_wall_blank[facing=south,shape=straight,branch_right=false,half=lower]
setblock 44 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=straight,branch_right=false]
setblock 44 79 108 minecraft:dirt
setblock 44 79 108 minecraft:smooth_stone
execute if block 44 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_07_PASS
execute unless block 44 80 108 britannia_mod:plaster_wall_and_support_blank_half[facing=west,shape=corner,branch_right=true] run say REOPENED_LONE_HALF_07_FAIL
