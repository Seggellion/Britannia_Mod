data modify storage closeout_probe:evidence phase set value 5
tp @e[tag=closeout_target] 307 87 7
execute as @a run tp @s 306 85 11 180 0
say PLAYBACK_5_REAL_AI_ASCENT
schedule function closeout_probe:alligator_6 200t replace
