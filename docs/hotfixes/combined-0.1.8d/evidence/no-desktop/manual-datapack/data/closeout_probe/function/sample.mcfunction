execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples append value {}
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].Pos set from entity @e[tag=closeout_alligator,limit=1] Pos
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].Motion set from entity @e[tag=closeout_alligator,limit=1] Motion
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].Air set from entity @e[tag=closeout_alligator,limit=1] Air
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].OnGround set from entity @e[tag=closeout_alligator,limit=1] OnGround
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].NoAI set from entity @e[tag=closeout_alligator,limit=1] NoAI
execute if data storage closeout_probe:evidence {active:1b} run data modify storage closeout_probe:evidence samples[-1].phase set from storage closeout_probe:evidence phase
execute if data storage closeout_probe:evidence {active:1b} run schedule function closeout_probe:sample 10t replace
