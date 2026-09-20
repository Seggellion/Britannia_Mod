"""Read a CLOSED disposable acceptance world and collect native F2 evidence."""
import argparse
import gzip
import json
import re
import shutil
from pathlib import Path
from dump_scene import ROOT, block, nbt_io, plain


def snapshot(world):
    pack = world / 'datapacks/plaster_probe'
    result = {}
    for name in ('SCENE_MANIFEST', 'STRUCTURES', 'NEIGHBORS', 'CONTROLS'):
        rows = json.loads((pack / (name + '.json')).read_text())
        for row in rows:
            x, y, z = row['position']
            row['saved_lower'] = block(world, x, y, z)
            row['saved_upper'] = block(world, x, y + 1, z)
            row['neighbors'] = {f'{dx},{dy},{dz}': block(world, x + dx, y + dy, z + dz)
                                for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)) for dy in (0, 1)}
        result[name] = rows
    result['ACTUAL_PLACEMENT_AND_DECORATOR'] = []
    for label, x, z in [('corner_first', 60, 36), ('reversed_arms', 72, 36), ('decorated_pair', 48, 84)]:
        result['ACTUAL_PLACEMENT_AND_DECORATOR'].append({
            'label': label, 'position': [x, 80, z],
            'lower': block(world, x, 80, z), 'upper': block(world, x, 81, z),
            'neighbors': {f'{dx},{dy},{dz}': block(world, x + dx, 80 + dy, z + dz)
                          for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)) for dy in (0, 1)}})
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--client', type=Path)
    args = parser.parse_args()
    world = args.world.resolve()
    assert world.is_relative_to((ROOT / 'tmp/plaster-corner-0.1.8d').resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(snapshot(world), indent=2) + '\n')
    if args.client:
        out = args.output.parent
        log = (args.client / 'logs/latest.log').read_text(errors='replace')
        frames = []
        scene = None
        for line in log.splitlines():
            match = re.search(r'ACCEPTANCE_SCENE_(\d+)_(\S+)', line)
            if match:
                scene = (int(match[1]), match[2])
            shot = re.search(r'Saved screenshot as ([\d_.-]+\.png)', line)
            if shot and scene:
                filename = f'join-scene-{scene[0]:02}-{shot[1]}'
                shutil.copy2(args.client / 'screenshots' / shot[1], out / filename)
                frames.append({'scene': scene[0], 'label': scene[1], 'file': filename, 'log': line})
        (out / 'JOIN_FRAMES.json').write_text(json.dumps(frames, indent=2) + '\n')
        print('Missing architectural scenes:', sorted(set(range(31)) - {r['scene'] for r in frames}))
        shutil.copy2(args.client / 'logs/latest.log', out / 'CLIENT_LATEST.log')
        shutil.copytree(world / 'datapacks/plaster_probe', out / 'commands', dirs_exist_ok=True)
        storage = world / 'data/command_storage_plaster_probe.dat'
        if storage.exists():
            _, tags = nbt_io.parse(gzip.decompress(storage.read_bytes()))
            (out / 'COMMAND_STORAGE.json').write_text(json.dumps(plain((10, tags)), indent=2) + '\n')
    print('Collected exact saved grid, structures, neighbors, controls and actual-click samples')


if __name__ == '__main__':
    main()
