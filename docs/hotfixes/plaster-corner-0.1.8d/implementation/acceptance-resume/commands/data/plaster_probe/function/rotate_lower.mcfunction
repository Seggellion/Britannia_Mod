time set noon
gamemode creative @s
clear @s
tp @s 48.5 80 86.2 180 43.6
item replace entity @s weapon.mainhand with britannia_mod:interior_decorator_tool
say BEFORE_rotate_lower
function plaster_probe:pair
schedule function plaster_probe:after_rotate_lower 10s replace
say CLICK_rotate_lower_THEN_RUN_PAIR
