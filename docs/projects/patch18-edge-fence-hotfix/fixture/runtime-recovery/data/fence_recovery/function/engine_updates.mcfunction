forceload add 320 200 340 210
fill 320 79 200 340 79 210 minecraft:smooth_stone
fill 320 80 200 340 82 210 minecraft:air
setblock 320 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0]
setblock 324 80 204 britannia_mod:wooden_fence[facing=east,layout_code=0]
setblock 328 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0]
setblock 332 80 204 britannia_mod:wooden_fence[facing=west,layout_code=0]
execute if block 320 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_ISOLATED_NORTH_OWNER_320_80_204_FULL_STATE_facing=north,layout_code=0,north=false,east=false,south=false,west=false
execute if block 324 80 204 britannia_mod:wooden_fence[facing=east,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_ISOLATED_EAST_OWNER_324_80_204_FULL_STATE_facing=east,layout_code=0,north=false,east=false,south=false,west=false
execute if block 328 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_ISOLATED_SOUTH_OWNER_328_80_204_FULL_STATE_facing=south,layout_code=0,north=false,east=false,south=false,west=false
execute if block 332 80 204 britannia_mod:wooden_fence[facing=west,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_ISOLATED_WEST_OWNER_332_80_204_FULL_STATE_facing=west,layout_code=0,north=false,east=false,south=false,west=false
setblock 321 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0]
setblock 324 80 205 britannia_mod:wooden_fence[facing=east,layout_code=0]
setblock 329 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0]
setblock 332 80 205 britannia_mod:wooden_fence[facing=west,layout_code=0]
execute if block 320 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0,north=false,east=true,south=false,west=false] run say ENGINE_A_AFTER_B_IMMEDIATE_NORTH_OWNER_320_80_204_FULL_STATE_facing=north,layout_code=0,north=false,east=true,south=false,west=false
execute if block 324 80 204 britannia_mod:wooden_fence[facing=east,layout_code=0,north=false,east=false,south=true,west=false] run say ENGINE_A_AFTER_B_IMMEDIATE_EAST_OWNER_324_80_204_FULL_STATE_facing=east,layout_code=0,north=false,east=false,south=true,west=false
execute if block 328 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0,north=false,east=true,south=false,west=false] run say ENGINE_A_AFTER_B_IMMEDIATE_SOUTH_OWNER_328_80_204_FULL_STATE_facing=south,layout_code=0,north=false,east=true,south=false,west=false
execute if block 332 80 204 britannia_mod:wooden_fence[facing=west,layout_code=0,north=false,east=false,south=true,west=false] run say ENGINE_A_AFTER_B_IMMEDIATE_WEST_OWNER_332_80_204_FULL_STATE_facing=west,layout_code=0,north=false,east=false,south=true,west=false
schedule function fence_recovery:engine_settled 5t
say COMMAND_SETUP_UPDATES_ONLY_NOT_BLOCKITEM_OR_MOUSE_ACCEPTANCE
