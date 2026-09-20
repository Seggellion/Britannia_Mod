data modify storage closeout_probe:evidence phase set value 7
tp @e[tag=closeout_target] 319 88 9
tp @e[tag=closeout_alligator] 314 88 9
execute as @a run tp @s 316 90 14 180 20
say PLAYBACK_7_LAND_WALK_RESET_AND_AIM
schedule function closeout_probe:alligator_done 200t replace
