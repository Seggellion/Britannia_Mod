execute as @a[tag=plaster_walk] run data modify storage plaster_probe:acceptance player_positions append from entity @s Pos
execute as @a[tag=plaster_walk] run data modify storage plaster_probe:acceptance player_ground append from entity @s OnGround
