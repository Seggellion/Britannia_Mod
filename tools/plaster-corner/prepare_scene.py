"""Install reproducible commands into an existing disposable task world only.

Run: python tools/plaster-corner/prepare_scene.py --world tmp/plaster-corner-0.1.8d/client-baseline/saves/"Plaster Corner Baseline"
In that world: /reload, /function plaster_probe:setup, /function plaster_probe:view_00
This is a command-state visual fixture, not proof of normal BlockItem placement.
"""
import argparse
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FULL = 'britannia_mod:plaster_wall_and_support_blank'
BLANK = 'britannia_mod:plaster_wall_blank'
HALF = FULL + '_half'
FACES = ['north', 'east', 'south', 'west']


def state(block, facing, branch=False, mirrored=False, half=None, shape='corner'):
    props = [f'facing={facing}', f'shape={shape}', 'branch_right=' + str(branch).lower()]
    if block == FULL:
        props.append('mirrored=' + str(mirrored).lower())
    if half:
        props.append('half=' + half)
    return block + '[' + ','.join(props) + ']'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    args = parser.parse_args()
    world = args.world.resolve()
    allowed = (ROOT / 'tmp/plaster-corner-0.1.8d').resolve()
    if not world.is_relative_to(allowed) or not (world / 'level.dat').exists():
        parser.error('world must already exist under this checkout/tmp/plaster-corner-0.1.8d')
    pack = world / 'datapacks/plaster_probe'
    functions = pack / 'data/plaster_probe/function'
    functions.mkdir(parents=True, exist_ok=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48,
        'description': 'Disposable plaster corner verification commands'}}, indent=2), encoding='utf-8')
    cases = [('south', False, False, FULL, 'corner')]
    cases += [(f, b, m, FULL, 'corner') for f, b, m in itertools.product(FACES, (False, True), (False, True))
              if (f, b, m) != cases[0][:3]]
    cases += [(f, b, False, HALF, 'corner') for f, b in itertools.product(FACES, (False, True))]
    cases += [('south', False, False, FULL, s) for s in ('straight', 't_junction')]
    commands = ['gamerule doMobSpawning false', 'gamerule doDaylightCycle false', 'time set noon', 'weather clear',
                'fill -8 79 -8 56 79 92 minecraft:smooth_stone', 'gamemode creative @s']
    manifest = []
    for i, (facing, branch, mirror, block, shape) in enumerate(cases):
        x, z = (i % 4) * 12, (i // 4) * 12
        turn = FACES.index(facing)
        # Logical blank arms: both axes continue away from the occupied strip edges.
        main_edge = FACES.index(facing)
        secondary = (main_edge + (1 if branch else -1)) % 4
        edges = (main_edge, secondary)
        ew = next(e for e in edges if e % 2 == 0)
        ns = next(e for e in edges if e % 2 == 1)
        ew_step = (-1 if ns == 1 else 1, 0)
        ns_step = (0, 1 if ew == 0 else -1)
        for (dx, dz), neighbor_facing in ((ew_step, FACES[ew]), (ns_step, FACES[ns])):
            for distance in (1, 2, 3):
                xx, zz = x + dx * distance, z + dz * distance
                commands += [f'setblock {xx} 81 {zz} ' + state(BLANK, neighbor_facing, half='upper', shape='straight'),
                             f'setblock {xx} 80 {zz} ' + state(BLANK, neighbor_facing, half='lower', shape='straight')]
        lower = state(block, facing, branch, mirror, 'lower' if block == FULL else None, shape)
        if block == FULL:
            commands.append(f'setblock {x} 81 {z} ' + state(block, facing, branch, mirror, 'upper', shape))
        commands += [f'setblock {x} 80 {z} {lower}', f'setblock {x} 80 {z} {lower}']
        vx, vz = (16 if mirror ^ branch else 0), 0
        for _ in range(turn):
            vx, vz = 16 - vz, vx
        dx, dz = (-1 if vx == 0 else 1), (-1 if vz == 0 else 1)
        cx, cz = x + vx / 16 + 2.2 * dx, z + vz / 16 + 2.2 * dz
        yaw = math.degrees(math.atan2(cx - (x + .5), (z + .5) - cz))
        view = ['gamemode spectator @s', f'tp @s {cx:.3f} 80 {cz:.3f} {yaw:.3f} 10',
                'tellraw @s ' + json.dumps({'text': f'PLASTER CASE {i:02}: {lower} at {x},80,{z}', 'color':'yellow'}),
                f'execute if block {x} 80 {z} {lower} run say PLASTER_CASE_{i:02}_EXACT_LOWER_PASS',
                f'execute unless block {x} 80 {z} {lower} run say PLASTER_CASE_{i:02}_STATE_NORMALIZED']
        (functions / f'view_{i:02}.mcfunction').write_text('\n'.join(view) + '\n', encoding='utf-8')
        manifest.append({'case': i, 'lower': lower, 'position': [x,80,z], 'camera': [cx,80,cz,yaw,10]})
    commands.append('function plaster_probe:view_00')
    (functions / 'setup.mcfunction').write_text('\n'.join(commands) + '\n', encoding='utf-8')
    (functions / 'owner_oblique.mcfunction').write_text('gamemode spectator @s\ntp @s 3.2 83 3.2 135 48\n', encoding='utf-8')
    (functions / 'owner_free_end.mcfunction').write_text('gamemode spectator @s\ntp @s 3.2 80 -2.2 45 10\n', encoding='utf-8')
    (functions / 'owner_junction.mcfunction').write_text('function plaster_probe:view_00\n', encoding='utf-8')
    (pack / 'SCENE_MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'Prepared {len(cases)} command-state cases in {pack}')


if __name__ == '__main__':
    main()
