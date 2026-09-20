"""Read-only recovery coordination. No installation, launch, restart or world edit.

Writes only this dated evidence record. Reuses completed gates and verifies that
the selected existing candidate contains the effective fence and plaster fixes.
"""
import hashlib
import json
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
DOC = Path(__file__).resolve().parent
EV = DOC / 'evidence'
EV.mkdir(exist_ok=True)
sys.path.insert(0, str(ROOT / 'tools/release'))
from verify_installation import artifact_identity, britannia_jars, preflight

SOURCE = 'cf49b1700d1a8992f39396f2ae531f0cb26502d7'
SHA = 'e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70'
JAR = ROOT / 'tmp/fence-runtime-recovery-2026-10-09/release/britannia_mod-0.1.8d-all.jar'
HISTORICAL = ROOT / 'tmp/combined-0.1.8d/release/britannia_mod-0.1.8d-all.jar'
INSTANCE = Path('C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft - Britannia')
SERVER = ROOT / 'tmp/fence-runtime-rediscovery-2026-10-08/source/tmp/edge-fence/recovery-2026-10-09/server'
BACKUP = INSTANCE / '.britannia-recovery-backups/2026-10-09-e64fe63c/britannia_mod-0.1.8d.jar'
RETIRED = BACKUP.parent / 'retired-from-mods.jar'

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

assert git('branch', '--show-current') == 'patch-18'
identity = artifact_identity(JAR)
assert identity['sha256'] == SHA and identity['source_commit'] == SOURCE
assert identity['bytes'] == 36142686 and identity['dirty'] == 'false'
assert identity['mod_version'] == '0.1.8d'
fixes = {}
for label, short in [('fence_layout_endpoint_implementation', '0fe906d7'),
                     ('fence_required_test_closeout', '07359f98'),
                     ('plaster_mesh_and_half', 'ac6b2e80'),
                     ('paired_wall_mirror_collision', '5a6734ba'),
                     ('combined_fixture_baseline', '986ed746'),
                     ('fence_runtime_test_identity', 'cf49b170'),
                     ('plaster_followup_integration', 'b440196d'),
                     ('fence_recovery_preparation', 'd808d3cb'),
                     ('fence_local_installation_closeout', '8707bb54')]:
    commit = git('rev-parse', short)
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, 'patch-18'], cwd=ROOT, check=True)
    in_candidate = subprocess.run(['git', 'merge-base', '--is-ancestor', commit, SOURCE], cwd=ROOT).returncode == 0
    fixes[label] = {'commit': commit, 'integrated_patch18': True, 'ancestor_of_candidate': in_candidate}
production_paths = ['src/main/java', 'src/main/resources', 'build.gradle', 'gradle.properties']
assert not git('diff', '--name-only', SOURCE, 'HEAD', '--', *production_paths)
assert not git('diff', '--name-only', '--', *production_paths)
with zipfile.ZipFile(HISTORICAL) as old, zipfile.ZipFile(JAR) as chosen:
    assert set(old.namelist()) == set(chosen.namelist())
    changed = sorted(p for p in old.namelist() if old.read(p) != chosen.read(p))
    assert changed == ['britannia_mod_build.properties'], changed
    archive_parity = {'entries': len(chosen.namelist()), 'different_entries': changed,
                     'identical_entries': len(chosen.namelist()) - len(changed),
                     'identical_class_entries': sum(p.endswith('.class') for p in chosen.namelist()),
                     'identical_asset_entries': sum(p.startswith('assets/') for p in chosen.namelist()),
                     'all_other_cumulative_hotfix_payload_preserved': True}
    support = {}
    for p in chosen.namelist():
        if ('plaster_wall_and_support_blank' in p and p.endswith('.json') and
            (p.startswith('assets/britannia_mod/models/block/structure/plaster/') or
             p.startswith('assets/britannia_mod/blockstates/'))):
            disk = ROOT / 'src/main/resources' / p
            working = disk.read_bytes()
            packed = chosen.read(p)
            committed = subprocess.check_output(['git', 'show', f'{SOURCE}:src/main/resources/{p}'], cwd=ROOT)
            assert json.loads(working) == json.loads(packed) == json.loads(committed), p
            assert working.replace(b'\r\n', b'\n') == packed.replace(b'\r\n', b'\n') == committed.replace(b'\r\n', b'\n'), p
            support[p] = {'semantic_equal': True, 'normalized_bytes_equal': True,
                          'working_copy_bytes_equal': working == packed,
                          'packaged_sha256': hashlib.sha256(packed).hexdigest()}
    assert len(support) == 17
    assert not any('FenceRuntimeIdentityGameTests' in p for p in chosen.namelist())
