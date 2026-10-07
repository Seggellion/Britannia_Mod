"""Freeze legal render states in a CLOSED disposable world copy, never a live world.

This fixture bypasses neighbor derivation on purpose. It proves model transforms,
not placement reachability. Original worlds and region bytes are retained.
"""
import argparse
import json
import math
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dump_scene import ROOT, nbt_io, block
from prepare_scene import FULL, HALF, BLANK, FACES, state


def entry(text):
    name, props = text.rstrip(']').split('[')
    return {'Name': (8, name), 'Properties': (10, {
        k: (8, v) for k, v in (p.split('=') for p in props.split(','))})}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--world', type=Path, required=True)
    args = ap.parse_args()
    world = args.world.resolve()
    allowed = [(ROOT / p).resolve() for p in (
        'tmp/plaster-corner-0.1.8d/client-resume/saves',
        'tmp/plaster-corner-0.1.8d/combined-client/saves')]
    if not any(world.is_relative_to(p) for p in allowed) or not (world / 'level.dat').exists():
        ap.error('only explicitly disposable resumed/combined acceptance world copies are writable')
    pack = world / 'datapacks/plaster_probe'
    cases = json.loads((pack / 'SCENE_MANIFEST.json').read_text())
    edits = {}
    air = {'Name': (8, 'minecraft:air')}
    for c in cases:
        x, y, z = c['position']
        p = entry(c['lower'])['Properties'][1]
        facing = p['facing'][1]
        shape = p['shape'][1]
        branch = p['branch_right'][1] == 'true'
        mirror = p.get('mirrored', (8, 'false'))[1] == 'true'
        is_full = c['lower'].startswith(FULL + '[')
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in (0, 1):
                    edits[(x+dx, y+dy, z+dz)] = air
        main_edge = FACES.index(facing)
        secondary = (main_edge + (1 if branch ^ mirror else -1)) % 4
        edges = [main_edge] if shape == 'straight' else [main_edge, secondary]
        if shape == 't_junction':
            edges.append((secondary + 2) % 4)
        # Arms follow the physical footprint, including mirror. They remain forced.
        horizontal = next((e for e in edges if e % 2 == 0), None)
        vertical = next((e for e in edges if e % 2 == 1), None)
        for edge in edges:
            if edge % 2 == 0:
                along = [(-1, 0), (1, 0)] if shape != 'corner' else [(-1 if vertical == 1 else 1, 0)]
            else:
                along = [(0, -1), (0, 1)] if shape == 'straight' or (shape == 't_junction' and main_edge % 2 == 1) else [(0, 1 if horizontal == 0 else -1)]
            for dx, dz in along:
                for distance in (1, 2, 3):
                    for dy in (0, 1):
                        edits[(x+dx*distance, y+dy, z+dz*distance)] = entry(state(
                            BLANK, FACES[edge], half='upper' if dy else 'lower', shape='straight'))
        edits[(x, y, z)] = entry(c['lower'])
        if is_full:
            edits[(x, y+1, z)] = entry(c['lower'].replace('half=lower', 'half=upper'))
    chunks = {}
    for pos, value in edits.items():
        x, y, z = pos
        chunks.setdefault((x//16, z//16), []).append((pos, value))
    regions = {}
    for (cx, cz), changes in chunks.items():
        path = world / f'region/r.{cx//32}.{cz//32}.mca'
        data = regions.setdefault(path, bytearray(path.read_bytes()))
        index = cx % 32 + 32 * (cz % 32)
        loc = struct.unpack_from('>I', data, index*4)[0]
        start = (loc >> 8)*4096
        if not start:
            raise ValueError(f'fixture must use already saved chunks: {cx},{cz}')
        length = struct.unpack_from('>I', data, start)[0]
        assert data[start+4] == 2
        raw = zlib.decompress(data[start+5:start+4+length])
        name, tags = nbt_io.parse(raw)
        assert nbt_io.serialise(name, tags) == raw
        sections = tags['sections'][1][1]
        for sy in sorted({p[1]//16 for p, _ in changes}):
            section = next(s for s in sections if s['Y'][1] == sy)
            states = section['block_states'][1]
            palette = states['palette'][1][1]
            bits = max(4, math.ceil(math.log2(len(palette))))
            per = 64//bits
            packed = states.get('data', (12, []))[1]
            indices = [0]*4096 if len(palette) == 1 else [
                ((packed[i//per] & ((1<<64)-1)) >> ((i%per)*bits)) & ((1<<bits)-1)
                for i in range(4096)]
            for (x,y,z), value in changes:
                if y//16 != sy:
                    continue
                if value not in palette:
                    palette.append(value)
                indices[(y%16)*256+(z%16)*16+x%16] = palette.index(value)
            bits = max(4, math.ceil(math.log2(len(palette))))
            per = 64//bits
            output = [0]*math.ceil(4096/per)
            for i, value in enumerate(indices):
                output[i//per] |= value << ((i%per)*bits)
            states['data'] = (12, [v if v < 1<<63 else v-(1<<64) for v in output])
        new_raw = nbt_io.serialise(name, tags)
        assert nbt_io.serialise(*nbt_io.parse(new_raw)) == new_raw
        payload = zlib.compress(new_raw)
        encoded = struct.pack('>I', len(payload)+1) + b'\x02' + payload
        sectors = math.ceil(len(encoded)/4096)
        assert sectors < 256 and len(data)%4096 == 0
        sector = len(data)//4096
        data.extend(encoded + bytes(sectors*4096-len(encoded)))
        struct.pack_into('>I', data, index*4, (sector<<8)|sectors)
    # All decoding/encoding succeeds before any writes; preserve original copy bytes.
    for path, data in regions.items():
        backup = path.with_suffix('.mca.before-forced-acceptance')
        version = 2
        while backup.exists():
            backup = path.with_suffix(f'.mca.before-forced-acceptance-v{version}')
            version += 1
        backup.write_bytes(path.read_bytes())
        path.write_bytes(data)
    result = []
    for c in cases:
        x,y,z = c['position']
        result.append({**c, 'forced_lower': block(world,x,y,z), 'forced_upper': block(world,x,y+1,z)})
    (pack / 'FORCED_STATES.json').write_text(json.dumps(result,indent=2)+'\n')
    for c in cases:
        i = c['case']
        f = pack / f'data/plaster_probe/function/view_{i:02}.mcfunction'
        text = f.read_text()
        text += f'execute if block {c["position"][0]} 81 {c["position"][2]} {c["lower"].replace("half=lower","half=upper")} run say PLASTER_CASE_{i:02}_EXACT_UPPER_PASS\n' if c['lower'].startswith(FULL+'[') else ''
        text += f'tellraw @s {{"text":"FORCED LEGAL RENDER FIXTURE; NOT PLACEMENT ACCEPTANCE","color":"aqua"}}\n'
        text += 'time set noon\n'
        f.write_text(text)
    print(f'Frozen {len(cases)} legal cases; retained region backups; no placement claim')


if __name__ == '__main__':
    main()
