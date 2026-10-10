"""Prepare a history-preserving public tree from committed Patch 18 objects.

No deployed files, local main, existing review branch or protections are changed.
The temporary index preserves every retained Git object, type and mode exactly.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def git(*args, env=None, data=None):
    return subprocess.check_output(['git', *args], cwd=ROOT, env=env, input=data)

def entries(ref):
    result = {}
    for row in git('ls-tree', '-r', '-z', ref).split(b'\0'):
        if not row:
            continue
        meta, path = row.split(b'\t', 1)
        mode, kind, oid = meta.decode().split()
        result[path.decode()] = {'mode': mode, 'type': kind, 'object': oid}
    return result

def exclusion(path, historical):
    lower = path.lower()
    if lower.endswith('.md'):
        return 'case-insensitive Markdown exclusion'
    if lower.startswith('docs/'):
        return 'internal documentation/evidence tree'
    if path in historical:
        return historical[path]
    if lower.split('/')[0] in {'.vscode', '.idea', '.claude', '.codex', '.agents', '.aws', '.gradle', 'tmp', 'build', 'run', 'runs', 'logs', 'crash-reports'}:
        return 'local configuration/cache/runtime/temporary data'
    # The established manifest retains tracked legacy source/archive inputs,
    # including .old files under src. Do not widen a local-artifact suffix rule
    # into deletion of previously published source/assets.
    source_input = lower.startswith(('src/', 'content/', 'weapons/'))
    if lower in {'.env', 'server.properties', 'ops.json', 'usercache.json', 'whitelist.json'} or (not source_input and lower.endswith(('.log', '.log.gz', '.hprof', '.bak', '.tmp', '.old'))):
        return 'local runtime/configuration/temporary artifact'
    return None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', required=True)
    parser.add_argument('--main', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    source = git('rev-parse', args.source).decode().strip()
    parent = git('rev-parse', args.main).decode().strip()
    policy = json.loads((ROOT/'docs/hotfixes/combined-0.1.8d/PUBLIC_SNAPSHOT.json').read_text())
    all_entries = entries(source)
    kept, excluded = {}, {}
    for path, item in all_entries.items():
        reason = exclusion(path, policy['excluded'])
        if reason:
            excluded[path] = reason
        else:
            kept[path] = item
    previous_public = entries(policy['sanitized_snapshot'])
    removed_source_inputs = [p for p in previous_public if p in all_entries and p not in kept]
    assert not removed_source_inputs, 'New exclusion of previously retained source requires investigation: ' + ', '.join(removed_source_inputs)
    # Refuse to overwrite independently evolved main content. Equal changes are safe.
    baseline = entries(policy['remote_main_parent'])
    current = entries(parent)
    main_changes = [p for p in set(baseline)|set(current) if baseline.get(p) != current.get(p)]
    conflicts = [p for p in main_changes if current.get(p) != kept.get(p)]
    assert not conflicts, 'Independent main changes require investigation: ' + ', '.join(conflicts)
    index = args.output.resolve()/'snapshot.index'
    assert not index.exists(), 'Preserve prior preparation; use a fresh output directory'
    env = {**os.environ, 'GIT_INDEX_FILE': str(index)}
    git('read-tree', '--empty', env=env)
    lines = ''.join(f"{v['mode']} {v['object']}\t{p}\n" for p,v in sorted(kept.items())).encode()
    git('update-index', '--index-info', env=env, data=lines)
    tree = git('write-tree', env=env).decode().strip()
    assert entries(tree) == kept, 'Retained object/type/mode parity failed'
    commit = git('commit-tree', tree, '-p', parent, '-F', '-', data=(
        f'Publish sanitized cumulative Patch 18 0.1.8d source\n\n'
        f'Integrated development source: {source}\n'
        'Includes the final fence/plaster/shared-wall/network/medallion/alligator fixes.\n'
        'Internal records/local artifacts excluded; portable required RunUO fixtures retained.\n'
    ).encode()).decode().strip()
    assert git('show', '-s', '--format=%P', commit).decode().strip() == parent
    manifest = {'patch18_source':source, 'fresh_remote_main_parent':parent,
                'sanitized_commit':commit, 'tree':tree, 'source_entries':len(all_entries),
                'retained_entries':len(kept), 'excluded_entries':len(excluded),
                'retained_object_mode_type_parity':True, 'exceptions':[],
                'independent_main_changed_paths':main_changes, 'excluded':excluded, 'retained':kept,
                'signing_status':'No configured authorized signer; prepared commit signature must be checked, never invented'}
    (args.output/'SNAPSHOT_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    (args.output/'SNAPSHOT_SHA.txt').write_text(commit+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k not in {'retained','excluded'}},indent=2))

if __name__ == '__main__':
    main()
