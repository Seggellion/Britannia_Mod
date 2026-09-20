data modify storage closeout_probe:evidence phase set value 1
tp @e[tag=closeout_target] 290 80 15
tp @e[tag=closeout_alligator] 283 80 15
function closeout_probe:shallow
execute as @a run tp @s 286.5 82 19 180 20
say PLAYBACK_1_SHALLOW_WADING_WALK
schedule function closeout_probe:alligator_2 200t replace
