"""Read a CLOSED task fixture; prepare state assertions and eight lone-full-wall half probes."""
import argparse, json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];DOC=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'tools/plaster-corner'))
from dump_scene import block
RUN=ROOT/'tmp/plaster-corner-0.1.8d/reopened-discovery-20261008';WORLD=RUN/'world'
F=WORLD/'datapacks/reopened_plaster/data/reopened_plaster/function'
faces=['north','east','south','west'];steps=[(0,-1),(1,0),(0,1),(-1,0)]
manifest=json.loads((DOC/'evidence/FIXTURE_MANIFEST.json').read_text())['cases']
p=argparse.ArgumentParser();p.add_argument('--phase',required=True);args=p.parse_args()
rows=[];checks=[]
for case in manifest:
    x,y,z=case['position'];lower=block(WORLD,x,y,z);upper=block(WORLD,x,y+1,z)
    rows.append({'case':case['case'],'position':case['position'],'saved_lower':lower,'saved_upper':upper,'neighbors':{f'{dx},{dy},{dz}':block(WORLD,x+dx,y+dy,z+dz) for dx,dz in steps for dy in (0,1)},'expected_properties':case['properties']})
    for half,s,dy in [('LOWER',lower,0),('UPPER',upper,1)]:
        if s.get('Name','')=='minecraft:air' and dy==1:continue
        target=s['Name']+'['+','.join(k+'='+v for k,v in s.get('Properties',{}).items())+']'
        checks += [f'execute if block {x} {y+dy} {z} {target} run say REOPENED_RELOAD_{case["case"]:02}_{half}_PASS',f'execute unless block {x} {y+dy} {z} {target} run say REOPENED_RELOAD_{case["case"]:02}_{half}_FAIL']
(DOC/'evidence'/('SAVED_'+args.phase+'.json')).write_text(json.dumps(rows,indent=2)+'\n')
if args.phase=='BLANK':
    (F/'capture_saved.mcfunction').write_text('\n'.join(checks)+'\n')
    lone=['forceload add 0 96 63 111','fill 0 79 96 63 79 111 minecraft:smooth_stone']
    lone_manifest=[]
    for i,(face,b) in enumerate((f,b) for f in faces for b in [False,True]):
        x=8+(i%4)*12;z=100+(i//4)*8;main=faces.index(face);sec=(main+(1 if b else -1))%4;dx,dz=steps[sec];nf=faces[(sec+2)%4]
        for half,dy in [('upper',1),('lower',0)]:lone += [f'setblock {x+dx} {80+dy} {z+dz} britannia_mod:plaster_wall_blank[facing={nf},shape=straight,branch_right=false,half={half}]']
        s=f'britannia_mod:plaster_wall_and_support_blank_half[facing={face},shape=straight,branch_right=false]'
        target=f'britannia_mod:plaster_wall_and_support_blank_half[facing={face},shape=corner,branch_right={str(b).lower()}]'
        lone += [f'setblock {x} 80 {z} {s}',f'setblock {x} 79 {z} minecraft:dirt',f'setblock {x} 79 {z} minecraft:smooth_stone',f'execute if block {x} 80 {z} {target} run say REOPENED_LONE_HALF_{i:02}_PASS',f'execute unless block {x} 80 {z} {target} run say REOPENED_LONE_HALF_{i:02}_FAIL']
        lone_manifest.append({'case':i,'position':[x,80,z],'expected_state':target,'full_neighbor_position':[x+dx,80,z+dz],'full_neighbor_facing':nf,'seed_shape':'straight','setup_kind':'command seed + normal neighbor notifications, not BlockItem'})
    (F/'lone_half.mcfunction').write_text('\n'.join(lone)+'\n')
    (DOC/'evidence/LONE_HALF_MANIFEST.json').write_text(json.dumps(lone_manifest,indent=2)+'\n')
    for name in ['capture_saved','lone_half']:(DOC/'fixture/data/reopened_plaster/function'/(name+'.mcfunction')).write_text((F/(name+'.mcfunction')).read_text())
else:
    lm=json.loads((DOC/'evidence/LONE_HALF_MANIFEST.json').read_text())
    for c in lm:
        x,y,z=c['position'];c['saved_state']=block(WORLD,x,y,z);c['saved_full_neighbor']=block(WORLD,*c['full_neighbor_position'])
    (DOC/'evidence/SAVED_LONE_HALF.json').write_text(json.dumps(lm,indent=2)+'\n')
print(json.dumps({'phase':args.phase,'cases':len(rows),'changed_properties':[{'case':r['case'],'expected':r['expected_properties'],'actual':r['saved_lower']} for r in rows if r['saved_lower'].get('Properties')!=r['expected_properties']]},indent=2))
