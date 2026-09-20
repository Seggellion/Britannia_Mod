forceload add 274 216 278 220
fill 274 79 216 278 79 220 minecraft:smooth_stone
fill 274 80 216 278 83 220 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 276.5 80 221.1 180 32
say CASE_30 A=276 80 218; fresh place A on the floor, intended facing=south. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
