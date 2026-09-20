forceload add 250 228 254 232
fill 250 79 228 254 79 232 minecraft:smooth_stone
fill 250 80 228 254 83 232 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 249.9 80 230.5 270 32
say CASE_42 A=252 80 230; fresh place A on the floor, intended facing=west. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
