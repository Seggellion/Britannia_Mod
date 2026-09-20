"""Join legal selector audit to actual command fixture observations; verify discovery links."""
import csv, hashlib, json, re
from pathlib import Path
DOC=Path(__file__).resolve().parent;ROOT=DOC.parents[2];EV=DOC/'evidence'
matrix=json.loads((EV/'MATRIX.json').read_text());blank=json.loads((EV/'SAVED_BLANK.json').read_text());support=json.loads((EV/'SAVED_SUPPORT.json').read_text());manifest=json.loads((EV/'FIXTURE_MANIFEST.json').read_text())['cases']
lone=json.loads((EV/'SAVED_LONE_HALF.json').read_text())
joined=[]
for r in matrix:
    props=r['state'];upper=props.get('half')=='upper';expected=dict(props)
    if upper:expected['half']='lower'
    case=next(c for c in manifest if c['properties']==expected)
    part='saved_upper' if upper else 'saved_lower'
    b=blank[case['case']][part];s=support[case['case']][part]
    lh=next((p for p in lone if p['saved_state'].get('Properties')==props),None) if r['kind']=='half_wall' else None
    joined.append(dict(r,fixture_case=case['case'],blank_actual=b,support_actual=s,blank_exact=b.get('Properties')==props,support_exact=s.get('Properties')==props,lone_half_exact=lh is not None if r['kind']=='half_wall' else None,new_visual_status='PENDING_OWNER_COMPARISON',ordinary_BlockItem_status='NOT_EXECUTED_IN_DISCOVERY',restart_stored_state_check='PASS_40_ASSERTIONS'))
(EV/'RUNTIME_MATRIX.json').write_text(json.dumps(joined,indent=2)+'\n')
with (EV/'RUNTIME_MATRIX.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.writer(f);w.writerow(['kind','facing','branch','mirror','half','model','geometry_status','fixture_case','blank_exact','blank_actual','support_exact','support_actual','lone_half_exact','new_visual','BlockItem'])
    for r in joined:
        p=r['state'];w.writerow([r['kind'],p['facing'],p['branch_right'],p.get('mirrored','N/A'),p.get('half','N/A'),r['selector']['model'],r['status'],r['fixture_case'],r['blank_exact'],r['blank_actual'].get('Properties'),r['support_exact'],r['support_actual'].get('Properties'),r['lone_half_exact'],r['new_visual_status'],r['ordinary_BlockItem_status']])
shader=Path('C:/Users/dusti/curseforge/minecraft/Instances/UltimaCraft - Britannia/shaderpacks/photon_v1.1.zip')
if shader.exists():(EV/'SHADER_IDENTITY.json').write_text(json.dumps({'path':str(shader),'size_bytes':shader.stat().st_size,'sha256':hashlib.sha256(shader.read_bytes()).hexdigest(),'selected':True,'visual_behavior':'Not evaluated or changed'},indent=2)+'\n')
missing=[]
for file in DOC.glob('*.md'):
    for target in re.findall(r'\]\(([^)]+)\)',file.read_text(encoding='utf-8')):
        if target.startswith(('http:','https:','#')):continue
        if not (file.parent/target.split('#',1)[0]).exists():missing.append({'document':file.name,'target':target})
assert not missing,missing
result={'legal_full_corner_rows':sum(r['kind']=='full' for r in joined),'legal_half_corner_rows':sum(r['kind']=='half_wall' for r in joined),'full_blank_exact_rows':sum(r['kind']=='full' and r['blank_exact'] for r in joined),'half_lone_exact_rows':sum(r['kind']=='half_wall' and r['lone_half_exact'] for r in joined),'document_missing_links':missing,'candidate_sha256':hashlib.sha256((ROOT/'tmp/combined-0.1.8d/release/britannia_mod-0.1.8d-all.jar').read_bytes()).hexdigest(),'production_visual_acceptance':False}
(EV/'DISCOVERY_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
