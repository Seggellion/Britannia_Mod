forceload add 250 198 254 202
fill 250 79 198 254 79 202 minecraft:smooth_stone
fill 250 80 198 254 83 202 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 252.5 80 197.9 0 32
say CASE_02 A=252 80 200; fresh place A on the floor, intended facing=north. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
