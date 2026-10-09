"""Verify staging/backup hashes, copied fixture states and new metadata without launching."""
import gzip,hashlib,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];DOC=Path(__file__).resolve().parent;EV=DOC/'evidence'
m=json.loads((EV/'INSTALLATION_MANIFEST.json').read_text());expected=m['candidate']['sha256']
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert all(sha(m['candidate'][key])==expected for key in ['original','staged','profile_mod'])
assert sha(m['selected_installed']['path'])==sha(m['selected_installed']['backup'])==m['selected_installed']['sha256_before']
sys.path.insert(0,str(ROOT/'tools/plaster-corner'));from dump_scene import block
world=Path(m['isolated_profile']['world']);prior=Path(m['isolated_profile']['source_world'])
manifest=json.loads((world/'datapacks/reopened_plaster/SCENE_MANIFEST.json').read_text())
compared=0
for c in manifest:
    x,y,z=c['position']
    for dx,dy,dz in [(0,0,0),(0,1,0)]+[(dx,dy,dz) for dx,dz in [(0,-1),(1,0),(0,1),(-1,0)] for dy in [0,1]]:
        assert block(world,x+dx,y+dy,z+dz)==block(prior,x+dx,y+dy,z+dz),(c['case'],dx,dy,dz)
        compared+=1
sys.path.insert(0,str(ROOT/'tools'));import nbt_io
raw=gzip.decompress((world/'level.dat').read_bytes());name,tags=nbt_io.parse(raw)
assert nbt_io.serialise(name,tags)==raw
data=tags['Data'][1]
assert data['allowCommands']==(1,1) and data['GameType']==(3,1)
assert 'Player' not in data
assert not (Path(m['isolated_profile']['path'])/'launcher_accounts.json').exists()
assert not (world/'playerdata').exists() and not (world/'serverconfig').exists()
mods=list((Path(m['isolated_profile']['path'])/'mods').glob('*.jar'));assert len(mods)==1
result={'candidate_original_staged_profile_equal':True,'owner_active_and_rollback_equal':True,'copied_saved_state_neighbor_assertions':compared,'copied_task_world_only':True,'new_metadata_roundtrip':True,'new_world_creative_cheats_enabled':True,'profile_launcher_accounts_absent':True,'playerdata_and_serverconfig_excluded':True,'profile_mod_jar_count':len(mods),'profile_launched':False,'actual_loaded_identity':'PENDING_OWNER_LAUNCH','visual_accepted':False}
(EV/'PREPARATION_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
