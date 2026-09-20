execute if block 320 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0,north=false,east=true,south=false,west=false] run say ENGINE_A_AFTER_B_SETTLED_NORTH_OWNER_320_80_204_FULL_STATE_facing=north,layout_code=0,north=false,east=true,south=false,west=false
execute if block 324 80 204 britannia_mod:wooden_fence[facing=east,layout_code=0,north=false,east=false,south=true,west=false] run say ENGINE_A_AFTER_B_SETTLED_EAST_OWNER_324_80_204_FULL_STATE_facing=east,layout_code=0,north=false,east=false,south=true,west=false
execute if block 328 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0,north=false,east=true,south=false,west=false] run say ENGINE_A_AFTER_B_SETTLED_SOUTH_OWNER_328_80_204_FULL_STATE_facing=south,layout_code=0,north=false,east=true,south=false,west=false
execute if block 332 80 204 britannia_mod:wooden_fence[facing=west,layout_code=0,north=false,east=false,south=true,west=false] run say ENGINE_A_AFTER_B_SETTLED_WEST_OWNER_332_80_204_FULL_STATE_facing=west,layout_code=0,north=false,east=false,south=true,west=false
setblock 321 80 204 minecraft:air
setblock 324 80 205 minecraft:air
setblock 329 80 204 minecraft:air
setblock 332 80 205 minecraft:air
execute if block 320 80 204 britannia_mod:wooden_fence[facing=north,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_AFTER_REMOVAL_NORTH_OWNER_320_80_204_FULL_STATE_facing=north,layout_code=0,north=false,east=false,south=false,west=false
execute if block 324 80 204 britannia_mod:wooden_fence[facing=east,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_AFTER_REMOVAL_EAST_OWNER_324_80_204_FULL_STATE_facing=east,layout_code=0,north=false,east=false,south=false,west=false
execute if block 328 80 204 britannia_mod:wooden_fence[facing=south,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_AFTER_REMOVAL_SOUTH_OWNER_328_80_204_FULL_STATE_facing=south,layout_code=0,north=false,east=false,south=false,west=false
execute if block 332 80 204 britannia_mod:wooden_fence[facing=west,layout_code=0,north=false,east=false,south=false,west=false] run say ENGINE_A_AFTER_REMOVAL_WEST_OWNER_332_80_204_FULL_STATE_facing=west,layout_code=0,north=false,east=false,south=false,west=false
schedule function fence_recovery:engine_removed_settled 5t
