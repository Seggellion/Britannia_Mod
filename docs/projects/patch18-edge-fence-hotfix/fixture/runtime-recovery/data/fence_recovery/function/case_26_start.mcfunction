forceload add 250 216 254 220
fill 250 79 216 254 79 220 minecraft:smooth_stone
fill 250 80 216 254 83 220 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 252.5 80 221.1 180 32
say CASE_26 A=252 80 218; fresh place A on the floor, intended facing=south. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
