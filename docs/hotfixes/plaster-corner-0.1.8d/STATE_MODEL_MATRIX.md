# Corner state-to-model matrix

Source-derived bounds in model pixels; every row is JSON audited, none is client rendered. Post Y is 0..32 relative to the lower block. Upper models contribute no mesh.

| facing | branch_right | mirrored | half | selected model (under britannia_mod:block/structure/plaster/) | Y rotation | timber post X,Z bounds |
|---|---|---|---|---|---|---|
| north | false | false | lower | plaster_wall_and_support_blank_corner | 0 | x -0.8..6.7; z 13.1..16.1 |
| north | false | false | upper | minecraft:block/air | 0 | none |
| north | false | true | lower | plaster_wall_and_support_blank_corner_mirrored | 0 | x 9.3..16.8; z 13.1..16.1 |
| north | false | true | upper | minecraft:block/air | 0 | none |
| north | true | false | lower | plaster_wall_and_support_blank_corner_branch_right | 0 | x -0.1..2.9; z -0.8..6.7<br>x 9.3..16.8; z -0.1..2.9 |
| north | true | false | upper | minecraft:block/air | 0 | none |
| north | true | true | lower | plaster_wall_and_support_blank_corner_branch_right_mirrored | 0 | x 13.1..16.1; z -0.8..6.7<br>x -0.8..6.7; z -0.1..2.9 |
| north | true | true | upper | minecraft:block/air | 0 | none |
| east | false | false | lower | plaster_wall_and_support_blank_corner | 90 | x -0.1..2.9; z -0.8..6.7 |
| east | false | false | upper | minecraft:block/air | 0 | none |
| east | false | true | lower | plaster_wall_and_support_blank_corner_mirrored | 90 | x -0.1..2.9; z 9.3..16.8 |
| east | false | true | upper | minecraft:block/air | 0 | none |
| east | true | false | lower | plaster_wall_and_support_blank_corner_branch_right | 90 | x 9.3..16.8; z -0.1..2.9<br>x 13.1..16.1; z 9.3..16.8 |
| east | true | false | upper | minecraft:block/air | 0 | none |
| east | true | true | lower | plaster_wall_and_support_blank_corner_branch_right_mirrored | 90 | x 9.3..16.8; z 13.1..16.1<br>x 13.1..16.1; z -0.8..6.7 |
| east | true | true | upper | minecraft:block/air | 0 | none |
| south | false | false | lower | plaster_wall_and_support_blank_corner | 180 | x 9.3..16.8; z -0.1..2.9 |
| south | false | false | upper | minecraft:block/air | 0 | none |
| south | false | true | lower | plaster_wall_and_support_blank_corner_mirrored | 180 | x -0.8..6.7; z -0.1..2.9 |
| south | false | true | upper | minecraft:block/air | 0 | none |
| south | true | false | lower | plaster_wall_and_support_blank_corner_branch_right | 180 | x 13.1..16.1; z 9.3..16.8<br>x -0.8..6.7; z 13.1..16.1 |
| south | true | false | upper | minecraft:block/air | 0 | none |
| south | true | true | lower | plaster_wall_and_support_blank_corner_branch_right_mirrored | 180 | x -0.1..2.9; z 9.3..16.8<br>x 9.3..16.8; z 13.1..16.1 |
| south | true | true | upper | minecraft:block/air | 0 | none |
| west | false | false | lower | plaster_wall_and_support_blank_corner | 270 | x 13.1..16.1; z 9.3..16.8 |
| west | false | false | upper | minecraft:block/air | 0 | none |
| west | false | true | lower | plaster_wall_and_support_blank_corner_mirrored | 270 | x 13.1..16.1; z -0.8..6.7 |
| west | false | true | upper | minecraft:block/air | 0 | none |
| west | true | false | lower | plaster_wall_and_support_blank_corner_branch_right | 270 | x -0.8..6.7; z 13.1..16.1<br>x -0.1..2.9; z -0.8..6.7 |
| west | true | false | upper | minecraft:block/air | 0 | none |
| west | true | true | lower | plaster_wall_and_support_blank_corner_branch_right_mirrored | 270 | x -0.8..6.7; z -0.1..2.9<br>x -0.1..2.9; z 9.3..16.8 |
| west | true | true | upper | minecraft:block/air | 0 | none |
