forceload add 256 198 260 202
fill 256 79 198 260 79 202 minecraft:smooth_stone
fill 256 80 198 260 83 202 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 259.3893 80 198.0568 20.0000 32.0000
say CASE_03 A=258 80 200; fresh place A on the floor, intended facing=north. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
