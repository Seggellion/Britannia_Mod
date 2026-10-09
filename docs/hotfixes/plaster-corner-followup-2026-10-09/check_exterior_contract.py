"""Static exterior first-quad probes at independently derived architectural edge joints.
Reads current/old authored faces, not expected post bounds/count. NOT rendered acceptance.
"""
import itertools,json
from pathlib import Path
DOC=Path(__file__).resolve().parent;EV=DOC/'evidence'
CURRENT=json.loads((EV/'ARTIFACTS.json').read_text())
OLD=json.loads((DOC.parent/'plaster-corner-reopened-2026-10-08/evidence/active_profile_models.json').read_text())
NEW=json.loads((DOC.parent/'plaster-corner-reopened-2026-10-08/evidence/candidate_models.json').read_text())
side={'west':(0,-1),'east':(0,1),'down':(1,-1),'up':(1,1),'north':(2,-1),'south':(2,1)}
faces=['north','east','south','west']
def rotate(p,angle):
    x,y,z=p
    for _ in range(angle//90):x,z=16-z,x
    return [x,y,z]
def transformed(doc,angle):
    out=[]
    for i,e in enumerate(doc['elements']):
        assert e.get('rotation',{}).get('angle',0)==0
        pts=[rotate(p,angle) for p in itertools.product(*zip(e['from'],e['to']))]
        fs={}
        for name,f in e['faces'].items():
            dest=faces[(faces.index(name)+angle//90)%4] if name in faces else name
            fs[dest]=f
        out.append({'element':i,'from':[min(p[k] for p in pts) for k in range(3)],'to':[max(p[k] for p in pts) for k in range(3)],'faces':fs})
    return out
def first(elements,cam,target):
    d=[b-a for a,b in zip(cam,target)];hits=[]
    for e in elements:
        for name,f in e['faces'].items():
            axis,sign=side[name]
            if d[axis]*sign>=0:continue
            plane=e['to'][axis] if sign>0 else e['from'][axis];t=(plane-cam[axis])/d[axis]
            if t<=0:continue
            p=[a+t*v for a,v in zip(cam,d)]
            if all(e['from'][j]-1e-6<=p[j]<=e['to'][j]+1e-6 for j in range(3) if j!=axis):hits.append((t,{'element':e['element'],'face':name,'texture':f['texture'],'point':[round(v,6) for v in p]}))
    return min(hits,key=lambda h:h[0])[1] if hits else None
matrix=json.loads((EV/'MATRIX.json').read_text());rows=[]
for r in matrix:
    if r['state'].get('half')=='upper':continue
    p=r['state'];right=(p['branch_right']=='true')^(p.get('mirrored','false')=='true');angle=r['selector'].get('y',0)
    model=r['selector']['model'].split(':',1)[1];path='assets/britannia_mod/models/'+model+'.json'
    for height in ([8,24] if r['kind']=='full' else [8]):
        joint=rotate([16 if right else 0,height,0],angle)
        camera=rotate([48 if right else -32,height,-32],angle)
        row={'kind':r['kind'],'state':p,'model':r['selector']['model'],'angle':angle,'height':height,'camera':camera,'architectural_joint':joint}
        for label,docs in [('candidate',NEW),('installed_old',OLD)]:
            hit=first(transformed(docs[path],angle),camera,joint);row[label]={'first_quad':hit,'hits_vertical_timber':hit is not None and hit['texture']=='#2'}
        rows.append(row)
result={'contract':'Both A/B have continuous exterior timber at the intersection of physical edge runs; preserve interior and current free-end style.','method':'Ray from exterior diagonal to edge intersection at Y8/Y24 (half Y8), using actual outward-facing authored quads after selector rotation. Low trim is below these heights. No post bounds/count used as expected output.','scope_limit':'Static sample/face evidence; no GPU, neighbors, installed loaded-memory or actual owner visual proof. Two heights do not replace complete continuity observation.','probes':rows,'candidate_exterior_hits':sum(r['candidate']['hits_vertical_timber'] for r in rows),'candidate_probe_count':len(rows),'installed_old_missing_junction_probes':sum(not r['installed_old']['hits_vertical_timber'] for r in rows),'plaster_accepted':False}
assert result['candidate_exterior_hits']==40
assert result['installed_old_missing_junction_probes']==20
(EV/'EXTERIOR_CONTRACT_RESULTS.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='probes'},indent=2))
