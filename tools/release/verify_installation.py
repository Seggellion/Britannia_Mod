"""Read-only installed-jar preflight using existing build provenance and /grabby env.

This compares files; it intentionally never certifies what a running JVM loaded.
Run with --help. SHA-256 and source must be complete, not release-version aliases.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

MOD_CLASS = 'com/seggellion/britannia_mod/BritanniaMod.class'
BUILD_PROPERTIES = 'britannia_mod_build.properties'
REQUIRED_BUNDLES = {
    'META-INF/jarjar/geckolib-neoforge-1.21.1-4.6.6.jar',
    'META-INF/jarjar/nanohttpd-2.2.0.jar',
}


def artifact_identity(path):
    """Read the identity produced by generateBuildInfo/artifactIdentity."""
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        if MOD_CLASS not in names:
            raise ValueError('Archive is not Britannia')
        properties = dict(line.split('=', 1) for line in
                          archive.read(BUILD_PROPERTIES).decode('utf-8').splitlines()
                          if '=' in line and not line.startswith('#'))
        missing = REQUIRED_BUNDLES - names
        if missing:
            raise ValueError('Not a bundled deployable jar: missing ' + ', '.join(sorted(missing)))
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {
        'filename': path.name,
        'bytes': path.stat().st_size,
        'sha256': digest,
        'mod_version': properties.get('mod.version'),
        'source_commit': properties.get('git.head'),
        'dirty': properties.get('git.dirty'),
        'build_timestamp': properties.get('build.timestamp'),
    }


def britannia_jars(mods):
    """Identify renamed duplicates by class, not only the filename/version."""
    result = []
    for path in sorted(mods.iterdir()):
        if path.is_file() and path.suffix.lower() == '.jar':
            try:
                with zipfile.ZipFile(path) as archive:
                    belongs = MOD_CLASS in archive.namelist()
            except zipfile.BadZipFile:
                if 'britannia' in path.name.lower():
                    raise ValueError('Unreadable active Britannia jar: ' + path.name)
                continue
            if belongs:
                result.append(path)
    return result


def preflight(candidate, mods, instance, expected_sha, expected_source,
              repository, approved_ref='patch-18'):
    errors = []
    result = {
        'file_status': 'FILE_MISMATCH',
        'loaded_runtime_status': 'LOADED_RUNTIME_UNVERIFIED',
        'loaded_runtime_reason': 'Files do not prove a loaded JVM. Verify a new session with /grabby env artifact/build/server lines.',
        'errors': errors,
    }
    try:
        if not re.fullmatch(r'[0-9a-fA-F]{64}', expected_sha):
            raise ValueError('Expected SHA-256 must have all 64 hexadecimal digits')
        if not re.fullmatch(r'[0-9a-fA-F]{40}', expected_source):
            raise ValueError('Expected source must be a complete 40-digit commit')
        candidate, mods, instance = candidate.resolve(), mods.resolve(), instance.resolve()
        if mods.name.lower() != 'mods' or mods.parent != instance:
            raise ValueError('Target mods directory does not belong to the expected instance')
        selected = artifact_identity(candidate)
        result['selected_artifact'] = selected
        if selected['sha256'] != expected_sha.lower():
            errors.append('Selected candidate hash differs from approved full SHA-256')
        if selected['source_commit'] != expected_source.lower():
            errors.append('Selected candidate source differs from approved source')
        if selected['dirty'] != 'false' or selected['mod_version'] != '0.1.8d':
            errors.append('Selected candidate must be clean version 0.1.8d')
        if not selected['build_timestamp']:
            errors.append('Selected candidate has no build timestamp')
        lineage = subprocess.run(
            ['git', '-C', str(repository), 'merge-base', '--is-ancestor',
             expected_source, approved_ref], capture_output=True)
        if lineage.returncode != 0:
            errors.append('Approved source is not an ancestor of the selected repository ref')
        result['source_ancestry_verified'] = lineage.returncode == 0
        active = britannia_jars(mods)
        result['active_britannia_jars'] = [artifact_identity(p) for p in active]
        result['disabled_old_files'] = sorted(p.name for p in mods.iterdir()
                                               if p.is_file() and p.name.lower().endswith('.old'))
        if len(active) != 1:
            errors.append('Expected exactly one active Britannia jar; found ' + str(len(active)))
        elif (result['active_britannia_jars'][0]['sha256'] != expected_sha.lower()
              or result['active_britannia_jars'][0]['source_commit'] != expected_source.lower()):
            errors.append('Installed Britannia bytes/source differ from approved candidate')
        if not errors:
            result['file_status'] = 'FILE_MATCH'
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        errors.append(str(error))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--mods', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--expected-sha256', required=True)
    parser.add_argument('--expected-source', required=True)
    parser.add_argument('--repository', type=Path, required=True)
    parser.add_argument('--approved-ref', default='patch-18')
    args = parser.parse_args()
    result = preflight(args.candidate, args.mods, args.instance, args.expected_sha256,
                       args.expected_source, args.repository, args.approved_ref)
    print(json.dumps(result, indent=2))
    return 0 if result['file_status'] == 'FILE_MATCH' else 1


if __name__ == '__main__':
    raise SystemExit(main())
