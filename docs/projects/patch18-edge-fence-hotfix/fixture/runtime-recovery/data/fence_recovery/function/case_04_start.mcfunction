forceload add 262 198 266 202
fill 262 79 198 266 79 202 minecraft:smooth_stone
fill 262 80 198 266 83 202 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 264.5 80 197.9 0 32
say CASE_04 A=264 80 200; fresh place A on the floor, intended facing=north. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
