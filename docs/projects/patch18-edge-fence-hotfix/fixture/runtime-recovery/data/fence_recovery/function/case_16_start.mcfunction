forceload add 238 210 242 214
fill 238 79 210 242 79 214 minecraft:smooth_stone
fill 238 80 210 242 83 214 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 243.1 80 212.5 90 32
say CASE_16 A=240 80 212; fresh place A on the floor, intended facing=east. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
