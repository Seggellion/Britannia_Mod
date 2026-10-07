gamemode spectator @s
gamerule spawnChunkRadius 0
setworldspawn 1000 80 1000
tp @s 1000 80 1000
schedule function plaster_probe:check_unloaded 10s replace
