execute if loaded 0 80 0 run say PLASTER_CHUNK_STILL_LOADED_FAIL
execute unless loaded 0 80 0 run say PLASTER_CHUNK_UNLOADED_PASS
execute as @a at @s run function plaster_probe:view_00
