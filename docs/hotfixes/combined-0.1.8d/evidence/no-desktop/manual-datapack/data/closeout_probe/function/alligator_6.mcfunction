data modify storage closeout_probe:evidence phase set value 6
tp @e[tag=closeout_target] 316 88 7
tp @e[tag=closeout_alligator] 310 86 7
execute as @a run tp @s 316 90 12 140 20
say PLAYBACK_6_REAL_AI_BANK_EXIT
schedule function closeout_probe:alligator_7 200t replace
