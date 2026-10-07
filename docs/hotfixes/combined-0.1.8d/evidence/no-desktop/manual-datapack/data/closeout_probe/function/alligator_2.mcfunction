data modify storage closeout_probe:evidence phase set value 2
fill 281 80 13 293 80 17 minecraft:air
data merge entity @e[tag=closeout_alligator,limit=1] {NoAI:1b,NoGravity:1b,Motion:[0d,0d,0d]}
tp @e[tag=closeout_alligator] 302 87.3 7
execute as @a run tp @s 302 89 12 180 15
say PLAYBACK_2_STOPPED_SURFACE_BIND_POSE
schedule function closeout_probe:alligator_3 200t replace
