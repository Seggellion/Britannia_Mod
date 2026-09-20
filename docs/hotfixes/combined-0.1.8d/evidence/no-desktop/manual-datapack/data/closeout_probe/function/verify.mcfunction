execute if block 230 80 27 britannia_mod:wooden_fence[facing=east,layout_code=9] run say MANUAL_FENCE_ROTATED_STATE_MATCH
execute if block 220 80 60 britannia_mod:plaster_wall_and_support_blank[half=lower,facing=east,shape=corner,branch_right=false,mirrored=true] if block 220 81 60 britannia_mod:plaster_wall_and_support_blank[half=upper,facing=east,shape=corner,branch_right=false,mirrored=true] run say MANUAL_WALL_MIRRORED_PAIR_MATCH
say STATE_MARKERS_ONLY_DO_NOT_CERTIFY_HUMAN_VISUAL_PASS
