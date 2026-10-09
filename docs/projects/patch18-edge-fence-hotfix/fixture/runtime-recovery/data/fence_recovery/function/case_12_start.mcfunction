forceload add 262 204 266 208
fill 262 79 204 266 79 208 minecraft:smooth_stone
fill 262 80 204 266 83 208 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 267.1 80 206.5 90 32
say CASE_12 A=264 80 206; fresh place A on the floor, intended facing=east. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
