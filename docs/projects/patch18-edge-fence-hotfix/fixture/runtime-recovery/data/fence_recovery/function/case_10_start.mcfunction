forceload add 250 204 254 208
fill 250 79 204 254 79 208 minecraft:smooth_stone
fill 250 80 204 254 83 208 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 252.5 80 203.9 0 32
say CASE_10 A=252 80 206; fresh place A on the floor, intended facing=north. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
