data modify storage closeout_probe:evidence active set value 0b
execute store result storage closeout_probe:evidence end long 1 run time query gametime
data merge entity @e[tag=closeout_alligator,limit=1] {NoAI:1b,Motion:[0d,0d,0d]}
execute as @a run gamemode creative @s
say PLAYBACK_1600_TICKS_DONE: human observations remain required; no scripted visual pass.
