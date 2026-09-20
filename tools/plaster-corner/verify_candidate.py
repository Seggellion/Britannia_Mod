"""Verify the clean bundled candidate identity and packaged support-family resources."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    source, jar = args.source.resolve(), args.jar.resolve()
    sha = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    assert not subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).strip()
    resources = source / 'src/main/resources'
    files = list((resources / 'assets/britannia_mod/models/block/structure/plaster').glob('plaster_wall_and_support_blank*.json'))
    files += list((resources / 'assets/britannia_mod/blockstates').glob('plaster_wall_and_support_blank*.json'))
    with zipfile.ZipFile(jar) as archive:
        raw = archive.read('britannia_mod_build.properties').decode()
        props = dict(line.split('=', 1) for line in raw.splitlines() if line and not line.startswith('#') and '=' in line)
        assert props['git.head'] == sha and props['git.dirty'] == 'false', props
        assert props['mod.version'] == '0.1.8d', props
        bundle = [p for p in archive.namelist() if p.startswith('META-INF/jarjar/') and p.endswith('.jar')]
        assert any('geckolib-neoforge-1.21.1-4.6.6' in p for p in bundle), bundle
        assert any('nanohttpd-2.2.0' in p for p in bundle), bundle
        compared = {}
        for path in files:
            name = path.relative_to(resources).as_posix()
            packed = archive.read(name)
            assert json.loads(packed) == json.loads(path.read_bytes()), name
            compared[name] = {'semantic_equal': True, 'byte_equal': packed == path.read_bytes(),
                              'packaged_sha256': hashlib.sha256(packed).hexdigest()}
        result = {'artifact': str(jar), 'size_bytes': jar.stat().st_size,
                  'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'identity': props,
                  'source_checkout': str(source), 'source_sha': sha, 'source_clean': True,
                  'bundled_dependencies': bundle, 'support_family_resources': compared}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'support_family_resources'}, indent=2))
    print(f'{len(files)} packaged support-family model/blockstate resources match source')


if __name__ == '__main__':
    main()
