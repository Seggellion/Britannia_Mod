difficulty normal
gamerule doMobSpawning false
gamemode spectator @s
effect give @s minecraft:night_vision 100 0 true
kill @e[tag=closeout_alligator]
kill @e[tag=closeout_target]
summon britannia_mod:town_person 290 80 10 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Health:1000f,Attributes:[{Name:"minecraft:generic.max_health",Base:1000d}],Tags:["closeout_target"]}
attribute @e[tag=closeout_target,limit=1] minecraft:generic.max_health base set 1000
attribute @e[tag=closeout_target,limit=1] minecraft:generic.knockback_resistance base set 1
effect give @e[tag=closeout_target,limit=1] minecraft:instant_health 1 10 true
effect give @e[tag=closeout_target,limit=1] minecraft:water_breathing 120 0 true
summon britannia_mod:alligator 283 80 10 {PersistenceRequired:1b,Tags:["closeout_alligator"]}
data modify storage closeout_probe:evidence active set value 1b
data modify storage closeout_probe:evidence samples set value []
execute store result storage closeout_probe:evidence start long 1 run time query gametime
function closeout_probe:alligator_0
function closeout_probe:sample
