"""Stage verified immutable artifacts and a new disposable profile. Never launch Minecraft.
No active profile, production world, Git ref, mod source or account file is modified.
"""
import gzip,hashlib,json,shutil,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];DOC=Path(__file__).resolve().parent;EV=DOC/'evidence'
EV.mkdir(exist_ok=True)
SOURCE='986ed746866dc944b7b496ab9428b670f65e15c0'
EXPECTED='f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd'
BASE=ROOT/'tmp/plaster-corner-0.1.8d/followup-20261009'
assert BASE.resolve().is_relative_to((ROOT/'tmp/plaster-corner-0.1.8d').resolve())
assert not BASE.exists(),'Preserve existing preparation; do not overwrite a previous profile/artifact.'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name,data):(EV/name).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
JAR=ROOT/'tmp/combined-0.1.8d/release/britannia_mod-0.1.8d-all.jar'
assert digest(JAR)==EXPECTED and JAR.stat().st_size==36142668
ACTIVE=Path('C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft - Britannia/mods/britannia_mod-0.1.8d.jar')
installed_hash=digest(ACTIVE)
STAGED=BASE/'artifacts'/SOURCE/JAR.name;STAGED.parent.mkdir(parents=True)
shutil.copy2(JAR,STAGED);assert digest(STAGED)==EXPECTED
BACKUP=BASE/'rollback'/installed_hash/ACTIVE.name;BACKUP.parent.mkdir(parents=True)
shutil.copy2(ACTIVE,BACKUP);assert digest(BACKUP)==installed_hash
CLIENT=BASE/'client';(CLIENT/'mods').mkdir(parents=True)
shutil.copy2(STAGED,CLIENT/'mods'/STAGED.name)
assert len(list((CLIENT/'mods').glob('*.jar')))==1 and digest(CLIENT/'mods'/STAGED.name)==EXPECTED
(CLIENT/'options.txt').write_text('resourcePacks:[]\nincompatibleResourcePacks:[]\npauseOnLostFocus:true\n',encoding='utf-8')
OLDWORLD=ROOT/'tmp/plaster-corner-0.1.8d/reopened-discovery-20261008/world'
# Caller inspected processes first; no owner world/profile is a source.
WORLD=CLIENT/'saves/Plaster exterior candidate 986ed746';WORLD.mkdir(parents=True)
copied=[]
for name in ['region','entities','poi','data','datapacks']:
    src=OLDWORLD/name
    if src.exists():shutil.copytree(src,WORLD/name);copied.append(name)
shutil.copy2(OLDWORLD/'level.dat',WORLD/'level.dat')
sys.path.insert(0,str(ROOT/'tools'));import nbt_io
raw=gzip.decompress((WORLD/'level.dat').read_bytes());name,tags=nbt_io.parse(raw)
assert nbt_io.serialise(name,tags)==raw,'NBT codec must roundtrip before editing this new copy.'
data=tags['Data'][1]
data.pop('Player',None)
data['allowCommands']=(1,1);data['GameType']=(3,1);data['LevelName']=(8,'Plaster exterior candidate 986ed746')
data['SpawnX']=(3,8);data['SpawnY']=(3,80);data['SpawnZ']=(3,8)
(WORLD/'level.dat').write_bytes(gzip.compress(nbt_io.serialise(name,tags),mtime=0))
# Explicit exterior camera actions at lower/upper view height; owner executes them manually.
F=WORLD/'datapacks/reopened_plaster/data/reopened_plaster/function'
for label,x,y,z,yaw in [('a_lower',11.2,80.0,11.2,135),('a_upper',11.2,81.0,11.2,135),('b_lower',17.8,80.0,11.2,-135),('b_upper',17.8,81.0,11.2,-135)]:
    commands=['gamemode spectator @s',f'tp @s {x} {y} {z} {yaw} 8',f'tellraw @s '+json.dumps({'text':f'EXTERIOR {label}: junction timber must span both visible sections; Y84 displays are forced art/no collision.','color':'yellow'})]
    (F/('exterior_'+label+'.mcfunction')).write_text('\n'.join(commands)+'\n')
