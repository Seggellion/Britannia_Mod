forceload add 274 204 278 208
fill 274 79 204 278 79 208 minecraft:smooth_stone
fill 274 80 204 278 83 208 minecraft:air
gamemode creative @s
item replace entity @s hotbar.0 with britannia_mod:wooden_fence 64
item replace entity @s hotbar.1 with britannia_mod:interior_decorator_tool
item replace entity @s weapon.offhand with minecraft:air
tp @s 279.1 80 206.5 90 32
say CASE_14 A=276 80 206; fresh place A on the floor, intended facing=east. Calibrate target from F3 before placement.
say Setup/reset only; this function never places a fence.