installed = preflight(JAR, INSTANCE/'mods', INSTANCE, SHA, SOURCE, ROOT)
assert installed['file_status'] == 'FILE_MATCH', installed['errors']
assert sha(BACKUP) == sha(RETIRED) == 'c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6'
assert len(britannia_jars(SERVER/'mods')) == 1
server_identity = artifact_identity(britannia_jars(SERVER/'mods')[0])
assert server_identity['sha256'] == SHA
log_lines = (SERVER/'logs/latest.log').read_text(encoding='utf-8', errors='replace').splitlines()
identity_lines = [l for l in log_lines if ('artifact: britannia_mod' in l or 'build: version=0.1.8d' in l or 'Starting Minecraft server on 127.0.0.1:25583' in l)]
assert any(SHA in l for l in identity_lines) and any(SOURCE in l for l in identity_lines)
(EV/'EXISTING_SERVER_IDENTITY.txt').write_text('\n'.join(identity_lines)+'\n', encoding='utf-8')
ps = r'''$taskProcesses=@(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | ForEach-Object { $taskArgs=[string]$_.CommandLine; $taskGame=[regex]::Match($taskArgs,'--gameDir\s+(?:"([^"]+)"|([^\s]+))'); [pscustomobject]@{pid=$_.ProcessId;game_dir=if($taskGame.Groups[1].Success){$taskGame.Groups[1].Value}elseif($taskGame.Groups[2].Success){$taskGame.Groups[2].Value}else{$null};client=($taskArgs -match 'net\.minecraft\.client\.main\.Main|KnotClient|forgeclient');server=($taskArgs -match 'forgeserver|server.Main')} }); $taskListeners=@(Get-NetTCPConnection -LocalPort 25583 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,OwningProcess); @{processes=$taskProcesses;listeners=$taskListeners} | ConvertTo-Json -Depth 5'''
processes = json.loads(subprocess.check_output(['powershell', '-NoProfile', '-Command', ps], text=True))
assert processes['listeners'] and all(p['LocalAddress']=='127.0.0.1' for p in processes['listeners'])
assert any(p['server'] and p['pid']==processes['listeners'][0]['OwningProcess'] for p in processes['processes'])
clients = [p for p in processes['processes'] if p['client']]
fixture_root = SERVER/'world/datapacks'
required = {'fence_recovery': ['case_24_start', 'case_24_extend', 'case_24_verify', 'case_12_start', 'case_12_extend', 'case_12_verify'],
            'closeout_probe': ['prepare', 'fence', 'fence_view', 'wall', 'wall_view', 'verify'],
            'reopened_plaster': ['setup', 'capture', 'exterior_a_lower', 'exterior_a_upper', 'exterior_b_lower', 'exterior_b_upper']}
fixture_files = {}
for namespace, functions in required.items():
    for function in functions:
        found = list(fixture_root.glob(f'*/data/{namespace}/function/{function}.mcfunction'))
        assert len(found)==1, (namespace, function)
        fixture_files[f'{namespace}:{function}'] = sha(found[0])
prior = json.loads((ROOT/'docs/projects/patch18-edge-fence-hotfix/evidence/runtime-recovery-2026-10-09/LOCAL_RECOVERY_RECEIPT.json').read_text())
assert prior['installed_sha256'] == SHA and prior['client_closed_before_replacement']
other_mods = {p.name:sha(p) for p in (INSTANCE/'mods').iterdir() if p.is_file() and p not in britannia_jars(INSTANCE/'mods')}
result = {'verified_utc':datetime.now(timezone.utc).isoformat(), 'entry_branch':'patch-18', 'entry_head':git('rev-parse','HEAD'),
          'fence_task_completion':'Completed; final report received before coordination proceeded',
          'implementation_blocking_failure':False, 'selected_path':str(JAR), 'selected_identity':identity,
          'integrated_fixes':fixes, 'current_production_source_matches_candidate_source':True,
          'historical_reference_path':str(HISTORICAL), 'archive_comparison':archive_parity,
          'support_resources':support, 'installed_preflight':installed,
          'backup':{'path':str(BACKUP),'sha256':sha(BACKUP),'retired_original':str(RETIRED),'outside_active_mods':True},
          'installation_status':'ALREADY_INSTALLED_BY_COMPLETED_FENCE_TASK_VERIFIED_NO_SECOND_REPLACEMENT',
          'prior_install_receipt':prior, 'current_processes_sanitized':processes, 'client_process_observed':bool(clients),
          'matching_existing_server':{'directory':str(SERVER),'address':'127.0.0.1:25583','identity':server_identity,'loaded_identity_in_existing_session':True},
          'fixture_functions_present':fixture_files, 'other_mod_files_current_hashes':other_mods,
          'historical_other_mod_files_unchanged_during_install':prior['unrelated_mod_files_unchanged'],
          'actions_this_coordination':{'profile_replaced':False,'candidate_rebuilt':False,'passing_suites_repeated':False,'server_started_or_restarted':False,'worlds_or_settings_modified':False,'desktop_inputs':False},
          'client_loaded_identity':'PENDING_OWNER_LAUNCH', 'fence_visual_acceptance':'PENDING', 'plaster_A_B_exterior_acceptance':'PENDING',
          'production_acceptance':'PENDING', 'production_server_changed':False}
(EV/'COMBINED_RECOVERY_VERIFICATION.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps({k:result[k] for k in ['entry_head','selected_identity','archive_comparison','installation_status','client_process_observed','client_loaded_identity']}, indent=2))