# A helper only stages launcher instructions. It cannot touch the owner profile.
launch=BASE/'START_ISOLATED_CLIENT.ps1'
launch.write_text("""param([switch]$Launch)
$ErrorActionPreference='Stop'
if (!$Launch) { Write-Output 'Prepared isolated profile only. Run with -Launch when YOU choose to open its client.'; exit 0 }
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$clientPath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'client'))
$jarPath=Join-Path $clientPath 'mods/britannia_mod-0.1.8d-all.jar'
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f02d2876d60e10a539829950b173b61055a72e6e42dd36643bec97ab62f482bd') { throw 'Candidate hash mismatch' }
if (@(Get-ChildItem -LiteralPath (Join-Path $clientPath 'mods') -Filter '*.jar').Count -ne 1) { throw 'Unexpected extra mod JAR' }
& (Join-Path $taskRoot 'tools/plaster-corner/run-isolated.ps1') -GradleTasks @('-p','tmp/combined-0.1.8d/candidate-source','runClient','-I','tools/plaster-corner/packaged-client.gradle','-x','compileJava','-x','processResources','-x','jar','-x','jarJar') -RunDirectory 'tmp/plaster-corner-0.1.8d/followup-20261009/client'
exit $LASTEXITCODE
""",encoding='utf-8')
manifest={'prepared_utc':datetime.now(timezone.utc).isoformat(),'release':'0.1.8d','candidate':{'original':str(JAR),'staged':str(STAGED),'profile_mod':str(CLIENT/'mods'/JAR.name),'source':SOURCE,'dirty':False,'size_bytes':STAGED.stat().st_size,'sha256':digest(STAGED),'filename_preserved':True},'selected_owner_profile':str(ACTIVE.parent.parent),'selected_installed':{'path':str(ACTIVE),'sha256_before':installed_hash,'sha256_after':digest(ACTIVE),'backup':str(BACKUP),'backup_sha256':digest(BACKUP),'active_profile_modified':False},'isolated_profile':{'path':str(CLIENT),'world':str(WORLD),'source_world':str(OLDWORLD),'copied_world_components':copied,'excluded_components':['playerdata','stats','advancements','session.lock','serverconfig','account/launcher files','owner options/shaders/packs','production worlds/configuration'],'mods':[JAR.name],'launch_helper':str(launch),'launched':False,'loaded_runtime_identity':'PENDING_OWNER_LAUNCH'},'route':'ROUTE_A_PREPARATION_PENDING_EXTERIOR_COMPARISON','plaster_status':'INSTALLATION_DISCREPANCY_CONFIRMED','visual_status':'EXTERIOR_A_B_PENDING','active_installation':'NOT_PERFORMED','no_candidate_rebuild':True,'owner_contract':'Both A/B continuous exterior junction timber; preserve interior design and existing one-post A/two-post B style.'}
assert installed_hash==manifest['selected_installed']['sha256_after']
save('INSTALLATION_MANIFEST.json',manifest)
for file in ['ARTIFACTS.json','PARITY_AND_PARENTS.json','MATRIX.json','RUNTIME_MATRIX.json','FACE_RAYS.json','RESOURCE_STACK.json','CURRENT_TREE_PARITY.json','RUNTIME_SUMMARY.json','SCOPED_FRESHNESS.txt']:
    shutil.copy2(ROOT/'docs/hotfixes/plaster-corner-reopened-2026-10-08/evidence'/file,EV/file)
save('BASELINE.json',{'entry_branch':subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip(),'entry_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'owner_contract':manifest['owner_contract'],'candidate_source':SOURCE,'production_delta_since_candidate':subprocess.check_output(['git','diff','--name-only',SOURCE,'HEAD','--','src/main','src/test','tools','build.gradle','gradle.properties','.gitattributes',':(exclude)src/main/java/com/seggellion/britannia_mod/gametest/**'],cwd=ROOT,text=True).strip(),'new_runtime_tests':'None; reuse artifact-bound prior evidence. Isolated profile not launched.','actual_owner_server_identity':'Unavailable; not needed to compare candidate art in isolated singleplayer.'})
print(json.dumps({'staged_candidate':str(STAGED),'candidate_sha256':digest(STAGED),'isolated_profile':str(CLIENT),'rollback_copy':str(BACKUP),'active_profile_unchanged':digest(ACTIVE)==installed_hash,'visual_status':'PENDING'},indent=2))
