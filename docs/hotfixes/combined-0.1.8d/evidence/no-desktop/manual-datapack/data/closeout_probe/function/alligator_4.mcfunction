data modify storage closeout_probe:evidence phase set value 4
tp @e[tag=closeout_target] 307 81 7
tp @e[tag=closeout_alligator] 302 87 7
data merge entity @e[tag=closeout_alligator,limit=1] {NoAI:0b,NoGravity:0b,Motion:[0d,0d,0d]}
execute as @a run tp @s 304 84 11 180 0
say PLAYBACK_4_REAL_AI_DIVE_NOT_TELEPORTED_TRAVEL
schedule function closeout_probe:alligator_5 200t replace
