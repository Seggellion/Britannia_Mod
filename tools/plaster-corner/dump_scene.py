"""Read saved disposable scene states without changing a world (close it first)."""
import argparse
import gzip
import json
import math
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import nbt_io


def plain(tag):
    kind, value = tag
    if kind == 10:
        return {k: plain(v) for k, v in value.items()}
    if kind == 9:
        subtype, values = value
        return [plain((subtype, v)) for v in values]
    return value


def block(world, x, y, z):
    cx, cz = x // 16, z // 16
    data = (world / f'region/r.{cx // 32}.{cz // 32}.mca').read_bytes()
    location = struct.unpack_from('>I', data, 4 * ((cx % 32) + 32 * (cz % 32)))[0]
    start = (location >> 8) * 4096
    if not start:
        return {'not_saved': True}
    length = struct.unpack_from('>I', data, start)[0]
    compressed = data[start + 5:start + 4 + length]
    compression = data[start + 4]
    raw = {1: gzip.decompress, 2: zlib.decompress, 3: lambda b: b}[compression](compressed)
    name, tags = nbt_io.parse(raw)
    assert nbt_io.serialise(name, tags) == raw, 'NBT codec must roundtrip this saved chunk'
    chunk = plain((10, tags))
    section = next(s for s in chunk['sections'] if s['Y'] == y // 16)
    states = section['block_states']
    palette = states['palette']
    if len(palette) == 1:
        return palette[0]
    bits = max(4, math.ceil(math.log2(len(palette))))
    per_long = 64 // bits
    index = (y % 16) * 256 + (z % 16) * 16 + x % 16
    packed = states['data'][index // per_long] & ((1 << 64) - 1)
    return palette[(packed >> ((index % per_long) * bits)) & ((1 << bits) - 1)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    world = args.world.resolve()
    if not world.is_relative_to((ROOT / 'tmp/plaster-corner-0.1.8d').resolve()):
        parser.error('read only a disposable task world')
    manifest = json.loads((world / 'datapacks/plaster_probe/SCENE_MANIFEST.json').read_text())
    result = []
    for case in manifest:
        x, y, z = case['position']
        case['saved_lower'] = block(world, x, y, z)
        case['saved_upper'] = block(world, x, y + 1, z)
        case['neighbors'] = {f'{dx},{dy},{dz}': block(world, x + dx, y + dy, z + dz)
                             for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)) for dy in (0, 1)}
        result.append(case)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f'Read {len(result)} saved cases with lower, upper, and eight neighboring states')


if __name__ == '__main__':
    main()
