"""Summarize actual task-world states separately from analytical asset findings."""
import hashlib,json,re,shutil
from pathlib import Path
DOC=Path(__file__).resolve().parent;ROOT=DOC.parents[2];EV=DOC/'evidence'
RUN=ROOT/'tmp/plaster-corner-0.1.8d/reopened-discovery-20261008'
faces=['north','east','south','west']
def edges(p):
    i=faces.index(p['facing']);b=p['branch_right']=='true';m=p.get('mirrored','false')=='true'
    return sorted([faces[i],faces[(i+(1 if b^m else -1))%4]]) if p['shape']!='straight' else [faces[i]]
blank=json.loads((EV/'SAVED_BLANK.json').read_text());support=json.loads((EV/'SAVED_SUPPORT.json').read_text())
rows=[]
for b,s in zip(blank,support):
    before=b['saved_lower']['Properties'];after=s['saved_lower']['Properties']
    rows.append({'case':b['case'],'position':b['position'],'blank_matches_requested':before==b['expected_properties'],'support_matches_requested':after==b['expected_properties'],'blank_to_support_changes':{k:[before[k],after[k]] for k in before if before[k]!=after[k]},'blank_physical_edges':edges(before),'support_physical_edges':edges(after),'physical_edges_retained':edges(before)==edges(after),'blank_pair_matches':b['saved_upper'].get('Properties')==dict(before,half='upper') if 'half' in before else None,'support_pair_matches':s['saved_upper'].get('Properties')==dict(after,half='upper') if 'half' in after else None})
lone=json.loads((EV/'SAVED_LONE_HALF.json').read_text())
logs={}
for name in ['HEADLESS_LAUNCH.log','HEADLESS_LAUNCH_2.log','HEADLESS_LAUNCH_3.log','HEADLESS_LAUNCH_4.log','HEADLESS_RESTART.log','HEADLESS_RESTART_2.log']:
    src=RUN/name;shutil.copy2(src,EV/name);log=src.read_text(errors='replace')
    logs[name]={'sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'success':'BUILD SUCCESSFUL' in log,'reload_passes':len(re.findall(r'REOPENED_RELOAD_\d+_(?:LOWER|UPPER)_PASS',log)),'reload_failures':re.findall(r'REOPENED_RELOAD_\d+_(?:LOWER|UPPER)_FAIL',log),'lone_half_passes':len(re.findall(r'REOPENED_LONE_HALF_\d+_PASS',log)),'lone_half_failures':re.findall(r'REOPENED_LONE_HALF_\d+_FAIL',log),'runtime_identity':[l for l in log.splitlines() if '[grabby-hands][env]' in l and any(k in l for k in ['artifact:','build:','neoforge:','server:'])]}
summary={'candidate_sha256':hashlib.sha256((RUN/'mods/britannia_mod-0.1.8d-all.jar').read_bytes()).hexdigest(),'setup_failure_is_candidate_failure':False,'logs':logs,'state_findings':rows,'all_physical_edges_retained':all(r['physical_edges_retained'] for r in rows),'all_full_pairs_match':all(r['blank_pair_matches'] and r['support_pair_matches'] for r in rows[:16]),'normal_BlockItem_placement_executed':False,'new_visual_observation_executed':False,'actual_owner_neighbors_captured':False,'lone_half_saved_states':lone,'scope':'New command-state / ordinary neighbor-notification / save-load evidence. Not player, decorator, structure-transform or render proof.'}
(EV/'RUNTIME_SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({'all_physical_edges_retained':summary['all_physical_edges_retained'],'all_full_pairs_match':summary['all_full_pairs_match'],'restart_reload_passes':logs['HEADLESS_RESTART_2.log']['reload_passes'],'restart_reload_failures':logs['HEADLESS_RESTART_2.log']['reload_failures'],'lone_half_passes':logs['HEADLESS_RESTART_2.log']['lone_half_passes'],'changed_branch_cases':[r['case'] for r in rows if 'branch_right' in r['blank_to_support_changes']]},indent=2))
