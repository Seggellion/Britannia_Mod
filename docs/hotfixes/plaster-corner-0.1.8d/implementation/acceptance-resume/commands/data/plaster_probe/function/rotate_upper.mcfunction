time set noon
gamemode creative @s
clear @s
tp @s 48.5 80 86.2 180 18
item replace entity @s weapon.mainhand with britannia_mod:interior_decorator_tool
say BEFORE_rotate_upper
function plaster_probe:pair
schedule function plaster_probe:after_rotate_upper 10s replace
say CLICK_rotate_upper_THEN_RUN_PAIR
