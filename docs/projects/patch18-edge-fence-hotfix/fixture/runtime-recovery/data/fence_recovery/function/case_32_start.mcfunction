forceload add 238 222 242 226
fill 238 79 222 242 79 226 minecraft:smooth_stone
fill 238 80 222 242 83 226 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 240.5 80 227.1 180 32
say CASE_32 A=240 80 224; fresh place A on the floor, intended facing=south. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
