data modify storage closeout_probe:evidence phase set value 3
tp @e[tag=closeout_alligator] 302 83 7
execute as @a run tp @s 302 83 11 180 0
say PLAYBACK_3_STOPPED_SUBMERGED_BIND_POSE
schedule function closeout_probe:alligator_4 200t replace
