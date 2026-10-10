"""Generate fixed-layout mappings and reflected copies of CHECKED-IN fence artwork.

Default is mapping-only. --reflections additionally refreshes derived *_reflected.json.
There is deliberately no authored-model or item-model writing path.
Run from any directory; --check verifies idempotence without writing.
"""
import argparse
import copy
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/britannia_mod'
MODEL_DIR = ROOT / 'models/block/structure/wooden_fence'
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
DIRECTIONS = tuple(FACINGS)
MODEL_NAMES = ('isolated', 'end', 'end_mirrored', 'straight', 'corner', 't_junction', 'cross')
Q = {'north': 'south', 'south': 'north', 'east': 'east', 'west': 'west', 'up': 'up', 'down': 'down'}

def topology(bits):
    count = sum(bits.values())
    if count == 0: return 'isolated'
    if count == 1: return 'end'
    if count == 2 and (bits['north'] and bits['south'] or bits['east'] and bits['west']): return 'straight'
    if count == 2: return 'corner'
    if count == 3: return 't_junction'
    return 'cross'

def entry_for(bits, facing):
    """Frozen old resource decoder, including noncanonical end states."""
    shape = topology(bits)
    name = shape
    if shape in ('isolated', 'straight', 'cross'): y = FACINGS[facing]
    elif shape == 'end':
        connection = next(d for d in DIRECTIONS if bits[d])
        y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}[connection]
        if facing != {'east': 'north', 'south': 'east', 'west': 'south', 'north': 'west'}[connection]:
            name = 'end_mirrored'
    elif shape == 'corner':
        pair = frozenset(d for d in DIRECTIONS if bits[d])
        y = {frozenset(('east','south')):0, frozenset(('south','west')):90,
             frozenset(('west','north')):180, frozenset(('north','east')):270}[pair]
    else: y = FACINGS[next(d for d in DIRECTIONS if not bits[d])]
    result = {'model': 'britannia_mod:block/structure/wooden_fence/' + name}
    if y: result['y'] = y
    return result

def mappings():
    variants = {}
    for facing in FACINGS:
        for mask in range(16):
            bits = {d: bool(mask & 1 << i) for i,d in enumerate(DIRECTIONS)}
            key = 'facing={},layout_code=32,'.format(facing) + ','.join('{}={}'.format(d,str(bits[d]).lower()) for d in DIRECTIONS)
            variants[key] = entry_for(bits, facing)
        for code in range(32):
            bits = {d: bool(code & 1 << i) for i,d in enumerate(DIRECTIONS)}
            if code & 16:
                entry = entry_for({Q[d]:v for d,v in bits.items()}, Q[facing])
                entry['model'] += '_reflected'
                y = -entry.pop('y',0) % 360
                if y: entry['y'] = y
            else: entry = entry_for(bits,facing)
            variants['facing={},layout_code={}'.format(facing,code)] = entry
    return {'variants': variants}

def uv_vertices(face):
    u0,v0,u1,v1 = face['uv']
    corners = [(u0,v0),(u0,v1),(u1,v1),(u1,v0)]
    turn = face.get('rotation',0)//90
    return [corners[(i+turn)%4] for i in range(4)]

def reflected_face(face, direction):
    result = copy.deepcopy(face)
    # FaceInfo order after a Z reflection: reversed winding, with a different
    # starting corner for the horizontal faces. Retain each vertex's texture UV.
    permutation = (1,0,3,2) if direction in ('up','down') else (3,2,1,0)
    old = uv_vertices(face)
    expected = [old[i] for i in permutation]
    u0,v0,u1,v1 = face['uv']
    for u in ((u0,u1),(u1,u0)):
        for v in ((v0,v1),(v1,v0)):
            for turn in (0,90,180,270):
                candidate = {'uv':[u[0],v[0],u[1],v[1]], 'rotation':turn}
                if uv_vertices(candidate) == expected:
                    result.update(candidate)
                    if turn==0: result.pop('rotation',None)
                    if 'cullface' in result: result['cullface'] = Q[result['cullface']]
                    return result
    raise ValueError('UV reflection cannot be represented: '+str(face))

def reflect_model(model):
    result = copy.deepcopy(model)
    for old, new in zip(model['elements'], result['elements']):
        new['from'][2], new['to'][2] = 16-old['to'][2], 16-old['from'][2]
        if 'rotation' in old:
            new['rotation']['origin'][2] = 16-old['rotation']['origin'][2]
            if old['rotation']['axis'] in ('x','y'): new['rotation']['angle'] = -old['rotation']['angle']
        new['faces'] = {Q[d]:reflected_face(f,d) for d,f in old['faces'].items()}
    return result

def emit(path, value, check):
    encoded = json.dumps(value,indent=2)+'\n'
    if check:
        if not path.exists() or path.read_text(encoding='utf-8') != encoded: raise SystemExit('Stale generated resource: '+str(path))
    else: path.write_text(encoded,encoding='utf-8')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reflections', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    emit(ROOT / 'blockstates/wooden_fence.json', mappings(), args.check)
    if args.reflections:
        for name in MODEL_NAMES:
            model = json.loads((MODEL_DIR / (name+'.json')).read_text(encoding='utf-8'))
            emit(MODEL_DIR / (name+'_reflected.json'), reflect_model(model), args.check)
    print('Verified' if args.check else 'Generated', '192 mappings; authored meshes/item untouched')

if __name__=='__main__': main()
