"""Read-only discovery audit. Run from any directory; prints JSON to stdout.

Bounds are model geometry, not evidence of Minecraft's baked/rendered output.
No asset generator main() is invoked and no repository assets are written.
"""
import hashlib
import itertools
import json
from pathlib import Path
import sys
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'src/main/resources/assets/britannia_mod'
FAMILY = 'plaster_wall_and_support_blank'
sys.path.insert(0, str(ROOT / 'tools/scaffolding'))
sys.path.insert(0, str(ROOT / 'tools'))
import gen_junctions as junctions
import gen_blockstates as states
import generate_half_walls as halves
import mcjson


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def model_path(identifier):
    namespace, path = identifier.split(':', 1)
    return ROOT / f'src/main/resources/assets/{namespace}/models/{path}.json'


def turn(point, angle):
    x, y, z = point
    for _ in range(angle // 90):
        x, z = 16 - z, x
    return [round(x, 4), y, round(z, 4)]


def box(el, angle):
    assert el.get('rotation', {}).get('angle', 0) == 0
    points = [turn(p, angle) for p in itertools.product(*zip(el['from'], el['to']))]
    return {'from': [min(p[i] for p in points) for i in range(3)],
            'to': [max(p[i] for p in points) for i in range(3)]}


variants = load(ASSETS / f'blockstates/{FAMILY}.json')['variants']
rows = []
inventory = {}
for key, entry in variants.items():
    props = dict(pair.split('=') for pair in key.split(','))
    if props['shape'] != 'corner':
        continue
    row = {'state': props, 'model': entry['model'], 'y': entry.get('y', 0), 'posts': []}
    if props['half'] == 'lower':
        path = model_path(entry['model'])
        model = load(path)
        row['wall_boxes'] = [box(el, row['y']) for el in model['elements']
                             if any(f['texture'] == '#1' for f in el['faces'].values())]
        for i, el in enumerate(model['elements']):
            if el['to'][1] - el['from'][1] > 16 and all(
                    f['texture'] == '#2' for f in el['faces'].values()):
                row['posts'].append({'element_index_0_based': i, **box(el, row['y'])})
        inventory[str(path.relative_to(ROOT))] = {
            'parent': model.get('parent'), 'textures': model.get('textures'),
            'ambientocclusion': model.get('ambientocclusion'),
            'render_type': model.get('render_type', 'unspecified'),
            'hand_authored_by_generator_contract': mcjson.is_hand_authored(str(path)),
            'sha256': digest(path.read_bytes()),
            'elements': [{'index': i, 'from': el['from'], 'to': el['to'],
                          'rotation': el.get('rotation'),
                          'faces': el.get('faces')} for i, el in enumerate(model['elements'])]}
    rows.append(row)

models = ASSETS / 'models/block/structure/plaster'
straight = load(models / f'{FAMILY}_straight.json')
generator_comparison = {}
for suffix, right in junctions.VARIANTS:
    actual = load(models / f'{FAMILY}_{suffix}.json')
    computed = junctions.build(straight, 'corner' if suffix.startswith('corner') else 't_junction', right)
    half_actual = load(models / f'{FAMILY}_half_{suffix}.json')
    generator_comparison[suffix] = {
        'elements_equal_current_junction_generator': actual['elements'] == computed['elements'],
        'half_elements_equal_current_half_generator': halves.matches(
            half_actual['elements'], halves.halve(actual)['elements'])}

references = {}
for path in (ASSETS / 'models').rglob('*.json'):
    parent = load(path).get('parent', '')
    if FAMILY in parent:
        references[str(path.relative_to(ROOT))] = parent

runtime = {}
asset_paths = [ASSETS / f'blockstates/{FAMILY}.json', *models.glob(f'{FAMILY}*.json')]
for jar in sorted((ROOT / 'build/libs').glob('*.jar')):
    with zipfile.ZipFile(jar) as archive:
        identity = archive.read('britannia_mod_build.properties').decode() if (
            'britannia_mod_build.properties' in archive.namelist()) else 'missing'
        compared = {}
        for path in asset_paths:
            name = str(path.relative_to(ROOT / 'src/main/resources')).replace('\\', '/')
            compared[name] = 'missing' if name not in archive.namelist() else (
                'byte-identical' if archive.read(name) == path.read_bytes() else
                'JSON-identical, byte formatting differs' if json.loads(archive.read(name)) == load(path)
                else 'JSON differs')
        runtime[str(jar.relative_to(ROOT))] = {'build_identity': identity, 'asset_comparison': compared}

expected = {(f, s, b, m, h) for f, s, b, m, h in itertools.product(
    states.ROTATION, ('straight', 'corner', 't_junction'), ('false', 'true'),
    ('false', 'true'), ('lower', 'upper'))}
actual = set()
missing_models = []
for key, entry in variants.items():
    p = dict(pair.split('=') for pair in key.split(','))
    actual.add(tuple(p[k] for k in ('facing', 'shape', 'branch_right', 'mirrored', 'half')))
    if entry['model'].startswith('britannia_mod:') and not model_path(entry['model']).exists():
        missing_models.append(entry['model'])
print(json.dumps({'variant_count': len(variants), 'state_coverage_exact': expected == actual,
                  'missing_models': missing_models, 'corner_rows': rows,
                  'model_inventory': inventory, 'generator_comparison': generator_comparison,
                  'support_in_blockstate_generator_mirrorable_list': FAMILY in states.MIRRORABLE_WINDOWS,
                  'parent_consumers': references, 'existing_jars': runtime}, indent=2))
